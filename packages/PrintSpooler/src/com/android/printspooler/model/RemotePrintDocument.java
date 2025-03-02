/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.printspooler.model;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder.DeathRecipient;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.print.ILayoutResultCallback;
import android.print.IPrintDocumentAdapter;
import android.print.IPrintDocumentAdapterObserver;
import android.print.IWriteResultCallback;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.util.Log;

import com.android.printspooler.R;
import com.android.printspooler.util.PageRangeUtils;

import libcore.io.IoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;

public final class RemotePrintDocument {
    private static final String LOG_TAG = "RemotePrintDocument";

    private static final boolean DEBUG = false;

    private static final int STATE_INITIAL = 0;
    private static final int STATE_STARTED = 1;
    private static final int STATE_UPDATING = 2;
    private static final int STATE_UPDATED = 3;
    private static final int STATE_FAILED = 4;
    private static final int STATE_FINISHED = 5;
    private static final int STATE_CANCELING = 6;
    private static final int STATE_CANCELED = 7;
    private static final int STATE_DESTROYED = 8;

    private static final PageRange[] ALL_PAGES_ARRAY = new PageRange[] {
            PageRange.ALL_PAGES
    };

    private final Context mContext;

    private final RemotePrintDocumentInfo mDocumentInfo;
    private final UpdateSpec mUpdateSpec = new UpdateSpec();

    private final Looper mLooper;
    private final IPrintDocumentAdapter mPrintDocumentAdapter;
    private final DocumentObserver mDocumentObserver;

    private final UpdateResultCallbacks mUpdateCallbacks;

    private final CommandDoneCallback mCommandResultCallback =
            new CommandDoneCallback() {
        @Override
        public void onDone() {
            if (mCurrentCommand.isCompleted()) {
                if (mCurrentCommand instanceof LayoutCommand) {
                    // If there is a next command after a layout is done, then another
                    // update was issued and the next command is another layout, so we
                    // do nothing. However, if there is no next command we may need to
                    // ask for some pages given we do not already have them or we do
                    // but the content has changed.
                    LayoutCommand layoutCommand = (LayoutCommand) mCurrentCommand;
                    if (mNextCommand == null) {
                        if (layoutCommand.isDocumentChanged() || !PageRangeUtils.contains(
                                mDocumentInfo.writtenPages, mUpdateSpec.pages)) {
                            mNextCommand = new WriteCommand(mContext, mLooper,
                                    mPrintDocumentAdapter, mDocumentInfo,
                                    mDocumentInfo.info.getPageCount(), mUpdateSpec.pages,
                                    mDocumentInfo.file, mCommandResultCallback);
                        } else {
                            // If we have the requested pages just update that ones to be printed.
                            mDocumentInfo.printedPages = computePrintedPages(mUpdateSpec.pages,
                                    mDocumentInfo.writtenPages, mDocumentInfo.info.getPageCount());
                            // Notify we are done.
                            notifyUpdateCompleted();
                        }
                    }
                } else {
                    // We always notify after a write.
                    notifyUpdateCompleted();
                }
                runPendingCommand();
            } else if (mCurrentCommand.isFailed()) {
                mState = STATE_FAILED;
                CharSequence error = mCurrentCommand.getError();
                mCurrentCommand = null;
                mNextCommand = null;
                mUpdateSpec.reset();
                notifyUpdateFailed(error);
            } else if (mCurrentCommand.isCanceled()) {
                if (mState == STATE_CANCELING) {
                    mState = STATE_CANCELED;
                    notifyUpdateCanceled();
                }
                runPendingCommand();
            }
        }
    };

    private final DeathRecipient mDeathRecipient = new DeathRecipient() {
        @Override
        public void binderDied() {
            finish();
        }
    };

    private int mState = STATE_INITIAL;

    private AsyncCommand mCurrentCommand;
    private AsyncCommand mNextCommand;

    public interface DocumentObserver {
        public void onDestroy();
    }

    public interface UpdateResultCallbacks {
        public void onUpdateCompleted(RemotePrintDocumentInfo document);
        public void onUpdateCanceled();
        public void onUpdateFailed(CharSequence error);
    }

