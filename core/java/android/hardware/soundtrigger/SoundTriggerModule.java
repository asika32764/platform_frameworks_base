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

package android.hardware.soundtrigger;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import java.lang.ref.WeakReference;
import java.util.UUID;

/**
 * The SoundTriggerModule provides APIs to control sound models and sound detection
 * on a given sound trigger hardware module.
 *
 * @hide
 */
public class SoundTriggerModule {
    private long mNativeContext;

    private int mId;
    private NativeEventHandlerDelegate mEventHandlerDelegate;

    private static final int EVENT_RECOGNITION = 1;
    private static final int EVENT_SERVICE_DIED = 2;

    SoundTriggerModule(int moduleId, SoundTrigger.StatusListener listener, Handler handler) {
        mId = moduleId;
        mEventHandlerDelegate = new NativeEventHandlerDelegate(listener, handler);
        native_setup(new WeakReference<SoundTriggerModule>(this));
    }
    private native void native_setup(Object module_this);

    @Override
    protected void finalize() {
        native_finalize();
    }
    private native void native_finalize();

    /**
     * Detach from this module. The {@link SoundTrigger.StatusListener} callback will not be called
     * anymore and associated resources will be released.
     * */
    public native void detach();

    /**
     * Load a {@link SoundTrigger.SoundModel} to the hardware. A sound model must be loaded in
     * order to start listening to a key phrase in this model.
     * @param model The sound model to load.
     * @param soundModelHandle an array of int where the sound model handle will be returned.
     * @return - {@link SoundTrigger#STATUS_OK} in case of success
     *         - {@link SoundTrigger#STATUS_ERROR} in case of unspecified error
     *         - {@link SoundTrigger#STATUS_PERMISSION_DENIED} if the caller does not have
     *         system permission
     *         - {@link SoundTrigger#STATUS_NO_INIT} if the native service cannot be reached
     *         - {@link SoundTrigger#STATUS_BAD_VALUE} if parameters are invalid
     *         - {@link SoundTrigger#STATUS_DEAD_OBJECT} if the binder transaction to the native
     *         service fails
     *         - {@link SoundTrigger#STATUS_INVALID_OPERATION} if the call is out of sequence
     */
    public native int loadSoundModel(SoundTrigger.SoundModel model, int[] soundModelHandle);

    /**
     * Unload a {@link SoundTrigger.SoundModel} and abort any pendiong recognition
     * @param soundModelHandle The sound model handle
     * @return - {@link SoundTrigger#STATUS_OK} in case of success
     *         - {@link SoundTrigger#STATUS_ERROR} in case of unspecified error
     *         - {@link SoundTrigger#STATUS_PERMISSION_DENIED} if the caller does not have
     *         system permission
     *         - {@link SoundTrigger#STATUS_NO_INIT} if the native service cannot be reached
     *         - {@link SoundTrigger#STATUS_BAD_VALUE} if the sound model handle is invalid
     *         - {@link SoundTrigger#STATUS_DEAD_OBJECT} if the binder transaction to the native
     *         service fails
     */
    public native int unloadSoundModel(int soundModelHandle);

    /**
     * Start listening to all key phrases in a {@link SoundTrigger.SoundModel}.
     * Recognition must be restarted after each callback (success or failure) received on
     * the {@link SoundTrigger.StatusListener}.
     * @param soundModelHandle The sound model handle to start listening to
     * @param data Opaque data for use by the implementation for this recognition
     * @return - {@link SoundTrigger#STATUS_OK} in case of success
     *         - {@link SoundTrigger#STATUS_ERROR} in case of unspecified error
     *         - {@link SoundTrigger#STATUS_PERMISSION_DENIED} if the caller does not have
     *         system permission
     *         - {@link SoundTrigger#STATUS_NO_INIT} if the native service cannot be reached
     *         - {@link SoundTrigger#STATUS_BAD_VALUE} if the sound model handle is invalid
     *         - {@link SoundTrigger#STATUS_DEAD_OBJECT} if the binder transaction to the native
     *         service fails
     *         - {@link SoundTrigger#STATUS_INVALID_OPERATION} if the call is out of sequence
     */
    public native int startRecognition(int soundModelHandle, byte[] data);

    /**
     * Stop listening to all key phrases in a {@link SoundTrigger.SoundModel}
     * @param soundModelHandle The sound model handle to stop listening to
     * @return - {@link SoundTrigger#STATUS_OK} in case of success
     *         - {@link SoundTrigger#STATUS_ERROR} in case of unspecified error
     *         - {@link SoundTrigger#STATUS_PERMISSION_DENIED} if the caller does not have
     *         system permission
     *         - {@link SoundTrigger#STATUS_NO_INIT} if the native service cannot be reached
     *         - {@link SoundTrigger#STATUS_BAD_VALUE} if the sound model handle is invalid
     *         - {@link SoundTrigger#STATUS_DEAD_OBJECT} if the binder transaction to the native
     *         service fails
     *         - {@link SoundTrigger#STATUS_INVALID_OPERATION} if the call is out of sequence
     */
    public native int stopRecognition(int soundModelHandle);

    private class NativeEventHandlerDelegate {
        private final Handler mHandler;

        NativeEventHandlerDelegate(final SoundTrigger.StatusListener listener,
                                   Handler handler) {
            // find the looper for our new event handler
            Looper looper;
            if (handler != null) {
                looper = handler.getLooper();
            } else {
                looper = Looper.myLooper();
                if (looper == null) {
                    looper = Looper.getMainLooper();
                }
            }

            // construct the event handler with this looper
            if (looper != null) {
                // implement the event handler delegate
                mHandler = new Handler(looper) {
                    @Override
                    public void handleMessage(Message msg) {
                        switch(msg.what) {
                        case EVENT_RECOGNITION:
                            if (listener != null) {
                                listener.onRecognition(
                                        (SoundTrigger.RecognitionEvent)msg.obj);
                            }
                            break;
                        case EVENT_SERVICE_DIED:
                            if (listener != null) {
                                listener.onServiceDied();
                            }
                            break;
                        default:
                            break;
                        }
                    }
                };
            } else {
                mHandler = null;
            }
        }

        Handler handler() {
            return mHandler;
        }
    }

    @SuppressWarnings("unused")
    private static void postEventFromNative(Object module_ref,
                                            int what, int arg1, int arg2, Object obj) {
        SoundTriggerModule module = (SoundTriggerModule)((WeakReference)module_ref).get();
        if (module == null) {
            return;
        }

        NativeEventHandlerDelegate delegate = module.mEventHandlerDelegate;
        if (delegate != null) {
            Handler handler = delegate.handler();
            if (handler != null) {
                Message m = handler.obtainMessage(what, arg1, arg2, obj);
                handler.sendMessage(m);
            }
        }
    }
}

