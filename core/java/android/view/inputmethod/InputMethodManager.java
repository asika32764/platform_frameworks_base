/*
 * Copyright (C) 2007-2008 The Android Open Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.view.inputmethod;

import com.android.internal.os.SomeArgs;
import com.android.internal.view.IInputConnectionWrapper;
import com.android.internal.view.IInputContext;
import com.android.internal.view.IInputMethodClient;
import com.android.internal.view.IInputMethodManager;
import com.android.internal.view.IInputMethodSession;
import com.android.internal.view.InputBindResult;

import android.content.Context;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.Trace;
import android.text.style.SuggestionSpan;
import android.util.Log;
import android.util.Pools.Pool;
import android.util.Pools.SimplePool;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.util.SparseArray;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventSender;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewRootImpl;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Central system API to the overall input method framework (IMF) architecture,
 * which arbitrates interaction between applications and the current input method.
 * You can retrieve an instance of this interface with
 * {@link Context#getSystemService(String) Context.getSystemService()}.
 * 
 * <p>Topics covered here:
 * <ol>
 * <li><a href="#ArchitectureOverview">Architecture Overview</a>
 * <li><a href="#Applications">Applications</a>
 * <li><a href="#InputMethods">Input Methods</a>
 * <li><a href="#Security">Security</a>
 * </ol>
 * 
 * <a name="ArchitectureOverview"></a>
 * <h3>Architecture Overview</h3>
 * 
 * <p>There are three primary parties involved in the input method
 * framework (IMF) architecture:</p>
 * 
 * <ul>
 * <li> The <strong>input method manager</strong> as expressed by this class
 * is the central point of the system that manages interaction between all
 * other parts.  It is expressed as the client-side API here which exists
 * in each application context and communicates with a global system service
 * that manages the interaction across all processes.
 * <li> An <strong>input method (IME)</strong> implements a particular
 * interaction model allowing the user to generate text.  The system binds
 * to the current input method that is use, causing it to be created and run,
 * and tells it when to hide and show its UI.  Only one IME is running at a time.
 * <li> Multiple <strong>client applications</strong> arbitrate with the input
 * method manager for input focus and control over the state of the IME.  Only
 * one such client is ever active (working with the IME) at a time.
 * </ul>
 * 
 * 
 * <a name="Applications"></a>
 * <h3>Applications</h3>
 * 
 * <p>In most cases, applications that are using the standard
 * {@link android.widget.TextView} or its subclasses will have little they need
 * to do to work well with soft input methods.  The main things you need to
 * be aware of are:</p>
 * 
 * <ul>
 * <li> Properly set the {@link android.R.attr#inputType} in your editable
 * text views, so that the input method will have enough context to help the
 * user in entering text into them.
 * <li> Deal well with losing screen space when the input method is
 * displayed.  Ideally an application should handle its window being resized
 * smaller, but it can rely on the system performing panning of the window
 * if needed.  You should set the {@link android.R.attr#windowSoftInputMode}
 * attribute on your activity or the corresponding values on windows you
 * create to help the system determine whether to pan or resize (it will
 * try to determine this automatically but may get it wrong).
 * <li> You can also control the preferred soft input state (open, closed, etc)
 * for your window using the same {@link android.R.attr#windowSoftInputMode}
 * attribute.
 * </ul>
 * 
 * <p>More finer-grained control is available through the APIs here to directly
 * interact with the IMF and its IME -- either showing or hiding the input
 * area, letting the user pick an input method, etc.</p>
 * 
 * <p>For the rare people amongst us writing their own text editors, you
 * will need to implement {@link android.view.View#onCreateInputConnection}
 * to return a new instance of your own {@link InputConnection} interface
 * allowing the IME to interact with your editor.</p>
 * 
 * 
 * <a name="InputMethods"></a>
 * <h3>Input Methods</h3>
 * 
 * <p>An input method (IME) is implemented
 * as a {@link android.app.Service}, typically deriving from
 * {@link android.inputmethodservice.InputMethodService}.  It must provide
 * the core {@link InputMethod} interface, though this is normally handled by
 * {@link android.inputmethodservice.InputMethodService} and implementors will
 * only need to deal with the higher-level API there.</p>
 * 
 * See the {@link android.inputmethodservice.InputMethodService} class for
 * more information on implementing IMEs.
 * 
 * 
 * <a name="Security"></a>
 * <h3>Security</h3>
 * 
 * <p>There are a lot of security issues associated with input methods,
 * since they essentially have freedom to completely drive the UI and monitor
 * everything the user enters.  The Android input method framework also allows
 * arbitrary third party IMEs, so care must be taken to restrict their
 * selection and interactions.</p>
 * 
 * <p>Here are some key points about the security architecture behind the
 * IMF:</p>
 * 
 * <ul>
 * <li> <p>Only the system is allowed to directly access an IME's
 * {@link InputMethod} interface, via the
 * {@link android.Manifest.permission#BIND_INPUT_METHOD} permission.  This is
 * enforced in the system by not binding to an input method service that does
 * not require this permission, so the system can guarantee no other untrusted
 * clients are accessing the current input method outside of its control.</p>
 * 
 * <li> <p>There may be many client processes of the IMF, but only one may
 * be active at a time.  The inactive clients can not interact with key
 * parts of the IMF through the mechanisms described below.</p>
 * 
 * <li> <p>Clients of an input method are only given access to its
 * {@link InputMethodSession} interface.  One instance of this interface is
 * created for each client, and only calls from the session associated with
 * the active client will be processed by the current IME.  This is enforced
 * by {@link android.inputmethodservice.AbstractInputMethodService} for normal
 * IMEs, but must be explicitly handled by an IME that is customizing the
 * raw {@link InputMethodSession} implementation.</p>
 * 
 * <li> <p>Only the active client's {@link InputConnection} will accept
 * operations.  The IMF tells each client process whether it is active, and
 * the framework enforces that in inactive processes calls on to the current
 * InputConnection will be ignored.  This ensures that the current IME can
 * only deliver events and text edits to the UI that the user sees as
 * being in focus.</p>
 * 
 * <li> <p>An IME can never interact with an {@link InputConnection} while
 * the screen is off.  This is enforced by making all clients inactive while
 * the screen is off, and prevents bad IMEs from driving the UI when the user
 * can not be aware of its behavior.</p>
 * 
 * <li> <p>A client application can ask that the system let the user pick a
 * new IME, but can not programmatically switch to one itself.  This avoids
 * malicious applications from switching the user to their own IME, which
 * remains running when the user navigates away to another application.  An
 * IME, on the other hand, <em>is</em> allowed to programmatically switch
 * the system to another IME, since it already has full control of user
 * input.</p>
 * 
 * <li> <p>The user must explicitly enable a new IME in settings before
 * they can switch to it, to confirm with the system that they know about it
 * and want to make it available for use.</p>
 * </ul>
 */
public final class InputMethodManager {
    static final boolean DEBUG = false;
    static final String TAG = "InputMethodManager";

    static final String PENDING_EVENT_COUNTER = "aq:imm";

    static InputMethodManager sInstance;

    /**
     * @hide Flag for IInputMethodManager.windowGainedFocus: a view in
     * the window has input focus.
     */
    public static final int CONTROL_WINDOW_VIEW_HAS_FOCUS = 1<<0;

    /**
     * @hide Flag for IInputMethodManager.windowGainedFocus: the focus
     * is a text editor.
     */
    public static final int CONTROL_WINDOW_IS_TEXT_EDITOR = 1<<1;

    /**
     * @hide Flag for IInputMethodManager.windowGainedFocus: this is the first
     * time the window has gotten focus.
     */
    public static final int CONTROL_WINDOW_FIRST = 1<<2;

    /**
     * @hide Flag for IInputMethodManager.startInput: this is the first
     * time the window has gotten focus.
     */
    public static final int CONTROL_START_INITIAL = 1<<8;

    /**
     * Timeout in milliseconds for delivering a key to an IME.
     */
    static final long INPUT_METHOD_NOT_RESPONDING_TIMEOUT = 2500;

    /** @hide */
    public static final int DISPATCH_IN_PROGRESS = -1;

    /** @hide */
    public static final int DISPATCH_NOT_HANDLED = 0;

    /** @hide */
    public static final int DISPATCH_HANDLED = 1;

    final IInputMethodManager mService;
    final Looper mMainLooper;
    
    // For scheduling work on the main thread.  This also serves as our
    // global lock.
    final H mH;
    
    // Our generic input connection if the current target does not have its own.
    final IInputContext mIInputContext;

    /**
     * True if this input method client is active, initially false.
     */
    boolean mActive = false;
    
    /**
     * Set whenever this client becomes inactive, to know we need to reset
     * state with the IME the next time we receive focus.
     */
    boolean mHasBeenInactive = true;
    
    /**
     * As reported by IME through InputConnection.
     */
    boolean mFullscreenMode;
    
    // -----------------------------------------------------------
    