    public RemotePrintDocument(Context context, IPrintDocumentAdapter adapter,
            File file, DocumentObserver destroyListener, UpdateResultCallbacks callbacks) {
        mPrintDocumentAdapter = adapter;
        mLooper = context.getMainLooper();
        mContext = context;
        mDocumentObserver = destroyListener;
        mDocumentInfo = new RemotePrintDocumentInfo();
        mDocumentInfo.file = file;
        mUpdateCallbacks = callbacks;
        connectToRemoteDocument();
    }

    public void start() {
        if (DEBUG) {
            Log.i(LOG_TAG, "[CALLED] start()");
        }
        if (mState != STATE_INITIAL) {
            throw new IllegalStateException("Cannot start in state:" + stateToString(mState));
        }
        try {
            mPrintDocumentAdapter.start();
            mState = STATE_STARTED;
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error calling start()", re);
            mState = STATE_FAILED;
            mDocumentObserver.onDestroy();
        }
    }

    public boolean update(PrintAttributes attributes, PageRange[] pages, boolean preview) {
        boolean willUpdate;

        if (DEBUG) {
            Log.i(LOG_TAG, "[CALLED] update()");
        }

        if (hasUpdateError()) {
            throw new IllegalStateException("Cannot update without a clearing the failure");
        }

        if (mState == STATE_INITIAL || mState == STATE_FINISHED || mState == STATE_DESTROYED) {
            throw new IllegalStateException("Cannot update in state:" + stateToString(mState));
        }

        // We schedule a layout if the constraints changed.
        if (!mUpdateSpec.hasSameConstraints(attributes, preview)) {
            willUpdate = true;

            // If there is a current command that is running we ask for a
            // cancellation and start over.
            if (mCurrentCommand != null && (mCurrentCommand.isRunning()
                    || mCurrentCommand.isPending())) {
                mCurrentCommand.cancel();
            }

            // Schedule a layout command.
            PrintAttributes oldAttributes = mDocumentInfo.attributes != null
                    ? mDocumentInfo.attributes : new PrintAttributes.Builder().build();
            AsyncCommand command = new LayoutCommand(mLooper, mPrintDocumentAdapter,
                  mDocumentInfo, oldAttributes, attributes, preview, mCommandResultCallback);
            scheduleCommand(command);

            mState = STATE_UPDATING;
        // If no layout in progress and we don't have all pages - schedule a write.
        } else if ((!(mCurrentCommand instanceof LayoutCommand)
                || (!mCurrentCommand.isPending() && !mCurrentCommand.isRunning()))
                && !PageRangeUtils.contains(mUpdateSpec.pages, pages)) {
            willUpdate = true;

            // Cancel the current write as a new one is to be scheduled.
            if (mCurrentCommand instanceof WriteCommand
                    && (mCurrentCommand.isPending() || mCurrentCommand.isRunning())) {
                mCurrentCommand.cancel();
            }

            // Schedule a write command.
            AsyncCommand command = new WriteCommand(mContext, mLooper, mPrintDocumentAdapter,
                    mDocumentInfo, mDocumentInfo.info.getPageCount(), pages,
                    mDocumentInfo.file, mCommandResultCallback);
            scheduleCommand(command);

            mState = STATE_UPDATING;
        } else {
            willUpdate = false;
            if (DEBUG) {
                Log.i(LOG_TAG, "[SKIPPING] No update needed");
            }
        }

        // Keep track of what is requested.
        mUpdateSpec.update(attributes, preview, pages);

        runPendingCommand();

        return willUpdate;
    }

    public void finish() {
        if (DEBUG) {
            Log.i(LOG_TAG, "[CALLED] finish()");
        }
        if (mState != STATE_STARTED && mState != STATE_UPDATED
                && mState != STATE_FAILED && mState != STATE_CANCELING) {
            throw new IllegalStateException("Cannot finish in state:"
                    + stateToString(mState));
        }
        try {
            mPrintDocumentAdapter.finish();
            mState = STATE_FINISHED;
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error calling finish()", re);
            mState = STATE_FAILED;
            mDocumentObserver.onDestroy();
        }
    }

