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

package android.hardware.camera2.legacy;

import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.utils.CameraBinderDecorator;
import android.os.Handler;
import android.util.Log;

/**
 * Emulates a the state of a single Camera2 device.
 *
 * <p>
 * This class acts as the state machine for a camera device.  Valid state transitions are given
 * in the table below:
 * </p>
 *
 * <ul>
 *      <li>{@code UNCONFIGURED -> CONFIGURING}</li>
 *      <li>{@code CONFIGURING -> IDLE}</li>
 *      <li>{@code IDLE -> CONFIGURING}</li>
 *      <li>{@code IDLE -> CAPTURING}</li>
 *      <li>{@code IDLE -> IDLE}</li>
 *      <li>{@code CAPTURING -> IDLE}</li>
 *      <li>{@code ANY -> ERROR}</li>
 * </ul>
 */
public class CameraDeviceState {
    private static final String TAG = "CameraDeviceState";
    private static final boolean DEBUG = Log.isLoggable(LegacyCameraDevice.DEBUG_PROP, Log.DEBUG);

    private static final int STATE_ERROR = 0;
    private static final int STATE_UNCONFIGURED = 1;
    private static final int STATE_CONFIGURING = 2;
    private static final int STATE_IDLE = 3;
    private static final int STATE_CAPTURING = 4;

    private int mCurrentState = STATE_UNCONFIGURED;
    private int mCurrentError = CameraBinderDecorator.NO_ERROR;

    private RequestHolder mCurrentRequest = null;

    private Handler mCurrentHandler = null;
    private CameraDeviceStateListener mCurrentListener = null;


    /**
     * CameraDeviceStateListener callbacks to be called after state transitions.
     */
    public interface CameraDeviceStateListener {
        void onError(int errorCode, RequestHolder holder);
        void onConfiguring();
        void onIdle();
        void onCaptureStarted(RequestHolder holder);
        void onCaptureResult(CameraMetadataNative result, RequestHolder holder);
    }

    /**
     * Transition to the {@code ERROR} state.
     *
     * <p>
     * The device cannot exit the {@code ERROR} state.  If the device was not already in the
     * {@code ERROR} state, {@link CameraDeviceStateListener#onError(int, RequestHolder)} will be
     * called.
     * </p>
     *
     * @param error the error to set.  Should be one of the error codes defined in
     *      {@link android.hardware.camera2.utils.CameraBinderDecorator}.
     */
    public synchronized void setError(int error) {
        mCurrentError = error;
        doStateTransition(STATE_ERROR);
    }

    /**
     * Transition to the {@code CONFIGURING} state, or {@code ERROR} if in an invalid state.
     *
     * <p>
     * If the device was not already in the {@code CONFIGURING} state,
     * {@link CameraDeviceStateListener#onConfiguring()} will be called.
     * </p>
     *
     * @return {@link CameraBinderDecorator#NO_ERROR}, or an error if one has occurred.
     */
    public synchronized int setConfiguring() {
        doStateTransition(STATE_CONFIGURING);
        return mCurrentError;
    }

    /**
     * Transition to the {@code IDLE} state, or {@code ERROR} if in an invalid state.
     *
     * <p>
     * If the device was not already in the {@code IDLE} state,
     * {@link CameraDeviceStateListener#onIdle()} will be called.
     * </p>
     *
     * @return {@link CameraBinderDecorator#NO_ERROR}, or an error if one has occurred.
     */
    public synchronized int setIdle() {
        doStateTransition(STATE_IDLE);
        return mCurrentError;
    }

    /**
     * Transition to the {@code CAPTURING} state, or {@code ERROR} if in an invalid state.
     *
     * <p>
     * If the device was not already in the {@code CAPTURING} state,
     * {@link CameraDeviceStateListener#onCaptureStarted(RequestHolder)} will be called.
     * </p>
     *
     * @param request A {@link RequestHolder} containing the request for the current capture.
     * @return {@link CameraBinderDecorator#NO_ERROR}, or an error if one has occurred.
     */
    public synchronized int setCaptureStart(final RequestHolder request) {
        mCurrentRequest = request;
        doStateTransition(STATE_CAPTURING);
        return mCurrentError;
    }