    /**
     * This is the root view of the overall window that currently has input
     * method focus.
     */
    View mCurRootView;
    /**
     * This is the view that should currently be served by an input method,
     * regardless of the state of setting that up.
     */
    View mServedView;
    /**
     * This is then next view that will be served by the input method, when
     * we get around to updating things.
     */
    View mNextServedView;
    /**
     * This is set when we are in the process of connecting, to determine
     * when we have actually finished.
     */
    boolean mServedConnecting;
    /**
     * This is non-null when we have connected the served view; it holds
     * the attributes that were last retrieved from the served view and given
     * to the input connection.
     */
    EditorInfo mCurrentTextBoxAttribute;
    /**
     * The InputConnection that was last retrieved from the served view.
     */
    InputConnection mServedInputConnection;
    ControlledInputConnectionWrapper mServedInputConnectionWrapper;
    /**
     * The completions that were last provided by the served view.
     */
    CompletionInfo[] mCompletions;
    
    // Cursor position on the screen.
    Rect mTmpCursorRect = new Rect();
    Rect mCursorRect = new Rect();
    int mCursorSelStart;
    int mCursorSelEnd;
    int mCursorCandStart;
    int mCursorCandEnd;

    /**
     * Represents an invalid action notification sequence number. {@link InputMethodManagerService}
     * always issues a positive integer for action notification sequence numbers. Thus -1 is
     * guaranteed to be different from any valid sequence number.
     */
    private static final int NOT_AN_ACTION_NOTIFICATION_SEQUENCE_NUMBER = -1;
    /**
     * The next sequence number that is to be sent to {@link InputMethodManagerService} via
     * {@link IInputMethodManager#notifyUserAction(int)} at once when a user action is observed.
     */
    private int mNextUserActionNotificationSequenceNumber =
            NOT_AN_ACTION_NOTIFICATION_SEQUENCE_NUMBER;

    /**
     * The last sequence number that is already sent to {@link InputMethodManagerService}.
     */
    private int mLastSentUserActionNotificationSequenceNumber =
            NOT_AN_ACTION_NOTIFICATION_SEQUENCE_NUMBER;

    /**
     * The instance that has previously been sent to the input method.
     */
    private CursorAnchorInfo mCursorAnchorInfo = null;

    /**
     * The buffer to retrieve the view location in screen coordinates in {@link #updateCursor}.
     */
    private final int[] mViewTopLeft = new int[2];

    // -----------------------------------------------------------
    
    /**
     * Sequence number of this binding, as returned by the server.
     */
    int mBindSequence = -1;
    /**
     * ID of the method we are bound to.
     */
    String mCurId;
    /**
     * The actual instance of the method to make calls on it.
     */
    IInputMethodSession mCurMethod;
    InputChannel mCurChannel;
    ImeInputEventSender mCurSender;

    /**
     * The current cursor/anchor monitor mode.
     */
    int mCursorAnchorMonitorMode = InputMethodService.CURSOR_ANCHOR_MONITOR_MODE_NONE;

    final Pool<PendingEvent> mPendingEventPool = new SimplePool<PendingEvent>(20);
    final SparseArray<PendingEvent> mPendingEvents = new SparseArray<PendingEvent>(20);

    // -----------------------------------------------------------
    
    static final int MSG_DUMP = 1;
    static final int MSG_BIND = 2;
    static final int MSG_UNBIND = 3;
    static final int MSG_SET_ACTIVE = 4;
    static final int MSG_SEND_INPUT_EVENT = 5;
    static final int MSG_TIMEOUT_INPUT_EVENT = 6;
    static final int MSG_FLUSH_INPUT_EVENT = 7;
    static final int MSG_SET_CURSOR_ANCHOR_MONITOR_MODE = 8;
    static final int MSG_SET_USER_ACTION_NOTIFICATION_SEQUENCE_NUMBER = 9;