    public void cancel() {
        if (DEBUG) {
            Log.i(LOG_TAG, "[CALLED] cancel()");
        }

        if (mState == STATE_CANCELING) {
            return;
        }

        if (mState != STATE_UPDATING) {
            throw new IllegalStateException("Cannot cancel in state:" + stateToString(mState));
        }

        mState = STATE_CANCELING;

        mCurrentCommand.cancel();
    }

    public void destroy() {
        if (DEBUG) {
            Log.i(LOG_TAG, "[CALLED] destroy()");
        }
        if (mState == STATE_DESTROYED) {
            throw new IllegalStateException("Cannot destroy in state:" + stateToString(mState));
        }

        mState = STATE_DESTROYED;

        disconnectFromRemoteDocument();
        mDocumentObserver.onDestroy();
    }

    public boolean isUpdating() {
        return mState == STATE_UPDATING || mState == STATE_CANCELING;
    }

    public boolean isDestroyed() {
        return mState == STATE_DESTROYED;
    }

    public boolean hasUpdateError() {
        return mState == STATE_FAILED;
    }

    public void clearUpdateError() {
        if (!hasUpdateError()) {
            throw new IllegalStateException("No update error to clear");
        }
        mState = STATE_STARTED;
    }

    public RemotePrintDocumentInfo getDocumentInfo() {
        return mDocumentInfo;
    }