    /**
     * Set the result for a capture.
     *
     * <p>
     * If the device was in the {@code CAPTURING} state,
     * {@link CameraDeviceStateListener#onCaptureResult(CameraMetadataNative, RequestHolder)} will
     * be called with the given result, otherwise this will result in the device transitioning to
     * the {@code ERROR} state,
     * </p>
     *
     * @param request the {@link RequestHolder} request that created this result.
     * @param result the {@link CameraMetadataNative} result to set.
     * @return {@link CameraBinderDecorator#NO_ERROR}, or an error if one has occurred.
     */
    public synchronized int setCaptureResult(final RequestHolder request,
                                             final CameraMetadataNative result) {
        if (mCurrentState != STATE_CAPTURING) {
            Log.e(TAG, "Cannot receive result while in state: " + mCurrentState);
            mCurrentError = CameraBinderDecorator.INVALID_OPERATION;
            doStateTransition(STATE_ERROR);
            return mCurrentError;
        }

        if (mCurrentHandler != null && mCurrentListener != null) {
            mCurrentHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCurrentListener.onCaptureResult(result, request);
                }
            });
        }
        return mCurrentError;
    }

    /**
     * Set the listener for state transition callbacks.
     *
     * @param handler handler on which to call the callbacks.
     * @param listener the {@link CameraDeviceStateListener} callbacks to call.
     */
    public synchronized void setCameraDeviceCallbacks(Handler handler,
                                                      CameraDeviceStateListener listener) {
        mCurrentHandler = handler;
        mCurrentListener = listener;
    }

    private void doStateTransition(int newState) {
        if (DEBUG) {
            if (newState != mCurrentState) {
                Log.d(TAG, "Transitioning to state " + newState);
            }
        }
        switch(newState) {
            case STATE_ERROR:
                if (mCurrentState != STATE_ERROR && mCurrentHandler != null &&
                        mCurrentListener != null) {
                    mCurrentHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mCurrentListener.onError(mCurrentError, mCurrentRequest);
                        }
                    });
                }
                mCurrentState = STATE_ERROR;
                break;
            case STATE_CONFIGURING:
                if (mCurrentState != STATE_UNCONFIGURED && mCurrentState != STATE_IDLE) {
                    Log.e(TAG, "Cannot call configure while in state: " + mCurrentState);
                    mCurrentError = CameraBinderDecorator.INVALID_OPERATION;
                    doStateTransition(STATE_ERROR);
                    break;
                }
                if (mCurrentState != STATE_CONFIGURING && mCurrentHandler != null &&
                        mCurrentListener != null) {
                    mCurrentHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mCurrentListener.onConfiguring();
                        }
                    });
                }
                mCurrentState = STATE_CONFIGURING;
                break;
            case STATE_IDLE:
                if (mCurrentState == STATE_IDLE) {
                    break;
                }

                if (mCurrentState != STATE_CONFIGURING && mCurrentState != STATE_CAPTURING) {
                    Log.e(TAG, "Cannot call idle while in state: " + mCurrentState);
                    mCurrentError = CameraBinderDecorator.INVALID_OPERATION;
                    doStateTransition(STATE_ERROR);
                    break;
                }

                if (mCurrentState != STATE_IDLE && mCurrentHandler != null &&
                        mCurrentListener != null) {
                    mCurrentHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mCurrentListener.onIdle();
                        }
                    });
                }
                mCurrentState = STATE_IDLE;
                break;
            case STATE_CAPTURING:
                if (mCurrentState != STATE_IDLE && mCurrentState != STATE_CAPTURING) {
                    Log.e(TAG, "Cannot call capture while in state: " + mCurrentState);
                    mCurrentError = CameraBinderDecorator.INVALID_OPERATION;
                    doStateTransition(STATE_ERROR);
                    break;
                }
                if (mCurrentHandler != null && mCurrentListener != null) {
                    mCurrentHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mCurrentListener.onCaptureStarted(mCurrentRequest);
                        }
                    });
                }
                mCurrentState = STATE_CAPTURING;
                break;
            default:
                throw new IllegalStateException("Transition to unknown state: " + newState);
        }
    }


}