    class H extends Handler {
        H(Looper looper) {
            super(looper, null, true);
        }
        
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DUMP: {
                    SomeArgs args = (SomeArgs)msg.obj;
                    try {
                        doDump((FileDescriptor)args.arg1,
                                (PrintWriter)args.arg2, (String[])args.arg3);
                    } catch (RuntimeException e) {
                        ((PrintWriter)args.arg2).println("Exception: " + e);
                    }
                    synchronized (args.arg4) {
                        ((CountDownLatch)args.arg4).countDown();
                    }
                    args.recycle();
                    return;
                }
                case MSG_BIND: {
                    final InputBindResult res = (InputBindResult)msg.obj;
                    if (DEBUG) {
                        Log.i(TAG, "handleMessage: MSG_BIND " + res.sequence + "," + res.id);
                    }
                    synchronized (mH) {
                        if (mBindSequence < 0 || mBindSequence != res.sequence) {
                            Log.w(TAG, "Ignoring onBind: cur seq=" + mBindSequence
                                    + ", given seq=" + res.sequence);
                            if (res.channel != null && res.channel != mCurChannel) {
                                res.channel.dispose();
                            }
                            return;
                        }

                        setInputChannelLocked(res.channel);
                        mCurMethod = res.method;
                        mCurId = res.id;
                        mBindSequence = res.sequence;
                    }
                    startInputInner(null, 0, 0, 0);
                    return;
                }
                case MSG_UNBIND: {
                    final int sequence = msg.arg1;
                    if (DEBUG) {
                        Log.i(TAG, "handleMessage: MSG_UNBIND " + sequence);
                    }
                    boolean startInput = false;
                    synchronized (mH) {
                        if (mBindSequence == sequence) {
                            if (false) {
                                // XXX the server has already unbound!
                                if (mCurMethod != null && mCurrentTextBoxAttribute != null) {
                                    try {
                                        mCurMethod.finishInput();
                                    } catch (RemoteException e) {
                                        Log.w(TAG, "IME died: " + mCurId, e);
                                    }
                                }
                            }
                            clearBindingLocked();
                            
                            // If we were actively using the last input method, then
                            // we would like to re-connect to the next input method.
                            if (mServedView != null && mServedView.isFocused()) {
                                mServedConnecting = true;
                            }
                            if (mActive) {
                                startInput = true;
                            }
                        }
                    }
                    if (startInput) {
                        startInputInner(null, 0, 0, 0);
                    }
                    return;
                }
                case MSG_SET_ACTIVE: {
                    final boolean active = msg.arg1 != 0;
                    if (DEBUG) {
                        Log.i(TAG, "handleMessage: MSG_SET_ACTIVE " + active + ", was " + mActive);
                    }
                    synchronized (mH) {
                        mActive = active;
                        mFullscreenMode = false;
                        if (!active) {
                            // Some other client has starting using the IME, so note
                            // that this happened and make sure our own editor's
                            // state is reset.
                            mHasBeenInactive = true;
                            try {
                                // Note that finishComposingText() is allowed to run
                                // even when we are not active.
                                mIInputContext.finishComposingText();
                            } catch (RemoteException e) {
                            }
                            // Check focus again in case that "onWindowFocus" is called before
                            // handling this message.
                            if (mServedView != null && mServedView.hasWindowFocus()) {
                                // "finishComposingText" has been already called above. So we
                                // should not call mServedInputConnection.finishComposingText here.
                                // Also, please note that this handler thread could be different
                                // from a thread that created mServedView. That could happen
                                // the current activity is running in the system process.
                                // In that case, we really should not call
                                // mServedInputConnection.finishComposingText.
                                if (checkFocusNoStartInput(mHasBeenInactive, false)) {
                                    startInputInner(null, 0, 0, 0);
                                }
                            }
                        }
                    }
                    return;
                }
                case MSG_SEND_INPUT_EVENT: {
                    sendInputEventAndReportResultOnMainLooper((PendingEvent)msg.obj);
                    return;
                }
                case MSG_TIMEOUT_INPUT_EVENT: {
                    finishedInputEvent(msg.arg1, false, true);
                    return;
                }
                case MSG_FLUSH_INPUT_EVENT: {
                    finishedInputEvent(msg.arg1, false, false);
                    return;
                }
                case MSG_SET_CURSOR_ANCHOR_MONITOR_MODE: {
                    synchronized (mH) {
                        mCursorAnchorMonitorMode = msg.arg1;
                        // Clear the cache.
                        mCursorRect.setEmpty();
                        mCursorAnchorInfo = null;
                    }
                    return;
                }
                case MSG_SET_USER_ACTION_NOTIFICATION_SEQUENCE_NUMBER: {
                    synchronized (mH) {
                        mNextUserActionNotificationSequenceNumber = msg.arg1;
                    }
                }
            }
        }
    }
    
    private static class ControlledInputConnectionWrapper extends IInputConnectionWrapper {
        private final InputMethodManager mParentInputMethodManager;
        private boolean mActive;

        public ControlledInputConnectionWrapper(final Looper mainLooper, final InputConnection conn,
                final InputMethodManager inputMethodManager) {
            super(mainLooper, conn);
            mParentInputMethodManager = inputMethodManager;
            mActive = true;
        }

        @Override
        public boolean isActive() {
            return mParentInputMethodManager.mActive && mActive;
        }

        void deactivate() {
            mActive = false;
        }
    }
    
    final IInputMethodClient.Stub mClient = new IInputMethodClient.Stub() {
        @Override
        protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
            // No need to check for dump permission, since we only give this
            // interface to the system.
            CountDownLatch latch = new CountDownLatch(1);
            SomeArgs sargs = SomeArgs.obtain();
            sargs.arg1 = fd;
            sargs.arg2 = fout;
            sargs.arg3 = args;
            sargs.arg4 = latch;
            mH.sendMessage(mH.obtainMessage(MSG_DUMP, sargs));
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    fout.println("Timeout waiting for dump");
                }
            } catch (InterruptedException e) {
                fout.println("Interrupted waiting for dump");
            }
        }

        @Override
        public void setUsingInputMethod(boolean state) {
        }

        @Override
        public void onBindMethod(InputBindResult res) {
            mH.sendMessage(mH.obtainMessage(MSG_BIND, res));
        }

        @Override
        public void onUnbindMethod(int sequence) {
            mH.sendMessage(mH.obtainMessage(MSG_UNBIND, sequence, 0));
        }

        @Override
        public void setActive(boolean active) {
            mH.sendMessage(mH.obtainMessage(MSG_SET_ACTIVE, active ? 1 : 0, 0));
        }

        @Override
        public void setCursorAnchorMonitorMode(int monitorMode) {
            mH.sendMessage(mH.obtainMessage(MSG_SET_CURSOR_ANCHOR_MONITOR_MODE, monitorMode, 0));
        }

        @Override
        public void setUserActionNotificationSequenceNumber(int sequenceNumber) {
            mH.sendMessage(mH.obtainMessage(MSG_SET_USER_ACTION_NOTIFICATION_SEQUENCE_NUMBER,
                    sequenceNumber, 0));
        }
    };

    final InputConnection mDummyInputConnection = new BaseInputConnection(this, false);

    InputMethodManager(IInputMethodManager service, Looper looper) {
        mService = service;
        mMainLooper = looper;
        mH = new H(looper);
        mIInputContext = new ControlledInputConnectionWrapper(looper,
                mDummyInputConnection, this);
    }

    /**
     * Retrieve the global InputMethodManager instance, creating it if it
     * doesn't already exist.
     * @hide
     */
    public static InputMethodManager getInstance() {
        synchronized (InputMethodManager.class) {
            if (sInstance == null) {
                IBinder b = ServiceManager.getService(Context.INPUT_METHOD_SERVICE);
                IInputMethodManager service = IInputMethodManager.Stub.asInterface(b);
                sInstance = new InputMethodManager(service, Looper.getMainLooper());
            }
            return sInstance;
        }
    }
    
    /**
     * Private optimization: retrieve the global InputMethodManager instance,
     * if it exists.
     * @hide
     */
    public static InputMethodManager peekInstance() {
        return sInstance;
    }
    
    /** @hide */
    public IInputMethodClient getClient() {
        return mClient;
    }
    
    /** @hide */
    public IInputContext getInputContext() {
        return mIInputContext;
    }
    
    public List<InputMethodInfo> getInputMethodList() {
        try {
            return mService.getInputMethodList();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public List<InputMethodInfo> getEnabledInputMethodList() {
        try {
            return mService.getEnabledInputMethodList();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a list of enabled input method subtypes for the specified input method info.
     * @param imi An input method info whose subtypes list will be returned.
     * @param allowsImplicitlySelectedSubtypes A boolean flag to allow to return the implicitly
     * selected subtypes. If an input method info doesn't have enabled subtypes, the framework
     * will implicitly enable subtypes according to the current system language.
     */
    public List<InputMethodSubtype> getEnabledInputMethodSubtypeList(InputMethodInfo imi,
            boolean allowsImplicitlySelectedSubtypes) {
        try {
            return mService.getEnabledInputMethodSubtypeList(
                    imi == null ? null : imi.getId(), allowsImplicitlySelectedSubtypes);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public void showStatusIcon(IBinder imeToken, String packageName, int iconId) {
        try {
            mService.updateStatusIcon(imeToken, packageName, iconId);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public void hideStatusIcon(IBinder imeToken) {
        try {
            mService.updateStatusIcon(imeToken, null, 0);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** @hide */
    public void setImeWindowStatus(IBinder imeToken, int vis, int backDisposition) {
        try {
            mService.setImeWindowStatus(imeToken, vis, backDisposition);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** @hide */
    public void setFullscreenMode(boolean fullScreen) {
        mFullscreenMode = fullScreen;
    }

    /** @hide */
    public void registerSuggestionSpansForNotification(SuggestionSpan[] spans) {
        try {
            mService.registerSuggestionSpansForNotification(spans);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** @hide */
    public void notifySuggestionPicked(SuggestionSpan span, String originalString, int index) {
        try {
            mService.notifySuggestionPicked(span, originalString, index);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Allows you to discover whether the attached input method is running
     * in fullscreen mode.  Return true if it is fullscreen, entirely covering
     * your UI, else returns false.
     */
    public boolean isFullscreenMode() {
        return mFullscreenMode;
    }
    
    /**
     * Return true if the given view is the currently active view for the
     * input method.
     */
    public boolean isActive(View view) {
        checkFocus();
        synchronized (mH) {
            return (mServedView == view
                    || (mServedView != null
                            && mServedView.checkInputConnectionProxy(view)))
                    && mCurrentTextBoxAttribute != null;
        }
    }
    
    /**
     * Return true if any view is currently active in the input method.
     */
    public boolean isActive() {
        checkFocus();
        synchronized (mH) {
            return mServedView != null && mCurrentTextBoxAttribute != null;
        }
    }
    
    /**
     * Return true if the currently served view is accepting full text edits.
     * If false, it has no input connection, so can only handle raw key events.
     */
    public boolean isAcceptingText() {
        checkFocus();
        return mServedInputConnection != null;
    }

    /**
     * Reset all of the state associated with being bound to an input method.
     */
    void clearBindingLocked() {
        if (DEBUG) Log.v(TAG, "Clearing binding!");
        clearConnectionLocked();
        setInputChannelLocked(null);
        mBindSequence = -1;
        mCurId = null;
        mCurMethod = null;
    }

    void setInputChannelLocked(InputChannel channel) {
        if (mCurChannel != channel) {
            if (mCurSender != null) {
                flushPendingEventsLocked();
                mCurSender.dispose();
                mCurSender = null;
            }
            if (mCurChannel != null) {
                mCurChannel.dispose();
            }
            mCurChannel = channel;
        }
    }

    /**
     * Reset all of the state associated with a served view being connected
     * to an input method
     */
    void clearConnectionLocked() {
        mCurrentTextBoxAttribute = null;
        mServedInputConnection = null;
        if (mServedInputConnectionWrapper != null) {
            mServedInputConnectionWrapper.deactivate();
            mServedInputConnectionWrapper = null;
        }
    }
    
    /**
     * Disconnect any existing input connection, clearing the served view.
     */
    void finishInputLocked() {
        mCurRootView = null;
        mNextServedView = null;
        if (mServedView != null) {
            if (DEBUG) Log.v(TAG, "FINISH INPUT: " + mServedView);
            
            if (mCurrentTextBoxAttribute != null) {
                try {
                    mService.finishInput(mClient);
                } catch (RemoteException e) {
                }
            }
            
            notifyInputConnectionFinished();
            
            mServedView = null;
            mCompletions = null;
            mServedConnecting = false;
            clearConnectionLocked();
        }
    }

    /**
     * Notifies the served view that the current InputConnection will no longer be used.
     */
    private void notifyInputConnectionFinished() {
        if (mServedView != null && mServedInputConnection != null) {
            // We need to tell the previously served view that it is no
            // longer the input target, so it can reset its state.  Schedule
            // this call on its window's Handler so it will be on the correct
            // thread and outside of our lock.
            ViewRootImpl viewRootImpl = mServedView.getViewRootImpl();
            if (viewRootImpl != null) {
                // This will result in a call to reportFinishInputConnection() below.
                viewRootImpl.dispatchFinishInputConnection(mServedInputConnection);
            }
        }
    }

    /**
     * Called from the FINISH_INPUT_CONNECTION message above.
     * @hide
     */
    public void reportFinishInputConnection(InputConnection ic) {
        if (mServedInputConnection != ic) {
            ic.finishComposingText();
            // To avoid modifying the public InputConnection interface
            if (ic instanceof BaseInputConnection) {
                ((BaseInputConnection) ic).reportFinish();
            }
        }
    }

    public void displayCompletions(View view, CompletionInfo[] completions) {
        checkFocus();
        synchronized (mH) {
            if (mServedView != view && (mServedView == null
                            || !mServedView.checkInputConnectionProxy(view))) {
                return;
            }
            
            mCompletions = completions;
            if (mCurMethod != null) {
                try {
                    mCurMethod.displayCompletions(mCompletions);
                } catch (RemoteException e) {
                }
            }
        }
    }
    
    public void updateExtractedText(View view, int token, ExtractedText text) {
        checkFocus();
        synchronized (mH) {
            if (mServedView != view && (mServedView == null
                    || !mServedView.checkInputConnectionProxy(view))) {
                return;
            }
            
            if (mCurMethod != null) {
                try {
                    mCurMethod.updateExtractedText(token, text);
                } catch (RemoteException e) {
                }
            }
        }
    }
    
    /**
     * Flag for {@link #showSoftInput} to indicate that this is an implicit
     * request to show the input window, not as the result of a direct request
     * by the user.  The window may not be shown in this case.
     */
    public static final int SHOW_IMPLICIT = 0x0001;
    
    /**
     * Flag for {@link #showSoftInput} to indicate that the user has forced
     * the input method open (such as by long-pressing menu) so it should
     * not be closed until they explicitly do so.
     */
    public static final int SHOW_FORCED = 0x0002;
    
    /**
     * Synonym for {@link #showSoftInput(View, int, ResultReceiver)} without
     * a result receiver: explicitly request that the current input method's
     * soft input area be shown to the user, if needed.
     * 
     * @param view The currently focused view, which would like to receive
     * soft keyboard input.
     * @param flags Provides additional operating flags.  Currently may be
     * 0 or have the {@link #SHOW_IMPLICIT} bit set.
     */
    public boolean showSoftInput(View view, int flags) {
        return showSoftInput(view, flags, null);
    }
    
    /**
     * Flag for the {@link ResultReceiver} result code from
     * {@link #showSoftInput(View, int, ResultReceiver)} and
     * {@link #hideSoftInputFromWindow(IBinder, int, ResultReceiver)}: the
     * state of the soft input window was unchanged and remains shown.
     */
    public static final int RESULT_UNCHANGED_SHOWN = 0;
    
    /**
     * Flag for the {@link ResultReceiver} result code from
     * {@link #showSoftInput(View, int, ResultReceiver)} and
     * {@link #hideSoftInputFromWindow(IBinder, int, ResultReceiver)}: the
     * state of the soft input window was unchanged and remains hidden.
     */
    public static final int RESULT_UNCHANGED_HIDDEN = 1;
    
    /**
     * Flag for the {@link ResultReceiver} result code from
     * {@link #showSoftInput(View, int, ResultReceiver)} and
     * {@link #hideSoftInputFromWindow(IBinder, int, ResultReceiver)}: the
     * state of the soft input window changed from hidden to shown.
     */
    public static final int RESULT_SHOWN = 2;
    
    /**
     * Flag for the {@link ResultReceiver} result code from
     * {@link #showSoftInput(View, int, ResultReceiver)} and
     * {@link #hideSoftInputFromWindow(IBinder, int, ResultReceiver)}: the
     * state of the soft input window changed from shown to hidden.
     */
    public static final int RESULT_HIDDEN = 3;
    
    /**
     * Explicitly request that the current input method's soft input area be
     * shown to the user, if needed.  Call this if the user interacts with
     * your view in such a way that they have expressed they would like to
     * start performing input into it.
     * 
     * @param view The currently focused view, which would like to receive
     * soft keyboard input.
     * @param flags Provides additional operating flags.  Currently may be
     * 0 or have the {@link #SHOW_IMPLICIT} bit set.
     * @param resultReceiver If non-null, this will be called by the IME when
     * it has processed your request to tell you what it has done.  The result
     * code you receive may be either {@link #RESULT_UNCHANGED_SHOWN},
     * {@link #RESULT_UNCHANGED_HIDDEN}, {@link #RESULT_SHOWN}, or
     * {@link #RESULT_HIDDEN}.
     */
    public boolean showSoftInput(View view, int flags, ResultReceiver resultReceiver) {
        checkFocus();
        synchronized (mH) {
            if (mServedView != view && (mServedView == null
                    || !mServedView.checkInputConnectionProxy(view))) {
                return false;
            }

            try {
                return mService.showSoftInput(mClient, flags, resultReceiver);
            } catch (RemoteException e) {
            }
            
            return false;
        }
    }
    
    /** @hide */
    public void showSoftInputUnchecked(int flags, ResultReceiver resultReceiver) {
        try {
            mService.showSoftInput(mClient, flags, resultReceiver);
        } catch (RemoteException e) {
        }
    }
    
    /**
     * Flag for {@link #hideSoftInputFromWindow} to indicate that the soft
     * input window should only be hidden if it was not explicitly shown
     * by the user.
     */
    public static final int HIDE_IMPLICIT_ONLY = 0x0001;
    
    /**
     * Flag for {@link #hideSoftInputFromWindow} to indicate that the soft
     * input window should normally be hidden, unless it was originally
     * shown with {@link #SHOW_FORCED}.
     */
    public static final int HIDE_NOT_ALWAYS = 0x0002;

    /**
     * Synonym for {@link #hideSoftInputFromWindow(IBinder, int, ResultReceiver)}
     * without a result: request to hide the soft input window from the
     * context of the window that is currently accepting input.
     * 
     * @param windowToken The token of the window that is making the request,
     * as returned by {@link View#getWindowToken() View.getWindowToken()}.
     * @param flags Provides additional operating flags.  Currently may be
     * 0 or have the {@link #HIDE_IMPLICIT_ONLY} bit set.
     */
    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags) {
        return hideSoftInputFromWindow(windowToken, flags, null);
    }
    
    /**
     * Request to hide the soft input window from the context of the window
     * that is currently accepting input.  This should be called as a result
     * of the user doing some actually than fairly explicitly requests to
     * have the input window hidden.
     * 
     * @param windowToken The token of the window that is making the request,
     * as returned by {@link View#getWindowToken() View.getWindowToken()}.
     * @param flags Provides additional operating flags.  Currently may be
     * 0 or have the {@link #HIDE_IMPLICIT_ONLY} bit set.
     * @param resultReceiver If non-null, this will be called by the IME when
     * it has processed your request to tell you what it has done.  The result
     * code you receive may be either {@link #RESULT_UNCHANGED_SHOWN},
     * {@link #RESULT_UNCHANGED_HIDDEN}, {@link #RESULT_SHOWN}, or
     * {@link #RESULT_HIDDEN}.
     */
    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags,
            ResultReceiver resultReceiver) {
        checkFocus();
        synchronized (mH) {
            if (mServedView == null || mServedView.getWindowToken() != windowToken) {
                return false;
            }

            try {
                return mService.hideSoftInput(mClient, flags, resultReceiver);
            } catch (RemoteException e) {
            }
            return false;
        }
    }
    

    /**
     * This method toggles the input method window display.
     * If the input window is already displayed, it gets hidden. 
     * If not the input window will be displayed.
     * @param windowToken The token of the window that is making the request,
     * as returned by {@link View#getWindowToken() View.getWindowToken()}.
     * @param showFlags Provides additional operating flags.  May be
     * 0 or have the {@link #SHOW_IMPLICIT},
     * {@link #SHOW_FORCED} bit set.
     * @param hideFlags Provides additional operating flags.  May be
     * 0 or have the {@link #HIDE_IMPLICIT_ONLY},
     * {@link #HIDE_NOT_ALWAYS} bit set.
     **/
    public void toggleSoftInputFromWindow(IBinder windowToken, int showFlags, int hideFlags) {
        synchronized (mH) {
            if (mServedView == null || mServedView.getWindowToken() != windowToken) {
                return;
            }
            if (mCurMethod != null) {
                try {
                    mCurMethod.toggleSoftInput(showFlags, hideFlags);
                } catch (RemoteException e) {
                }
            }
        }
    }

    /*
     * This method toggles the input method window display.
     * If the input window is already displayed, it gets hidden. 
     * If not the input window will be displayed.
     * @param showFlags Provides additional operating flags.  May be
     * 0 or have the {@link #SHOW_IMPLICIT},
     * {@link #SHOW_FORCED} bit set.
     * @param hideFlags Provides additional operating flags.  May be
     * 0 or have the {@link #HIDE_IMPLICIT_ONLY},
     * {@link #HIDE_NOT_ALWAYS} bit set.
     * @hide
     */
    public void toggleSoftInput(int showFlags, int hideFlags) {
        if (mCurMethod != null) {
            try {
                mCurMethod.toggleSoftInput(showFlags, hideFlags);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * If the input method is currently connected to the given view,
     * restart it with its new contents.  You should call this when the text
     * within your view changes outside of the normal input method or key
     * input flow, such as when an application calls TextView.setText().
     * 
     * @param view The view whose text has changed.
     */
    public void restartInput(View view) {
        checkFocus();
        synchronized (mH) {
            if (mServedView != view && (mServedView == null
                    || !mServedView.checkInputConnectionProxy(view))) {
                return;
            }
            
            mServedConnecting = true;
        }
        
        startInputInner(null, 0, 0, 0);
    }
    
    boolean startInputInner(IBinder windowGainingFocus, int controlFlags, int softInputMode,
            int windowFlags) {
        final View view;
        synchronized (mH) {
            view = mServedView;
            
            // Make sure we have a window token for the served view.
            if (DEBUG) Log.v(TAG, "Starting input: view=" + view);
            if (view == null) {
                if (DEBUG) Log.v(TAG, "ABORT input: no served view!");
                return false;
            }
        }
        
        // Now we need to get an input connection from the served view.
        // This is complicated in a couple ways: we can't be holding our lock
        // when calling out to the view, and we need to make sure we call into
        // the view on the same thread that is driving its view hierarchy.
        Handler vh = view.getHandler();
        if (vh == null) {
            // If the view doesn't have a handler, something has changed out
            // from under us, so just close the current input.
            // If we don't close the current input, the current input method can remain on the
            // screen without a connection.
            if (DEBUG) Log.v(TAG, "ABORT input: no handler for view! Close current input.");
            closeCurrentInput();
            return false;
        }
        if (vh.getLooper() != Looper.myLooper()) {
            // The view is running on a different thread than our own, so
            // we need to reschedule our work for over there.
            if (DEBUG) Log.v(TAG, "Starting input: reschedule to view thread");
            vh.post(new Runnable() {
                @Override
                public void run() {
                    startInputInner(null, 0, 0, 0);
                }
            });
            return false;
        }
        
        // Okay we are now ready to call into the served view and have it
        // do its stuff.
        // Life is good: let's hook everything up!
        EditorInfo tba = new EditorInfo();
        tba.packageName = view.getContext().getPackageName();
        tba.fieldId = view.getId();
        InputConnection ic = view.onCreateInputConnection(tba);
        if (DEBUG) Log.v(TAG, "Starting input: tba=" + tba + " ic=" + ic);

        synchronized (mH) {
            // Now that we are locked again, validate that our state hasn't
            // changed.
            if (mServedView != view || !mServedConnecting) {
                // Something else happened, so abort.
                if (DEBUG) Log.v(TAG, 
                        "Starting input: finished by someone else (view="
                        + mServedView + " conn=" + mServedConnecting + ")");
                return false;
            }

            // If we already have a text box, then this view is already
            // connected so we want to restart it.
            if (mCurrentTextBoxAttribute == null) {
                controlFlags |= CONTROL_START_INITIAL;
            }
            
            // Hook 'em up and let 'er rip.
            mCurrentTextBoxAttribute = tba;
            mServedConnecting = false;
            // Notify the served view that its previous input connection is finished
            notifyInputConnectionFinished();
            mServedInputConnection = ic;
            ControlledInputConnectionWrapper servedContext;
            if (ic != null) {
                mCursorSelStart = tba.initialSelStart;
                mCursorSelEnd = tba.initialSelEnd;
                mCursorCandStart = -1;
                mCursorCandEnd = -1;
                mCursorRect.setEmpty();
                mCursorAnchorInfo = null;
                servedContext = new ControlledInputConnectionWrapper(vh.getLooper(), ic, this);
            } else {
                servedContext = null;
            }
            if (mServedInputConnectionWrapper != null) {
                mServedInputConnectionWrapper.deactivate();
            }
            mServedInputConnectionWrapper = servedContext;
            
            try {
                if (DEBUG) Log.v(TAG, "START INPUT: " + view + " ic="
                        + ic + " tba=" + tba + " controlFlags=#"
                        + Integer.toHexString(controlFlags));
                InputBindResult res;
                if (windowGainingFocus != null) {
                    res = mService.windowGainedFocus(mClient, windowGainingFocus,
                            controlFlags, softInputMode, windowFlags,
                            tba, servedContext);
                } else {
                    res = mService.startInput(mClient,
                            servedContext, tba, controlFlags);
                }
                if (DEBUG) Log.v(TAG, "Starting input: Bind result=" + res);
                if (res != null) {
                    if (res.id != null) {
                        setInputChannelLocked(res.channel);
                        mBindSequence = res.sequence;
                        mCurMethod = res.method;
                        mCurId = res.id;
                        mNextUserActionNotificationSequenceNumber =
                                res.userActionNotificationSequenceNumber;
                    } else {
                        if (res.channel != null && res.channel != mCurChannel) {
                            res.channel.dispose();
                        }
                        if (mCurMethod == null) {
                            // This means there is no input method available.
                            if (DEBUG) Log.v(TAG, "ABORT input: no input method!");
                            return true;
                        }
                    }
                }
                if (mCurMethod != null && mCompletions != null) {
                    try {
                        mCurMethod.displayCompletions(mCompletions);
                    } catch (RemoteException e) {
                    }
                }
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
            }
        }

        return true;
    }

    /**
     * When the focused window is dismissed, this method is called to finish the
     * input method started before.
     * @hide
     */
    public void windowDismissed(IBinder appWindowToken) {
        checkFocus();
        synchronized (mH) {
            if (mServedView != null &&
                    mServedView.getWindowToken() == appWindowToken) {
                finishInputLocked();
            }
        }
    }

    /**
     * Call this when a view receives focus.
     * @hide
     */
    public void focusIn(View view) {
        synchronized (mH) {
            focusInLocked(view);
        }
    }

    void focusInLocked(View view) {
        if (DEBUG) Log.v(TAG, "focusIn: " + view);
        
        if (mCurRootView != view.getRootView()) {
            // This is a request from a window that isn't in the window with
            // IME focus, so ignore it.
            if (DEBUG) Log.v(TAG, "Not IME target window, ignoring");
            return;
        }
        
        mNextServedView = view;
        scheduleCheckFocusLocked(view);
    }

    /**
     * Call this when a view loses focus.
     * @hide
     */
    public void focusOut(View view) {
        synchronized (mH) {
            if (DEBUG) Log.v(TAG, "focusOut: " + view
                    + " mServedView=" + mServedView
                    + " winFocus=" + view.hasWindowFocus());
            if (mServedView != view) {
                // The following code would auto-hide the IME if we end up
                // with no more views with focus.  This can happen, however,
                // whenever we go into touch mode, so it ends up hiding
                // at times when we don't really want it to.  For now it
                // seems better to just turn it all off.
                if (false && view.hasWindowFocus()) {
                    mNextServedView = null;
                    scheduleCheckFocusLocked(view);
                }
            }
        }
    }

    static void scheduleCheckFocusLocked(View view) {
        ViewRootImpl viewRootImpl = view.getViewRootImpl();
        if (viewRootImpl != null) {
            viewRootImpl.dispatchCheckFocus();
        }
    }

    /**
     * @hide
     */
    public void checkFocus() {
        if (checkFocusNoStartInput(false, true)) {
            startInputInner(null, 0, 0, 0);
        }
    }

    private boolean checkFocusNoStartInput(boolean forceNewFocus, boolean finishComposingText) {
        // This is called a lot, so short-circuit before locking.
        if (mServedView == mNextServedView && !forceNewFocus) {
            return false;
        }

        InputConnection ic = null;
        synchronized (mH) {
            if (mServedView == mNextServedView && !forceNewFocus) {
                return false;
            }
            if (DEBUG) Log.v(TAG, "checkFocus: view=" + mServedView
                    + " next=" + mNextServedView
                    + " forceNewFocus=" + forceNewFocus
                    + " package="
                    + (mServedView != null ? mServedView.getContext().getPackageName() : "<none>"));

            if (mNextServedView == null) {
                finishInputLocked();
                // In this case, we used to have a focused view on the window,
                // but no longer do.  We should make sure the input method is
                // no longer shown, since it serves no purpose.
                closeCurrentInput();
                return false;
            }

            ic = mServedInputConnection;

            mServedView = mNextServedView;
            mCurrentTextBoxAttribute = null;
            mCompletions = null;
            mServedConnecting = true;
        }

        if (finishComposingText && ic != null) {
            ic.finishComposingText();
        }

        return true;
    }
    
    void closeCurrentInput() {
        try {
            mService.hideSoftInput(mClient, HIDE_NOT_ALWAYS, null);
        } catch (RemoteException e) {
        }
    }

    /**
     * Called by ViewAncestor when its window gets input focus.
     * @hide
     */
    public void onWindowFocus(View rootView, View focusedView, int softInputMode,
            boolean first, int windowFlags) {
        boolean forceNewFocus = false;
        synchronized (mH) {
            if (DEBUG) Log.v(TAG, "onWindowFocus: " + focusedView
                    + " softInputMode=" + softInputMode
                    + " first=" + first + " flags=#"
                    + Integer.toHexString(windowFlags));
            if (mHasBeenInactive) {
                if (DEBUG) Log.v(TAG, "Has been inactive!  Starting fresh");
                mHasBeenInactive = false;
                forceNewFocus = true;
            }
            focusInLocked(focusedView != null ? focusedView : rootView);
        }

        int controlFlags = 0;
        if (focusedView != null) {
            controlFlags |= CONTROL_WINDOW_VIEW_HAS_FOCUS;
            if (focusedView.onCheckIsTextEditor()) {
                controlFlags |= CONTROL_WINDOW_IS_TEXT_EDITOR;
            }
        }
        if (first) {
            controlFlags |= CONTROL_WINDOW_FIRST;
        }
        
        if (checkFocusNoStartInput(forceNewFocus, true)) {
            // We need to restart input on the current focus view.  This
            // should be done in conjunction with telling the system service
            // about the window gaining focus, to help make the transition
            // smooth.
            if (startInputInner(rootView.getWindowToken(),
                    controlFlags, softInputMode, windowFlags)) {
                return;
            }
        }
        
        // For some reason we didn't do a startInput + windowFocusGain, so
        // we'll just do a window focus gain and call it a day.
        synchronized (mH) {
            try {
                if (DEBUG) Log.v(TAG, "Reporting focus gain, without startInput");
                mService.windowGainedFocus(mClient, rootView.getWindowToken(),
                        controlFlags, softInputMode, windowFlags, null, null);
            } catch (RemoteException e) {
            }
        }
    }
    
    /** @hide */
    public void startGettingWindowFocus(View rootView) {
        synchronized (mH) {
            mCurRootView = rootView;
        }
    }
    
    /**
     * Report the current selection range.
     *
     * <p><strong>Editor authors</strong>, you need to call this method whenever
     * the cursor moves in your editor. Remember that in addition to doing this, your
     * editor needs to always supply current cursor values in
     * {@link EditorInfo#initialSelStart} and {@link EditorInfo#initialSelEnd} every
     * time {@link android.view.View#onCreateInputConnection(EditorInfo)} is
     * called, which happens whenever the keyboard shows up or the focus changes
     * to a text field, among other cases.</p>
     */
    public void updateSelection(View view, int selStart, int selEnd,
            int candidatesStart, int candidatesEnd) {
        checkFocus();
        synchronized (mH) {
            if ((mServedView != view && (mServedView == null
                        || !mServedView.checkInputConnectionProxy(view)))
                    || mCurrentTextBoxAttribute == null || mCurMethod == null) {
                return;
            }

            if (mCursorSelStart != selStart || mCursorSelEnd != selEnd
                    || mCursorCandStart != candidatesStart
                    || mCursorCandEnd != candidatesEnd) {
                if (DEBUG) Log.d(TAG, "updateSelection");

                try {
                    if (DEBUG) Log.v(TAG, "SELECTION CHANGE: " + mCurMethod);
                    final int oldSelStart = mCursorSelStart;
                    final int oldSelEnd = mCursorSelEnd;
                    // Update internal values before sending updateSelection to the IME, because
                    // if it changes the text within its onUpdateSelection handler in a way that
                    // does not move the cursor we don't want to call it again with the same values.
                    mCursorSelStart = selStart;
                    mCursorSelEnd = selEnd;
                    mCursorCandStart = candidatesStart;
                    mCursorCandEnd = candidatesEnd;
                    mCurMethod.updateSelection(oldSelStart, oldSelEnd,
                            selStart, selEnd, candidatesStart, candidatesEnd);
                } catch (RemoteException e) {
                    Log.w(TAG, "IME died: " + mCurId, e);
                }
            }
        }
    }

    /**
     * Notify the event when the user tapped or clicked the text view.
     */
    public void viewClicked(View view) {
        final boolean focusChanged = mServedView != mNextServedView;
        checkFocus();
        synchronized (mH) {
            if ((mServedView != view && (mServedView == null
                    || !mServedView.checkInputConnectionProxy(view)))
                    || mCurrentTextBoxAttribute == null || mCurMethod == null) {
                return;
            }
            try {
                if (DEBUG) Log.v(TAG, "onViewClicked: " + focusChanged);
                mCurMethod.viewClicked(focusChanged);
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
            }
        }
    }

    /**
     * Returns true if the current input method wants to watch the location
     * of the input editor's cursor in its window.
     */
    public boolean isWatchingCursor(View view) {
        if (!isActive(view)) {
            return false;
        }
        synchronized (mH) {
            return (mCursorAnchorMonitorMode &
                    InputMethodService.CURSOR_ANCHOR_MONITOR_MODE_CURSOR_RECT) != 0;
        }
    }

    /**
     * Returns true if the current input method wants to receive the cursor rectangle in
     * screen coordinates rather than local coordinates in the attached view.
     *
     * @hide
     */
    public boolean usesScreenCoordinatesForCursorLocked() {
        // {@link InputMethodService#CURSOR_ANCHOR_MONITOR_MODE_CURSOR_RECT} also means
        // that {@link InputMethodService#onUpdateCursor} should provide the cursor rectangle
        // in screen coordinates rather than local coordinates.
        return (mCursorAnchorMonitorMode &
                InputMethodService.CURSOR_ANCHOR_MONITOR_MODE_CURSOR_RECT) != 0;
    }

    /**
     * Set cursor/anchor monitor mode via {@link com.android.server.InputMethodManagerService}.
     * This is an internal method for {@link android.inputmethodservice.InputMethodService} and
     * should never be used from IMEs and applications.
     *
     * @hide
     */
    public void setCursorAnchorMonitorMode(IBinder imeToken, int monitorMode) {
        try {
            mService.setCursorAnchorMonitorMode(imeToken, monitorMode);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Report the current cursor location in its window.
     */
    public void updateCursor(View view, int left, int top, int right, int bottom) {
        checkFocus();
        synchronized (mH) {
            if ((mServedView != view && (mServedView == null
                        || !mServedView.checkInputConnectionProxy(view)))
                    || mCurrentTextBoxAttribute == null || mCurMethod == null) {
                return;
            }
            if (DEBUG) Log.d(TAG, "updateCursor");
            mTmpCursorRect.set(left, top, right, bottom);
            if (!Objects.equals(mCursorRect, mTmpCursorRect)) {
                try {
                    if (DEBUG) Log.v(TAG, "CURSOR CHANGE: " + mCurMethod);
                    mCursorRect.set(mTmpCursorRect);
                    if (usesScreenCoordinatesForCursorLocked()) {
                        view.getLocationOnScreen(mViewTopLeft);
                        mTmpCursorRect.offset(mViewTopLeft[0], mViewTopLeft[1]);
                    }
                    mCurMethod.updateCursor(mTmpCursorRect);
                } catch (RemoteException e) {
                    Log.w(TAG, "IME died: " + mCurId, e);
                }
            }
        }
    }

    /**
     * Report positional change of the text insertion point and/or characters in the composition
     * string.
     */
    public void updateCursorAnchorInfo(View view, final CursorAnchorInfo cursorAnchorInfo) {
        if (view == null || cursorAnchorInfo == null) {
            return;
        }
        checkFocus();
        synchronized (mH) {
            if ((mServedView != view &&
                    (mServedView == null || !mServedView.checkInputConnectionProxy(view)))
                    || mCurrentTextBoxAttribute == null || mCurMethod == null) {
                return;
            }
            if (Objects.equals(mCursorAnchorInfo, cursorAnchorInfo)) {
                Log.w(TAG, "Ignoring redundant updateCursorAnchorInfo: info=" + cursorAnchorInfo);
                return;
            }
            if (DEBUG) Log.v(TAG, "updateCursorAnchorInfo: " + cursorAnchorInfo);
            try {
                mCurMethod.updateCursorAnchorInfo(cursorAnchorInfo);
                mCursorAnchorInfo = cursorAnchorInfo;
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
            }
        }
    }

    /**
     * Call {@link InputMethodSession#appPrivateCommand(String, Bundle)
     * InputMethodSession.appPrivateCommand()} on the current Input Method.
     * @param view Optional View that is sending the command, or null if
     * you want to send the command regardless of the view that is attached
     * to the input method.
     * @param action Name of the command to be performed.  This <em>must</em>
     * be a scoped name, i.e. prefixed with a package name you own, so that
     * different developers will not create conflicting commands.
     * @param data Any data to include with the command.
     */
    public void sendAppPrivateCommand(View view, String action, Bundle data) {
        checkFocus();
        synchronized (mH) {
            if ((mServedView != view && (mServedView == null
                        || !mServedView.checkInputConnectionProxy(view)))
                    || mCurrentTextBoxAttribute == null || mCurMethod == null) {
                return;
            }
            try {
                if (DEBUG) Log.v(TAG, "APP PRIVATE COMMAND " + action + ": " + data);
                mCurMethod.appPrivateCommand(action, data);
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
            }
        }
    }

    /**
     * Force switch to a new input method component. This can only be called
     * from an application or a service which has a token of the currently active input method.
     * @param token Supplies the identifying token given to an input method
     * when it was started, which allows it to perform this operation on
     * itself.
     * @param id The unique identifier for the new input method to be switched to.
     */
    public void setInputMethod(IBinder token, String id) {
        try {
            mService.setInputMethod(token, id);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Force switch to a new input method and subtype. This can only be called
     * from an application or a service which has a token of the currently active input method.
     * @param token Supplies the identifying token given to an input method
     * when it was started, which allows it to perform this operation on
     * itself.
     * @param id The unique identifier for the new input method to be switched to.
     * @param subtype The new subtype of the new input method to be switched to.
     */
    public void setInputMethodAndSubtype(IBinder token, String id, InputMethodSubtype subtype) {
        try {
            mService.setInputMethodAndSubtype(token, id, subtype);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Close/hide the input method's soft input area, so the user no longer
     * sees it or can interact with it.  This can only be called
     * from the currently active input method, as validated by the given token.
     * 
     * @param token Supplies the identifying token given to an input method
     * when it was started, which allows it to perform this operation on
     * itself.
     * @param flags Provides additional operating flags.  Currently may be
     * 0 or have the {@link #HIDE_IMPLICIT_ONLY},
     * {@link #HIDE_NOT_ALWAYS} bit set.
     */
    public void hideSoftInputFromInputMethod(IBinder token, int flags) {
        try {
            mService.hideMySoftInput(token, flags);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Show the input method's soft input area, so the user 
     * sees the input method window and can interact with it.
     * This can only be called from the currently active input method,
     * as validated by the given token.
     * 
     * @param token Supplies the identifying token given to an input method
     * when it was started, which allows it to perform this operation on
     * itself.
     * @param flags Provides additional operating flags.  Currently may be
     * 0 or have the {@link #SHOW_IMPLICIT} or
     * {@link #SHOW_FORCED} bit set.
     */
    public void showSoftInputFromInputMethod(IBinder token, int flags) {
        try {
            mService.showMySoftInput(token, flags);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Dispatches an input event to the IME.
     *
     * Returns {@link #DISPATCH_HANDLED} if the event was handled.
     * Returns {@link #DISPATCH_NOT_HANDLED} if the event was not handled.
     * Returns {@link #DISPATCH_IN_PROGRESS} if the event is in progress and the
     * callback will be invoked later.
     *
     * @hide
     */
    public int dispatchInputEvent(InputEvent event, Object token,
            FinishedInputEventCallback callback, Handler handler) {
        synchronized (mH) {
            if (mCurMethod != null) {
                if (event instanceof KeyEvent) {
                    KeyEvent keyEvent = (KeyEvent)event;
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN
                            && keyEvent.getKeyCode() == KeyEvent.KEYCODE_SYM
                            && keyEvent.getRepeatCount() == 0) {
                        showInputMethodPickerLocked();
                        return DISPATCH_HANDLED;
                    }
                }

                if (DEBUG) Log.v(TAG, "DISPATCH INPUT EVENT: " + mCurMethod);

                PendingEvent p = obtainPendingEventLocked(
                        event, token, mCurId, callback, handler);
                if (mMainLooper.isCurrentThread()) {
                    // Already running on the IMM thread so we can send the event immediately.
                    return sendInputEventOnMainLooperLocked(p);
                }

                // Post the event to the IMM thread.
                Message msg = mH.obtainMessage(MSG_SEND_INPUT_EVENT, p);
                msg.setAsynchronous(true);
                mH.sendMessage(msg);
                return DISPATCH_IN_PROGRESS;
            }
        }
        return DISPATCH_NOT_HANDLED;
    }

    // Must be called on the main looper
    void sendInputEventAndReportResultOnMainLooper(PendingEvent p) {
        final boolean handled;
        synchronized (mH) {
            int result = sendInputEventOnMainLooperLocked(p);
            if (result == DISPATCH_IN_PROGRESS) {
                return;
            }

            handled = (result == DISPATCH_HANDLED);
        }

        invokeFinishedInputEventCallback(p, handled);
    }

    // Must be called on the main looper
    int sendInputEventOnMainLooperLocked(PendingEvent p) {
        if (mCurChannel != null) {
            if (mCurSender == null) {
                mCurSender = new ImeInputEventSender(mCurChannel, mH.getLooper());
            }

            final InputEvent event = p.mEvent;
            final int seq = event.getSequenceNumber();
            if (mCurSender.sendInputEvent(seq, event)) {
                mPendingEvents.put(seq, p);
                Trace.traceCounter(Trace.TRACE_TAG_INPUT, PENDING_EVENT_COUNTER,
                        mPendingEvents.size());

                Message msg = mH.obtainMessage(MSG_TIMEOUT_INPUT_EVENT, p);
                msg.setAsynchronous(true);
                mH.sendMessageDelayed(msg, INPUT_METHOD_NOT_RESPONDING_TIMEOUT);
                return DISPATCH_IN_PROGRESS;
            }

            Log.w(TAG, "Unable to send input event to IME: "
                    + mCurId + " dropping: " + event);
        }
        return DISPATCH_NOT_HANDLED;
    }

    void finishedInputEvent(int seq, boolean handled, boolean timeout) {
        final PendingEvent p;
        synchronized (mH) {
            int index = mPendingEvents.indexOfKey(seq);
            if (index < 0) {
                return; // spurious, event already finished or timed out
            }

            p = mPendingEvents.valueAt(index);
            mPendingEvents.removeAt(index);
            Trace.traceCounter(Trace.TRACE_TAG_INPUT, PENDING_EVENT_COUNTER, mPendingEvents.size());

            if (timeout) {
                Log.w(TAG, "Timeout waiting for IME to handle input event after "
                        + INPUT_METHOD_NOT_RESPONDING_TIMEOUT + " ms: " + p.mInputMethodId);
            } else {
                mH.removeMessages(MSG_TIMEOUT_INPUT_EVENT, p);
            }
        }

        invokeFinishedInputEventCallback(p, handled);
    }

    // Assumes the event has already been removed from the queue.
    void invokeFinishedInputEventCallback(PendingEvent p, boolean handled) {
        p.mHandled = handled;
        if (p.mHandler.getLooper().isCurrentThread()) {
            // Already running on the callback handler thread so we can send the
            // callback immediately.
            p.run();
        } else {
            // Post the event to the callback handler thread.
            // In this case, the callback will be responsible for recycling the event.
            Message msg = Message.obtain(p.mHandler, p);
            msg.setAsynchronous(true);
            msg.sendToTarget();
        }
    }

    private void flushPendingEventsLocked() {
        mH.removeMessages(MSG_FLUSH_INPUT_EVENT);

        final int count = mPendingEvents.size();
        for (int i = 0; i < count; i++) {
            int seq = mPendingEvents.keyAt(i);
            Message msg = mH.obtainMessage(MSG_FLUSH_INPUT_EVENT, seq, 0);
            msg.setAsynchronous(true);
            msg.sendToTarget();
        }
    }

    private PendingEvent obtainPendingEventLocked(InputEvent event, Object token,
            String inputMethodId, FinishedInputEventCallback callback, Handler handler) {
        PendingEvent p = mPendingEventPool.acquire();
        if (p == null) {
            p = new PendingEvent();
        }
        p.mEvent = event;
        p.mToken = token;
        p.mInputMethodId = inputMethodId;
        p.mCallback = callback;
        p.mHandler = handler;
        return p;
    }

    private void recyclePendingEventLocked(PendingEvent p) {
        p.recycle();
        mPendingEventPool.release(p);
    }

    public void showInputMethodPicker() {
        synchronized (mH) {
            showInputMethodPickerLocked();
        }
    }

    private void showInputMethodPickerLocked() {
        try {
            mService.showInputMethodPickerFromClient(mClient);
        } catch (RemoteException e) {
            Log.w(TAG, "IME died: " + mCurId, e);
        }
    }

    /**
     * Show the settings for enabling subtypes of the specified input method.
     * @param imiId An input method, whose subtypes settings will be shown. If imiId is null,
     * subtypes of all input methods will be shown.
     */
    public void showInputMethodAndSubtypeEnabler(String imiId) {
        synchronized (mH) {
            try {
                mService.showInputMethodAndSubtypeEnablerFromClient(mClient, imiId);
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
            }
        }
    }

    /**
     * Returns the current input method subtype. This subtype is one of the subtypes in
     * the current input method. This method returns null when the current input method doesn't
     * have any input method subtype.
     */
    public InputMethodSubtype getCurrentInputMethodSubtype() {
        synchronized (mH) {
            try {
                return mService.getCurrentInputMethodSubtype();
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
                return null;
            }
        }
    }

    /**
     * Switch to a new input method subtype of the current input method.
     * @param subtype A new input method subtype to switch.
     * @return true if the current subtype was successfully switched. When the specified subtype is
     * null, this method returns false.
     */
    public boolean setCurrentInputMethodSubtype(InputMethodSubtype subtype) {
        synchronized (mH) {
            try {
                return mService.setCurrentInputMethodSubtype(subtype);
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
                return false;
            }
        }
    }

    /**
     * Notify that a user took some action with this input method.
     * @hide
     */
    public void notifyUserAction() {
        synchronized (mH) {
            if (mLastSentUserActionNotificationSequenceNumber ==
                    mNextUserActionNotificationSequenceNumber) {
                if (DEBUG) {
                    Log.w(TAG, "Ignoring notifyUserAction as it has already been sent."
                            + " mLastSentUserActionNotificationSequenceNumber: "
                            + mLastSentUserActionNotificationSequenceNumber
                            + " mNextUserActionNotificationSequenceNumber: "
                            + mNextUserActionNotificationSequenceNumber);
                }
                return;
            }
            try {
                if (DEBUG) {
                    Log.w(TAG, "notifyUserAction: "
                            + " mLastSentUserActionNotificationSequenceNumber: "
                            + mLastSentUserActionNotificationSequenceNumber
                            + " mNextUserActionNotificationSequenceNumber: "
                            + mNextUserActionNotificationSequenceNumber);
                }
                mService.notifyUserAction(mNextUserActionNotificationSequenceNumber);
                mLastSentUserActionNotificationSequenceNumber =
                        mNextUserActionNotificationSequenceNumber;
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
            }
        }
    }

    /**
     * Returns a map of all shortcut input method info and their subtypes.
     */
    public Map<InputMethodInfo, List<InputMethodSubtype>> getShortcutInputMethodsAndSubtypes() {
        synchronized (mH) {
            HashMap<InputMethodInfo, List<InputMethodSubtype>> ret =
                    new HashMap<InputMethodInfo, List<InputMethodSubtype>>();
            try {
                // TODO: We should change the return type from List<Object> to List<Parcelable>
                List<Object> info = mService.getShortcutInputMethodsAndSubtypes();
                // "info" has imi1, subtype1, subtype2, imi2, subtype2, imi3, subtype3..in the list
                ArrayList<InputMethodSubtype> subtypes = null;
                final int N = info.size();
                if (info != null && N > 0) {
                    for (int i = 0; i < N; ++i) {
                        Object o = info.get(i);
                        if (o instanceof InputMethodInfo) {
                            if (ret.containsKey(o)) {
                                Log.e(TAG, "IMI list already contains the same InputMethod.");
                                break;
                            }
                            subtypes = new ArrayList<InputMethodSubtype>();
                            ret.put((InputMethodInfo)o, subtypes);
                        } else if (subtypes != null && o instanceof InputMethodSubtype) {
                            subtypes.add((InputMethodSubtype)o);
                        }
                    }
                }
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
            }
            return ret;
        }
    }

    /**
     * @return The current height of the input method window.
     * @hide
     */
    public int getInputMethodWindowVisibleHeight() {
        synchronized (mH) {
            try {
                return mService.getInputMethodWindowVisibleHeight();
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
                return 0;
            }
        }
    }

    /**
     * Force switch to the last used input method and subtype. If the last input method didn't have
     * any subtypes, the framework will simply switch to the last input method with no subtype
     * specified.
     * @param imeToken Supplies the identifying token given to an input method when it was started,
     * which allows it to perform this operation on itself.
     * @return true if the current input method and subtype was successfully switched to the last
     * used input method and subtype.
     */
    public boolean switchToLastInputMethod(IBinder imeToken) {
        synchronized (mH) {
            try {
                return mService.switchToLastInputMethod(imeToken);
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
                return false;
            }
        }
    }

    /**
     * Force switch to the next input method and subtype. If there is no IME enabled except
     * current IME and subtype, do nothing.
     * @param imeToken Supplies the identifying token given to an input method when it was started,
     * which allows it to perform this operation on itself.
     * @param onlyCurrentIme if true, the framework will find the next subtype which
     * belongs to the current IME
     * @return true if the current input method and subtype was successfully switched to the next
     * input method and subtype.
     */
    public boolean switchToNextInputMethod(IBinder imeToken, boolean onlyCurrentIme) {
        synchronized (mH) {
            try {
                return mService.switchToNextInputMethod(imeToken, onlyCurrentIme);
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
                return false;
            }
        }
    }

    /**
     * Returns true if the current IME needs to offer the users ways to switch to a next input
     * method (e.g. a globe key.).
     * When an IME sets supportsSwitchingToNextInputMethod and this method returns true,
     * the IME has to offer ways to to invoke {@link #switchToNextInputMethod} accordingly.
     * <p> Note that the system determines the most appropriate next input method
     * and subtype in order to provide the consistent user experience in switching
     * between IMEs and subtypes.
     * @param imeToken Supplies the identifying token given to an input method when it was started,
     * which allows it to perform this operation on itself.
     */
    public boolean shouldOfferSwitchingToNextInputMethod(IBinder imeToken) {
        synchronized (mH) {
            try {
                return mService.shouldOfferSwitchingToNextInputMethod(imeToken);
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
                return false;
            }
        }
    }

    /**
     * Set additional input method subtypes. Only a process which shares the same uid with the IME
     * can add additional input method subtypes to the IME.
     * Please note that a subtype's status is stored in the system.
     * For example, enabled subtypes are remembered by the framework even after they are removed
     * by using this method. If you re-add the same subtypes again,
     * they will just get enabled. If you want to avoid such conflicts, for instance, you may
     * want to create a "different" new subtype even with the same locale and mode,
     * by changing its extra value. The different subtype won't get affected by the stored past
     * status. (You may want to take a look at {@link InputMethodSubtype#hashCode()} to refer
     * to the current implementation.)
     * @param imiId Id of InputMethodInfo which additional input method subtypes will be added to.
     * @param subtypes subtypes will be added as additional subtypes of the current input method.
     */
    public void setAdditionalInputMethodSubtypes(String imiId, InputMethodSubtype[] subtypes) {
        synchronized (mH) {
            try {
                mService.setAdditionalInputMethodSubtypes(imiId, subtypes);
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
            }
        }
    }

    public InputMethodSubtype getLastInputMethodSubtype() {
        synchronized (mH) {
            try {
                return mService.getLastInputMethodSubtype();
            } catch (RemoteException e) {
                Log.w(TAG, "IME died: " + mCurId, e);
                return null;
            }
        }
    }

    void doDump(FileDescriptor fd, PrintWriter fout, String[] args) {
        final Printer p = new PrintWriterPrinter(fout);
        p.println("Input method client state for " + this + ":");
        
        p.println("  mService=" + mService);
        p.println("  mMainLooper=" + mMainLooper);
        p.println("  mIInputContext=" + mIInputContext);
        p.println("  mActive=" + mActive
                + " mHasBeenInactive=" + mHasBeenInactive
                + " mBindSequence=" + mBindSequence
                + " mCurId=" + mCurId);
        p.println("  mCurMethod=" + mCurMethod);
        p.println("  mCurRootView=" + mCurRootView);
        p.println("  mServedView=" + mServedView);
        p.println("  mNextServedView=" + mNextServedView);
        p.println("  mServedConnecting=" + mServedConnecting);
        if (mCurrentTextBoxAttribute != null) {
            p.println("  mCurrentTextBoxAttribute:");
            mCurrentTextBoxAttribute.dump(p, "    ");
        } else {
            p.println("  mCurrentTextBoxAttribute: null");
        }
        p.println("  mServedInputConnection=" + mServedInputConnection);
        p.println("  mCompletions=" + mCompletions);
        p.println("  mCursorRect=" + mCursorRect);
        p.println("  mCursorSelStart=" + mCursorSelStart
                + " mCursorSelEnd=" + mCursorSelEnd
                + " mCursorCandStart=" + mCursorCandStart
                + " mCursorCandEnd=" + mCursorCandEnd);
        p.println("  mNextUserActionNotificationSequenceNumber="
                + mNextUserActionNotificationSequenceNumber
                + " mLastSentUserActionNotificationSequenceNumber="
                + mLastSentUserActionNotificationSequenceNumber);
    }

    /**
     * Callback that is invoked when an input event that was dispatched to
     * the IME has been finished.
     * @hide
     */
    public interface FinishedInputEventCallback {
        public void onFinishedInputEvent(Object token, boolean handled);
    }

    private final class ImeInputEventSender extends InputEventSender {
        public ImeInputEventSender(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEventFinished(int seq, boolean handled) {
            finishedInputEvent(seq, handled, false);
        }
    }

    private final class PendingEvent implements Runnable {
        public InputEvent mEvent;
        public Object mToken;
        public String mInputMethodId;
        public FinishedInputEventCallback mCallback;
        public Handler mHandler;
        public boolean mHandled;

        public void recycle() {
            mEvent = null;
            mToken = null;
            mInputMethodId = null;
            mCallback = null;
            mHandler = null;
            mHandled = false;
        }

        @Override
        public void run() {
            mCallback.onFinishedInputEvent(mToken, mHandled);

            synchronized (mH) {
                recyclePendingEventLocked(this);
            }
        }
    }
}