    public void writeContent(ContentResolver contentResolver, Uri uri) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(mDocumentInfo.file);
            out = contentResolver.openOutputStream(uri);
            final byte[] buffer = new byte[8192];
            while (true) {
                final int readByteCount = in.read(buffer);
                if (readByteCount < 0) {
                    break;
                }
                out.write(buffer, 0, readByteCount);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error writing document content.", e);
        } finally {
            IoUtils.closeQuietly(in);
            IoUtils.closeQuietly(out);
        }
    }

    private void notifyUpdateCanceled() {
        if (DEBUG) {
            Log.i(LOG_TAG, "[CALLING] onUpdateCanceled()");
        }
        mUpdateCallbacks.onUpdateCanceled();
    }

    private void notifyUpdateCompleted() {
        if (DEBUG) {
            Log.i(LOG_TAG, "[CALLING] onUpdateCompleted()");
        }
        mUpdateCallbacks.onUpdateCompleted(mDocumentInfo);
    }

    private void notifyUpdateFailed(CharSequence error) {
        if (DEBUG) {
            Log.i(LOG_TAG, "[CALLING] onUpdateCompleted()");
        }
        mUpdateCallbacks.onUpdateFailed(error);
    }

    private void connectToRemoteDocument() {
        try {
            mPrintDocumentAdapter.asBinder().linkToDeath(mDeathRecipient, 0);
        } catch (RemoteException re) {
            Log.w(LOG_TAG, "The printing process is dead.");
            destroy();
            return;
        }

        try {
            mPrintDocumentAdapter.setObserver(new PrintDocumentAdapterObserver(this));
        } catch (RemoteException re) {
            Log.w(LOG_TAG, "Error setting observer to the print adapter.");
            destroy();
        }
    }

    private void disconnectFromRemoteDocument() {
        try {
            mPrintDocumentAdapter.setObserver(null);
        } catch (RemoteException re) {
            Log.w(LOG_TAG, "Error setting observer to the print adapter.");
            // Keep going - best effort...
        }

        mPrintDocumentAdapter.asBinder().unlinkToDeath(mDeathRecipient, 0);
    }

    private void scheduleCommand(AsyncCommand command) {
        if (mCurrentCommand == null) {
            mCurrentCommand = command;
        } else {
            mNextCommand = command;
        }
    }

    private void runPendingCommand() {
        if (mCurrentCommand != null
                && (mCurrentCommand.isCompleted()
                        || mCurrentCommand.isCanceled())) {
            mCurrentCommand = mNextCommand;
            mNextCommand = null;
        }

        if (mCurrentCommand != null) {
            if (mCurrentCommand.isPending()) {
                mCurrentCommand.run();
            }
            mState = STATE_UPDATING;
        } else {
            mState = STATE_UPDATED;
        }
    }

    private static String stateToString(int state) {
        switch (state) {
            case STATE_FINISHED: {
                return "STATE_FINISHED";
            }
            case STATE_FAILED: {
                return "STATE_FAILED";
            }
            case STATE_STARTED: {
                return "STATE_STARTED";
            }
            case STATE_UPDATING: {
                return "STATE_UPDATING";
            }
            case STATE_UPDATED: {
                return "STATE_UPDATED";
            }
            case STATE_CANCELING: {
                return "STATE_CANCELING";
            }
            case STATE_CANCELED: {
                return "STATE_CANCELED";
            }
            case STATE_DESTROYED: {
                return "STATE_DESTROYED";
            }
            default: {
                return "STATE_UNKNOWN";
            }
        }
    }

    private static PageRange[] computePrintedPages(PageRange[] requestedPages,
                                                   PageRange[] writtenPages, int pageCount) {
        // Adjust the print job pages based on what was requested and written.
        // The cases are ordered in the most expected to the least expected.
        if (Arrays.equals(writtenPages, requestedPages)) {
            // We got a document with exactly the pages we wanted. Hence,
            // the printer has to print all pages in the data.
            return ALL_PAGES_ARRAY;
        } else if (Arrays.equals(writtenPages, ALL_PAGES_ARRAY)) {
            // We requested specific pages but got all of them. Hence,
            // the printer has to print only the requested pages.
            return requestedPages;
        } else if (PageRangeUtils.contains(writtenPages, requestedPages)) {
            // We requested specific pages and got more but not all pages.
            // Hence, we have to offset appropriately the printed pages to
            // be based off the start of the written ones instead of zero.
            // The written pages are always non-null and not empty.
            final int offset = -writtenPages[0].getStart();
            PageRangeUtils.offset(requestedPages, offset);
            return requestedPages;
        } else if (Arrays.equals(requestedPages, ALL_PAGES_ARRAY)
                && isAllPages(writtenPages, pageCount)) {
            // We requested all pages via the special constant and got all
            // of them as an explicit enumeration. Hence, the printer has
            // to print only the requested pages.
            return ALL_PAGES_ARRAY;
        }

        return null;
    }

    private static boolean isAllPages(PageRange[] pageRanges, int pageCount) {
        return pageRanges.length > 0 && pageRanges[0].getStart() == 0
                && pageRanges[pageRanges.length - 1].getEnd() == pageCount - 1;
    }

    static final class UpdateSpec {
        final PrintAttributes attributes = new PrintAttributes.Builder().build();
        boolean preview;
        PageRange[] pages;

        public void update(PrintAttributes attributes, boolean preview,
                PageRange[] pages) {
            this.attributes.copyFrom(attributes);
            this.preview = preview;
            this.pages = Arrays.copyOf(pages, pages.length);
        }

        public void reset() {
            attributes.clear();
            preview = false;
            pages = null;
        }

        public boolean hasSameConstraints(PrintAttributes attributes, boolean preview) {
            return this.attributes.equals(attributes) && this.preview == preview;
        }
    }

    public static final class RemotePrintDocumentInfo {
        public PrintAttributes attributes;
        public Bundle metadata;
        public PrintDocumentInfo info;
        public PageRange[] printedPages;
        public PageRange[] writtenPages;
        public File file;
    }

    private interface CommandDoneCallback {
        public void onDone();
    }

    private static abstract class AsyncCommand implements Runnable {
        private static final int STATE_PENDING = 0;
        private static final int STATE_RUNNING = 1;
        private static final int STATE_COMPLETED = 2;
        private static final int STATE_CANCELED = 3;
        private static final int STATE_CANCELING = 4;
        private static final int STATE_FAILED = 5;

        private static int sSequenceCounter;

        protected final int mSequence = sSequenceCounter++;
        protected final IPrintDocumentAdapter mAdapter;
        protected final RemotePrintDocumentInfo mDocument;

        protected final CommandDoneCallback mDoneCallback;

        protected ICancellationSignal mCancellation;

        private CharSequence mError;

        private int mState = STATE_PENDING;

        public AsyncCommand(IPrintDocumentAdapter adapter, RemotePrintDocumentInfo document,
                CommandDoneCallback doneCallback) {
            mAdapter = adapter;
            mDocument = document;
            mDoneCallback = doneCallback;
        }

        protected final boolean isCanceling() {
            return mState == STATE_CANCELING;
        }

        public final boolean isCanceled() {
            return mState == STATE_CANCELED;
        }

        public final void cancel() {
            if (isRunning()) {
                canceling();
                if (mCancellation != null) {
                    try {
                        mCancellation.cancel();
                    } catch (RemoteException re) {
                        Log.w(LOG_TAG, "Error while canceling", re);
                    }
                }
            } else {
                canceled();

                // Done.
                mDoneCallback.onDone();
            }
        }

        protected final void canceling() {
            if (mState != STATE_PENDING && mState != STATE_RUNNING) {
                throw new IllegalStateException("Command not pending or running.");
            }
            mState = STATE_CANCELING;
        }

        protected final void canceled() {
            if (mState != STATE_CANCELING) {
                throw new IllegalStateException("Not canceling.");
            }
            mState = STATE_CANCELED;
        }

        public final boolean isPending() {
            return mState == STATE_PENDING;
        }

        protected final void running() {
            if (mState != STATE_PENDING) {
                throw new IllegalStateException("Not pending.");
            }
            mState = STATE_RUNNING;
        }

        public final boolean isRunning() {
            return mState == STATE_RUNNING;
        }

        protected final void completed() {
            if (mState != STATE_RUNNING && mState != STATE_CANCELING) {
                throw new IllegalStateException("Not running.");
            }
            mState = STATE_COMPLETED;
        }

        public final boolean isCompleted() {
            return mState == STATE_COMPLETED;
        }

        protected final void failed(CharSequence error) {
            if (mState != STATE_RUNNING) {
                throw new IllegalStateException("Not running.");
            }
            mState = STATE_FAILED;

            mError = error;
        }

        public final boolean isFailed() {
            return mState == STATE_FAILED;
        }

        public CharSequence getError() {
            return mError;
        }
    }

    private static final class LayoutCommand extends AsyncCommand {
        private final PrintAttributes mOldAttributes = new PrintAttributes.Builder().build();
        private final PrintAttributes mNewAttributes = new PrintAttributes.Builder().build();
        private final Bundle mMetadata = new Bundle();

        private final ILayoutResultCallback mRemoteResultCallback;

        private final Handler mHandler;

        private boolean mDocumentChanged;

        public LayoutCommand(Looper looper, IPrintDocumentAdapter adapter,
                RemotePrintDocumentInfo document, PrintAttributes oldAttributes,
                PrintAttributes newAttributes, boolean preview, CommandDoneCallback callback) {
            super(adapter, document, callback);
            mHandler = new LayoutHandler(looper);
            mRemoteResultCallback = new LayoutResultCallback(mHandler);
            mOldAttributes.copyFrom(oldAttributes);
            mNewAttributes.copyFrom(newAttributes);
            mMetadata.putBoolean(PrintDocumentAdapter.EXTRA_PRINT_PREVIEW, preview);
        }

        public boolean isDocumentChanged() {
            return mDocumentChanged;
        }

        @Override
        public void run() {
            running();

            try {
                if (DEBUG) {
                    Log.i(LOG_TAG, "[PERFORMING] layout");
                }
                mAdapter.layout(mOldAttributes, mNewAttributes, mRemoteResultCallback,
                        mMetadata, mSequence);
            } catch (RemoteException re) {
                Log.e(LOG_TAG, "Error calling layout", re);
                handleOnLayoutFailed(null, mSequence);
            }
        }

        private void handleOnLayoutStarted(ICancellationSignal cancellation, int sequence) {
            if (sequence != mSequence) {
                return;
            }

            if (DEBUG) {
                Log.i(LOG_TAG, "[CALLBACK] onLayoutStarted");
            }

            if (isCanceling()) {
                try {
                    cancellation.cancel();
                } catch (RemoteException re) {
                    Log.e(LOG_TAG, "Error cancelling", re);
                    handleOnLayoutFailed(null, mSequence);
                }
            } else {
                mCancellation = cancellation;
            }
        }

        private void handleOnLayoutFinished(PrintDocumentInfo info,
                boolean changed, int sequence) {
            if (sequence != mSequence) {
                return;
            }

            if (DEBUG) {
                Log.i(LOG_TAG, "[CALLBACK] onLayoutFinished");
            }

            completed();

            // If the document description changed or the content in the
            // document changed, the we need to invalidate the pages.
            if (changed || !equalsIgnoreSize(mDocument.info, info)) {
                // If the content changed we throw away all pages as
                // we will request them again with the new content.
                mDocument.writtenPages = null;
                mDocument.printedPages = null;
                mDocumentChanged = true;
            }

            // Update the document with data from the layout pass.
            mDocument.attributes = mNewAttributes;
            mDocument.metadata = mMetadata;
            mDocument.info = info;

            // Release the remote cancellation interface.
            mCancellation = null;

            // Done.
            mDoneCallback.onDone();
        }

        private void handleOnLayoutFailed(CharSequence error, int sequence) {
            if (sequence != mSequence) {
                return;
            }

            if (DEBUG) {
                Log.i(LOG_TAG, "[CALLBACK] onLayoutFailed");
            }

            failed(error);

            // Release the remote cancellation interface.
            mCancellation = null;

            // Failed.
            mDoneCallback.onDone();
        }

        private void handleOnLayoutCanceled(int sequence) {
            if (sequence != mSequence) {
                return;
            }

            if (DEBUG) {
                Log.i(LOG_TAG, "[CALLBACK] onLayoutCanceled");
            }

            canceled();

            // Release the remote cancellation interface.
            mCancellation = null;

            // Done.
            mDoneCallback.onDone();
        }

        private boolean equalsIgnoreSize(PrintDocumentInfo lhs, PrintDocumentInfo rhs) {
            if (lhs == rhs) {
                return true;
            }
            if (lhs == null) {
                return false;
            } else {
                if (rhs == null) {
                    return false;
                }
                if (lhs.getContentType() != rhs.getContentType()
                        || lhs.getPageCount() != rhs.getPageCount()) {
                    return false;
                }
            }
            return true;
        }

        private final class LayoutHandler extends Handler {
            public static final int MSG_ON_LAYOUT_STARTED = 1;
            public static final int MSG_ON_LAYOUT_FINISHED = 2;
            public static final int MSG_ON_LAYOUT_FAILED = 3;
            public static final int MSG_ON_LAYOUT_CANCELED = 4;

            public LayoutHandler(Looper looper) {
                super(looper, null, false);
            }

            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_ON_LAYOUT_STARTED: {
                        ICancellationSignal cancellation = (ICancellationSignal) message.obj;
                        final int sequence = message.arg1;
                        handleOnLayoutStarted(cancellation, sequence);
                    } break;

                    case MSG_ON_LAYOUT_FINISHED: {
                        PrintDocumentInfo info = (PrintDocumentInfo) message.obj;
                        final boolean changed = (message.arg1 == 1);
                        final int sequence = message.arg2;
                        handleOnLayoutFinished(info, changed, sequence);
                    } break;

                    case MSG_ON_LAYOUT_FAILED: {
                        CharSequence error = (CharSequence) message.obj;
                        final int sequence = message.arg1;
                        handleOnLayoutFailed(error, sequence);
                    } break;

                    case MSG_ON_LAYOUT_CANCELED: {
                        final int sequence = message.arg1;
                        handleOnLayoutCanceled(sequence);
                    } break;
                }
            }
        }

        private static final class LayoutResultCallback extends ILayoutResultCallback.Stub {
            private final WeakReference<Handler> mWeakHandler;

            public LayoutResultCallback(Handler handler) {
                mWeakHandler = new WeakReference<>(handler);
            }

            @Override
            public void onLayoutStarted(ICancellationSignal cancellation, int sequence) {
                Handler handler = mWeakHandler.get();
                if (handler != null) {
                    handler.obtainMessage(LayoutHandler.MSG_ON_LAYOUT_STARTED,
                            sequence, 0, cancellation).sendToTarget();
                }
            }

            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed, int sequence) {
                Handler handler = mWeakHandler.get();
                if (handler != null) {
                    handler.obtainMessage(LayoutHandler.MSG_ON_LAYOUT_FINISHED,
                            changed ? 1 : 0, sequence, info).sendToTarget();
                }
            }

            @Override
            public void onLayoutFailed(CharSequence error, int sequence) {
                Handler handler = mWeakHandler.get();
                if (handler != null) {
                    handler.obtainMessage(LayoutHandler.MSG_ON_LAYOUT_FAILED,
                            sequence, 0, error).sendToTarget();
                }
            }

            @Override
            public void onLayoutCanceled(int sequence) {
                Handler handler = mWeakHandler.get();
                if (handler != null) {
                    handler.obtainMessage(LayoutHandler.MSG_ON_LAYOUT_CANCELED,
                            sequence, 0).sendToTarget();
                }
            }
        }
    }

    private static final class WriteCommand extends AsyncCommand {
        private final int mPageCount;
        private final PageRange[] mPages;
        private final File mContentFile;

        private final IWriteResultCallback mRemoteResultCallback;
        private final CommandDoneCallback mDoneCallback;

        private final Context mContext;
        private final Handler mHandler;

        public WriteCommand(Context context, Looper looper, IPrintDocumentAdapter adapter,
                RemotePrintDocumentInfo document, int pageCount, PageRange[] pages,
                File contentFile, CommandDoneCallback callback) {
            super(adapter, document, callback);
            mContext = context;
            mHandler = new WriteHandler(looper);
            mRemoteResultCallback = new WriteResultCallback(mHandler);
            mPageCount = pageCount;
            mPages = Arrays.copyOf(pages, pages.length);
            mContentFile = contentFile;
            mDoneCallback = callback;
        }

        @Override
        public void run() {
            running();

            // This is a long running operation as we will be reading fully
            // the written data. In case of a cancellation, we ask the client
            // to stop writing data and close the file descriptor after
            // which we will reach the end of the stream, thus stop reading.
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    InputStream in = null;
                    OutputStream out = null;
                    ParcelFileDescriptor source = null;
                    ParcelFileDescriptor sink = null;
                    try {
                        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                        source = pipe[0];
                        sink = pipe[1];

                        in = new FileInputStream(source.getFileDescriptor());
                        out = new FileOutputStream(mContentFile);

                        // Async call to initiate the other process writing the data.
                        if (DEBUG) {
                            Log.i(LOG_TAG, "[PERFORMING] write");
                        }
                        mAdapter.write(mPages, sink, mRemoteResultCallback, mSequence);

                        // Close the source. It is now held by the client.
                        sink.close();
                        sink = null;

                        // Read the data.
                        final byte[] buffer = new byte[8192];
                        while (true) {
                            final int readByteCount = in.read(buffer);
                            if (readByteCount < 0) {
                                break;
                            }
                            out.write(buffer, 0, readByteCount);
                        }
                    } catch (RemoteException | IOException e) {
                        Log.e(LOG_TAG, "Error calling write()", e);
                    } finally {
                        IoUtils.closeQuietly(in);
                        IoUtils.closeQuietly(out);
                        IoUtils.closeQuietly(sink);
                        IoUtils.closeQuietly(source);
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        }

        private void handleOnWriteStarted(ICancellationSignal cancellation, int sequence) {
            if (sequence != mSequence) {
                return;
            }

            if (DEBUG) {
                Log.i(LOG_TAG, "[CALLBACK] onWriteStarted");
            }

            if (isCanceling()) {
                try {
                    cancellation.cancel();
                } catch (RemoteException re) {
                    Log.e(LOG_TAG, "Error cancelling", re);
                    handleOnWriteFailed(null, sequence);
                }
            } else {
                mCancellation = cancellation;
            }
        }

        private void handleOnWriteFinished(PageRange[] pages, int sequence) {
            if (sequence != mSequence) {
                return;
            }

            if (DEBUG) {
                Log.i(LOG_TAG, "[CALLBACK] onWriteFinished");
            }

            PageRange[] writtenPages = PageRangeUtils.normalize(pages);
            PageRange[] printedPages = computePrintedPages(mPages, writtenPages, mPageCount);

            // Handle if we got invalid pages
            if (printedPages != null) {
                mDocument.writtenPages = writtenPages;
                mDocument.printedPages = printedPages;
                completed();
            } else {
                mDocument.writtenPages = null;
                mDocument.printedPages = null;
                failed(mContext.getString(R.string.print_error_default_message));
            }

            // Release the remote cancellation interface.
            mCancellation = null;

            // Done.
            mDoneCallback.onDone();
        }

        private void handleOnWriteFailed(CharSequence error, int sequence) {
            if (sequence != mSequence) {
                return;
            }

            if (DEBUG) {
                Log.i(LOG_TAG, "[CALLBACK] onWriteFailed");
            }

            failed(error);

            // Release the remote cancellation interface.
            mCancellation = null;

            // Done.
            mDoneCallback.onDone();
        }

        private void handleOnWriteCanceled(int sequence) {
            if (sequence != mSequence) {
                return;
            }

            if (DEBUG) {
                Log.i(LOG_TAG, "[CALLBACK] onWriteCanceled");
            }

            canceled();

            // Release the remote cancellation interface.
            mCancellation = null;

            // Done.
            mDoneCallback.onDone();
        }

        private final class WriteHandler extends Handler {
            public static final int MSG_ON_WRITE_STARTED = 1;
            public static final int MSG_ON_WRITE_FINISHED = 2;
            public static final int MSG_ON_WRITE_FAILED = 3;
            public static final int MSG_ON_WRITE_CANCELED = 4;

            public WriteHandler(Looper looper) {
                super(looper, null, false);
            }

            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_ON_WRITE_STARTED: {
                        ICancellationSignal cancellation = (ICancellationSignal) message.obj;
                        final int sequence = message.arg1;
                        handleOnWriteStarted(cancellation, sequence);
                    } break;

                    case MSG_ON_WRITE_FINISHED: {
                        PageRange[] pages = (PageRange[]) message.obj;
                        final int sequence = message.arg1;
                        handleOnWriteFinished(pages, sequence);
                    } break;

                    case MSG_ON_WRITE_FAILED: {
                        CharSequence error = (CharSequence) message.obj;
                        final int sequence = message.arg1;
                        handleOnWriteFailed(error, sequence);
                    } break;

                    case MSG_ON_WRITE_CANCELED: {
                        final int sequence = message.arg1;
                        handleOnWriteCanceled(sequence);
                    } break;
                }
            }
        }

        private static final class WriteResultCallback extends IWriteResultCallback.Stub {
            private final WeakReference<Handler> mWeakHandler;

            public WriteResultCallback(Handler handler) {
                mWeakHandler = new WeakReference<>(handler);
            }

            @Override
            public void onWriteStarted(ICancellationSignal cancellation, int sequence) {
                Handler handler = mWeakHandler.get();
                if (handler != null) {
                    handler.obtainMessage(WriteHandler.MSG_ON_WRITE_STARTED,
                            sequence, 0, cancellation).sendToTarget();
                }
            }

            @Override
            public void onWriteFinished(PageRange[] pages, int sequence) {
                Handler handler = mWeakHandler.get();
                if (handler != null) {
                    handler.obtainMessage(WriteHandler.MSG_ON_WRITE_FINISHED,
                            sequence, 0, pages).sendToTarget();
                }
            }

            @Override
            public void onWriteFailed(CharSequence error, int sequence) {
                Handler handler = mWeakHandler.get();
                if (handler != null) {
                    handler.obtainMessage(WriteHandler.MSG_ON_WRITE_FAILED,
                        sequence, 0, error).sendToTarget();
                }
            }

            @Override
            public void onWriteCanceled(int sequence) {
                Handler handler = mWeakHandler.get();
                if (handler != null) {
                    handler.obtainMessage(WriteHandler.MSG_ON_WRITE_CANCELED,
                        sequence, 0).sendToTarget();
                }
            }
        }
    }

    private static final class PrintDocumentAdapterObserver
            extends IPrintDocumentAdapterObserver.Stub {
        private final WeakReference<RemotePrintDocument> mWeakDocument;

        public PrintDocumentAdapterObserver(RemotePrintDocument document) {
            mWeakDocument = new WeakReference<>(document);
        }

        @Override
        public void onDestroy() {
            final RemotePrintDocument document = mWeakDocument.get();
            if (document != null) {
                new Handler(document.mLooper).post(new Runnable() {
                    @Override
                    public void run() {
                        document.mDocumentObserver.onDestroy();
                    }
                });
            }
        }
    }
}
