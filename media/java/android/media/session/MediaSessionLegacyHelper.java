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

package android.media.session;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Helper for connecting existing APIs up to the new session APIs. This can be
 * used by RCC, AudioFocus, etc. to create a single session that translates to
 * all those components.
 *
 * @hide
 */
public class MediaSessionLegacyHelper {
    private static final String TAG = "MediaSessionHelper";
    private static final boolean DEBUG = true;

    private static final Object sLock = new Object();
    private static MediaSessionLegacyHelper sInstance;

    private MediaSessionManager mSessionManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    // The legacy APIs use PendingIntents to register/unregister media button
    // receivers and these are associated with RCC.
    private ArrayMap<PendingIntent, SessionHolder> mSessions
            = new ArrayMap<PendingIntent, SessionHolder>();

    private MediaSessionLegacyHelper(Context context) {
        mSessionManager = (MediaSessionManager) context
                .getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public static MediaSessionLegacyHelper getHelper(Context context) {
        if (DEBUG) {
            Log.d(TAG, "Attempting to get helper with context " + context);
        }
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new MediaSessionLegacyHelper(context);
            }
        }
        return sInstance;
    }

    public static Bundle getOldMetadata(MediaMetadata metadata) {
        Bundle oldMetadata = new Bundle();
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_ALBUM)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_ART)) {
            oldMetadata.putParcelable(String.valueOf(MediaMetadataEditor.BITMAP_KEY_ARTWORK),
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ART));
        } else if (metadata.containsKey(MediaMetadata.METADATA_KEY_ALBUM_ART)) {
            // Fall back to album art if the track art wasn't available
            oldMetadata.putParcelable(String.valueOf(MediaMetadataEditor.BITMAP_KEY_ARTWORK),
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_ARTIST)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_AUTHOR)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_AUTHOR),
                    metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_COMPILATION)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_COMPILATION),
                    metadata.getString(MediaMetadata.METADATA_KEY_COMPILATION));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_COMPOSER)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                    metadata.getString(MediaMetadata.METADATA_KEY_COMPOSER));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_DATE)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_DATE),
                    metadata.getString(MediaMetadata.METADATA_KEY_DATE));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_DISC_NUMBER)) {
            oldMetadata.putLong(String.valueOf(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                    metadata.getLong(MediaMetadata.METADATA_KEY_DISC_NUMBER));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
            oldMetadata.putLong(String.valueOf(MediaMetadataRetriever.METADATA_KEY_DURATION),
                    metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_GENRE)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    metadata.getString(MediaMetadata.METADATA_KEY_GENRE));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_NUM_TRACKS)) {
            oldMetadata.putLong(String.valueOf(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS),
                    metadata.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_RATING)) {
            oldMetadata.putParcelable(String.valueOf(MediaMetadataEditor.RATING_KEY_BY_OTHERS),
                    metadata.getRating(MediaMetadata.METADATA_KEY_RATING));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_USER_RATING)) {
            oldMetadata.putParcelable(String.valueOf(MediaMetadataEditor.RATING_KEY_BY_USER),
                    metadata.getRating(MediaMetadata.METADATA_KEY_USER_RATING));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_TITLE)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_TRACK_NUMBER)) {
            oldMetadata.putLong(
                    String.valueOf(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                    metadata.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_WRITER)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_WRITER),
                    metadata.getString(MediaMetadata.METADATA_KEY_WRITER));
        }
        if (metadata.containsKey(MediaMetadata.METADATA_KEY_YEAR)) {
            oldMetadata.putString(String.valueOf(MediaMetadataRetriever.METADATA_KEY_YEAR),
                    metadata.getString(MediaMetadata.METADATA_KEY_YEAR));
        }
        return oldMetadata;
    }

    public MediaSession getSession(PendingIntent pi) {
        SessionHolder holder = mSessions.get(pi);
        return holder == null ? null : holder.mSession;
    }

    public void sendMediaButtonEvent(KeyEvent keyEvent, boolean needWakeLock) {
        mSessionManager.dispatchMediaKeyEvent(keyEvent, needWakeLock);
        if (DEBUG) {
            Log.d(TAG, "dispatched media key " + keyEvent);
        }
    }

    public void sendAdjustVolumeBy(int suggestedStream, int delta, int flags) {
        mSessionManager.dispatchAdjustVolumeBy(suggestedStream, delta, flags);
        if (DEBUG) {
            Log.d(TAG, "dispatched volume adjustment");
        }
    }

    public void addRccListener(PendingIntent pi,
            MediaSession.TransportControlsCallback listener) {
        if (pi == null) {
            Log.w(TAG, "Pending intent was null, can't add rcc listener.");
            return;
        }
        SessionHolder holder = getHolder(pi, true);
        if (holder.mRccListener != null) {
            if (holder.mRccListener == listener) {
                if (DEBUG) {
                    Log.d(TAG, "addRccListener listener already added.");
                }
                // This is already the registered listener, ignore
                return;
            }
            // Otherwise it changed so we need to switch to the new one
            holder.mSession.removeTransportControlsCallback(holder.mRccListener);
        }
        holder.mSession.addTransportControlsCallback(listener, mHandler);
        holder.mRccListener = listener;
        holder.mFlags |= MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS;
        holder.mSession.setFlags(holder.mFlags);
        holder.update();
        if (DEBUG) {
            Log.d(TAG, "Added rcc listener for " + pi + ".");
        }
    }

    public void removeRccListener(PendingIntent pi) {
        if (pi == null) {
            return;
        }
        SessionHolder holder = getHolder(pi, false);
        if (holder != null && holder.mRccListener != null) {
            holder.mSession.removeTransportControlsCallback(holder.mRccListener);
            holder.mRccListener = null;
            holder.mFlags &= ~MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS;
            holder.mSession.setFlags(holder.mFlags);
            holder.update();
            if (DEBUG) {
                Log.d(TAG, "Removed rcc listener for " + pi + ".");
            }
        }
    }

    public void addMediaButtonListener(PendingIntent pi,
            Context context) {
        if (pi == null) {
            Log.w(TAG, "Pending intent was null, can't addMediaButtonListener.");
            return;
        }
        SessionHolder holder = getHolder(pi, true);
        if (holder.mMediaButtonListener != null) {
            // Already have this listener registered, but update it anyway as
            // the extras may have changed.
            if (DEBUG) {
                Log.d(TAG, "addMediaButtonListener already added " + pi);
            }
            return;
        }
        holder.mMediaButtonListener = new MediaButtonListener(pi, context);
        // TODO determine if handling transport performer commands should also
        // set this flag
        holder.mFlags |= MediaSession.FLAG_HANDLES_MEDIA_BUTTONS;
        holder.mSession.setFlags(holder.mFlags);
        holder.mSession.addTransportControlsCallback(holder.mMediaButtonListener, mHandler);

        holder.mMediaButtonReceiver = new MediaButtonReceiver(pi, context);
        holder.mSession.addCallback(holder.mMediaButtonReceiver, mHandler);
        if (DEBUG) {
            Log.d(TAG, "addMediaButtonListener added " + pi);
        }
    }

    public void removeMediaButtonListener(PendingIntent pi) {
        if (pi == null) {
            return;
        }
        SessionHolder holder = getHolder(pi, false);
        if (holder != null && holder.mMediaButtonListener != null) {
            holder.mSession.removeTransportControlsCallback(holder.mMediaButtonListener);
            holder.mFlags &= ~MediaSession.FLAG_HANDLES_MEDIA_BUTTONS;
            holder.mSession.setFlags(holder.mFlags);
            holder.mMediaButtonListener = null;

            holder.mSession.removeCallback(holder.mMediaButtonReceiver);
            holder.mMediaButtonReceiver = null;
            holder.update();
            if (DEBUG) {
                Log.d(TAG, "removeMediaButtonListener removed " + pi);
            }
        }
    }

    private SessionHolder getHolder(PendingIntent pi, boolean createIfMissing) {
        SessionHolder holder = mSessions.get(pi);
        if (holder == null && createIfMissing) {
            MediaSession session = mSessionManager.createSession(TAG);
            session.setActive(true);
            holder = new SessionHolder(session, pi);
            mSessions.put(pi, holder);
        }
        return holder;
    }

    private static void sendKeyEvent(PendingIntent pi, Context context, Intent intent) {
        try {
            pi.send(context, 0, intent);
        } catch (CanceledException e) {
            Log.e(TAG, "Error sending media key down event:", e);
            // Don't bother sending up if down failed
            return;
        }
    }

    private static final class MediaButtonReceiver extends MediaSession.Callback {
        private final PendingIntent mPendingIntent;
        private final Context mContext;

        public MediaButtonReceiver(PendingIntent pi, Context context) {
            mPendingIntent = pi;
            mContext = context;
        }

        @Override
        public void onMediaButtonEvent(Intent mediaButtonIntent) {
            MediaSessionLegacyHelper.sendKeyEvent(mPendingIntent, mContext, mediaButtonIntent);
        }
    }

    private static final class MediaButtonListener extends MediaSession.TransportControlsCallback {
        private final PendingIntent mPendingIntent;
        private final Context mContext;

        public MediaButtonListener(PendingIntent pi, Context context) {
            mPendingIntent = pi;
            mContext = context;
        }

        @Override
        public void onPlay() {
            sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY);
        }

        @Override
        public void onPause() {
            sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE);
        }

        @Override
        public void onSkipToNext() {
            sendKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
        }

        @Override
        public void onSkipToPrevious() {
            sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        @Override
        public void onFastForward() {
            sendKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        }

        @Override
        public void onRewind() {
            sendKeyEvent(KeyEvent.KEYCODE_MEDIA_REWIND);
        }

        @Override
        public void onStop() {
            sendKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP);
        }

        private void sendKeyEvent(int keyCode) {
            KeyEvent ke = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);

            intent.putExtra(Intent.EXTRA_KEY_EVENT, ke);
            MediaSessionLegacyHelper.sendKeyEvent(mPendingIntent, mContext, intent);

            ke = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            intent.putExtra(Intent.EXTRA_KEY_EVENT, ke);
            MediaSessionLegacyHelper.sendKeyEvent(mPendingIntent, mContext, intent);

            if (DEBUG) {
                Log.d(TAG, "Sent " + keyCode + " to pending intent " + mPendingIntent);
            }
        }
    }

    private class SessionHolder {
        public final MediaSession mSession;
        public final PendingIntent mPi;
        public MediaButtonListener mMediaButtonListener;
        public MediaButtonReceiver mMediaButtonReceiver;
        public MediaSession.TransportControlsCallback mRccListener;
        public int mFlags;

        public SessionHolder(MediaSession session, PendingIntent pi) {
            mSession = session;
            mPi = pi;
        }

        public void update() {
            if (mMediaButtonListener == null && mRccListener == null) {
                mSession.release();
                mSessions.remove(mPi);
            }
        }
    }
}
