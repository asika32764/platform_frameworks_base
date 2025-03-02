/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.hardware.camera2;

import android.content.Context;
import android.hardware.ICameraService;
import android.hardware.ICameraServiceListener;
import android.hardware.CameraInfo;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.legacy.CameraDeviceUserShim;
import android.hardware.camera2.legacy.LegacyMetadataMapper;
import android.hardware.camera2.utils.CameraBinderDecorator;
import android.hardware.camera2.utils.CameraRuntimeException;
import android.hardware.camera2.utils.BinderHolder;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.ArrayMap;

import java.util.ArrayList;

/**
 * <p>A system service manager for detecting, characterizing, and connecting to
 * {@link CameraDevice CameraDevices}.</p>
 *
 * <p>You can get an instance of this class by calling
 * {@link android.content.Context#getSystemService(String) Context.getSystemService()}.</p>
 *
 * <pre>CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);</pre>
 *
 * <p>For more details about communicating with camera devices, read the Camera
 * developer guide or the {@link android.hardware.camera2 camera2}
 * package documentation.</p>
 */
public final class CameraManager {

    private static final String TAG = "CameraManager";

    /**
     * This should match the ICameraService definition
     */
    private static final String CAMERA_SERVICE_BINDER_NAME = "media.camera";
    private static final int USE_CALLING_UID = -1;

    @SuppressWarnings("unused")
    private static final int API_VERSION_1 = 1;
    private static final int API_VERSION_2 = 2;

    private final ICameraService mCameraService;
    private ArrayList<String> mDeviceIdList;

    private final ArrayMap<AvailabilityListener, Handler> mListenerMap =
            new ArrayMap<AvailabilityListener, Handler>();

    private final Context mContext;
    private final Object mLock = new Object();

    /**
     * @hide
     */
    public CameraManager(Context context) {
        mContext = context;

        IBinder cameraServiceBinder = ServiceManager.getService(CAMERA_SERVICE_BINDER_NAME);
        ICameraService cameraServiceRaw = ICameraService.Stub.asInterface(cameraServiceBinder);

        /**
         * Wrap the camera service in a decorator which automatically translates return codes
         * into exceptions, and RemoteExceptions into other exceptions.
         */
        mCameraService = CameraBinderDecorator.newInstance(cameraServiceRaw);

        try {
            CameraBinderDecorator.throwOnError(
                    CameraMetadataNative.nativeSetupGlobalVendorTagDescriptor());
        } catch (CameraRuntimeException e) {
            handleRecoverableSetupErrors(e, "Failed to set up vendor tags");
        }

        try {
            mCameraService.addListener(new CameraServiceListener());
        } catch(CameraRuntimeException e) {
            throw new IllegalStateException("Failed to register a camera service listener",
                    e.asChecked());
        } catch (RemoteException e) {
            // impossible
        }
    }

    /**
     * Return the list of currently connected camera devices by
     * identifier.
     *
     * <p>Non-removable cameras use integers starting at 0 for their
     * identifiers, while removable cameras have a unique identifier for each
     * individual device, even if they are the same model.</p>
     *
     * @return The list of currently connected camera devices.
     */
    public String[] getCameraIdList() throws CameraAccessException {
        synchronized (mLock) {
            try {
                return getOrCreateDeviceIdListLocked().toArray(new String[0]);
            } catch(CameraAccessException e) {
                // this should almost never happen, except if mediaserver crashes
                throw new IllegalStateException(
                        "Failed to query camera service for device ID list", e);
            }
        }
    }

    /**
     * Register a listener to be notified about camera device availability.
     *
     * <p>Registering the same listener again will replace the handler with the
     * new one provided.</p>
     *
     * @param listener The new listener to send camera availability notices to
     * @param handler The handler on which the listener should be invoked, or
     * {@code null} to use the current thread's {@link android.os.Looper looper}.
     */
    public void addAvailabilityListener(AvailabilityListener listener, Handler handler) {
        if (handler == null) {
            Looper looper = Looper.myLooper();
            if (looper == null) {
                throw new IllegalArgumentException(
                        "No handler given, and current thread has no looper!");
            }
            handler = new Handler(looper);
        }

        synchronized (mLock) {
            mListenerMap.put(listener, handler);

            // TODO: fire the current oldest known state when adding a new listener
            //    (must be done while holding lock)
        }
    }

    /**
     * Remove a previously-added listener; the listener will no longer receive
     * connection and disconnection callbacks.
     *
     * <p>Removing a listener that isn't registered has no effect.</p>
     *
     * @param listener The listener to remove from the notification list
     */
    public void removeAvailabilityListener(AvailabilityListener listener) {
        synchronized (mLock) {
            mListenerMap.remove(listener);
        }
    }

    /**
     * <p>Query the capabilities of a camera device. These capabilities are
     * immutable for a given camera.</p>
     *
     * @param cameraId The id of the camera device to query
     * @return The properties of the given camera
     *
     * @throws IllegalArgumentException if the cameraId does not match any
     * currently connected camera device.
     * @throws CameraAccessException if the camera is disabled by device policy.
     * @throws SecurityException if the application does not have permission to
     * access the camera
     *
     * @see #getCameraIdList
     * @see android.app.admin.DevicePolicyManager#setCameraDisabled
     */
    public CameraCharacteristics getCameraCharacteristics(String cameraId)
            throws CameraAccessException {

        synchronized (mLock) {
            if (!getOrCreateDeviceIdListLocked().contains(cameraId)) {
                throw new IllegalArgumentException(String.format("Camera id %s does not match any" +
                        " currently connected camera device", cameraId));
            }
        }

        int id = Integer.valueOf(cameraId);

        /*
         * Get the camera characteristics from the camera service directly if it supports it,
         * otherwise get them from the legacy shim instead.
         */

        if (!supportsCamera2Api(cameraId)) {
            // Legacy backwards compatibility path; build static info from the camera parameters
            String[] outParameters = new String[1];
            try {
                mCameraService.getLegacyParameters(id, /*out*/outParameters);
                String parameters = outParameters[0];

                CameraInfo info = new CameraInfo();
                mCameraService.getCameraInfo(id, /*out*/info);

                return LegacyMetadataMapper.createCharacteristics(parameters, info);
            } catch (RemoteException e) {
                // Impossible
                return null;
            } catch (CameraRuntimeException e) {
                throw e.asChecked();
            }

        } else {
            // Normal path: Get the camera characteristics directly from the camera service
            CameraMetadataNative info = new CameraMetadataNative();

            try {
                mCameraService.getCameraCharacteristics(id, info);
            } catch(CameraRuntimeException e) {
                throw e.asChecked();
            } catch(RemoteException e) {
                // impossible
                return null;
            }

            return new CameraCharacteristics(info);
        }
    }

    /**
     * Helper for openning a connection to a camera with the given ID.
     *
     * @param cameraId The unique identifier of the camera device to open
     * @param listener The listener for the camera. Must not be null.
     * @param handler  The handler to call the listener on. Must not be null.
     *
     * @throws CameraAccessException if the camera is disabled by device policy,
     * or too many camera devices are already open, or the cameraId does not match
     * any currently available camera device.
     *
     * @throws SecurityException if the application does not have permission to
     * access the camera
     * @throws IllegalArgumentException if listener or handler is null.
     * @return A handle to the newly-created camera device.
     *
     * @see #getCameraIdList
     * @see android.app.admin.DevicePolicyManager#setCameraDisabled
     */
    private CameraDevice openCameraDeviceUserAsync(String cameraId,
            CameraDevice.StateListener listener, Handler handler)
            throws CameraAccessException {
        CameraCharacteristics characteristics = getCameraCharacteristics(cameraId);
        CameraDevice device = null;
        try {

            synchronized (mLock) {

                ICameraDeviceUser cameraUser = null;

                android.hardware.camera2.impl.CameraDeviceImpl deviceImpl =
                        new android.hardware.camera2.impl.CameraDeviceImpl(
                                cameraId,
                                listener,
                                handler,
                                characteristics);

                BinderHolder holder = new BinderHolder();

                ICameraDeviceCallbacks callbacks = deviceImpl.getCallbacks();
                int id = Integer.parseInt(cameraId);
                try {
                    mCameraService.connectDevice(callbacks, id, mContext.getPackageName(),
                            USE_CALLING_UID, holder);
                    cameraUser = ICameraDeviceUser.Stub.asInterface(holder.getBinder());
                } catch (CameraRuntimeException e) {
                    if (e.getReason() == CameraAccessException.CAMERA_DEPRECATED_HAL) {
                        // Use legacy camera implementation for HAL1 devices
                        Log.i(TAG, "Using legacy camera HAL.");
                        cameraUser = CameraDeviceUserShim.connectBinderShim(callbacks, id);
                    } else if (e.getReason() == CameraAccessException.CAMERA_IN_USE ||
                            e.getReason() == CameraAccessException.MAX_CAMERAS_IN_USE ||
                            e.getReason() == CameraAccessException.CAMERA_DISABLED ||
                            e.getReason() == CameraAccessException.CAMERA_DISCONNECTED ||
                            e.getReason() == CameraAccessException.CAMERA_ERROR) {
                        // Received one of the known connection errors
                        // The remote camera device cannot be connected to, so
                        // set the local camera to the startup error state
                        deviceImpl.setRemoteFailure(e);

                        if (e.getReason() == CameraAccessException.CAMERA_DISABLED ||
                                e.getReason() == CameraAccessException.CAMERA_DISCONNECTED) {
                            // Per API docs, these failures call onError and throw
                            throw e;
                        }
                    } else {
                        // Unexpected failure - rethrow
                        throw e;
                    }
                }

                // TODO: factor out listener to be non-nested, then move setter to constructor
                // For now, calling setRemoteDevice will fire initial
                // onOpened/onUnconfigured callbacks.
                deviceImpl.setRemoteDevice(cameraUser);
                device = deviceImpl;
            }

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected cameraId to be numeric, but it was: "
                    + cameraId);
        } catch (CameraRuntimeException e) {
            throw e.asChecked();
        } catch (RemoteException e) {
            // impossible
        }
        return device;
    }

    /**
     * Open a connection to a camera with the given ID.
     *
     * <p>Use {@link #getCameraIdList} to get the list of available camera
     * devices. Note that even if an id is listed, open may fail if the device
     * is disconnected between the calls to {@link #getCameraIdList} and
     * {@link #openCamera}.</p>
     *
     * <p>Once the camera is successfully opened, {@link CameraDevice.StateListener#onOpened} will
     * be invoked with the newly opened {@link CameraDevice}. The camera device can then be set up
     * for operation by calling {@link CameraDevice#createCaptureSession} and
     * {@link CameraDevice#createCaptureRequest}</p>
     *
     * <!--
     * <p>Since the camera device will be opened asynchronously, any asynchronous operations done
     * on the returned CameraDevice instance will be queued up until the device startup has
     * completed and the listener's {@link CameraDevice.StateListener#onOpened onOpened} method is
     * called. The pending operations are then processed in order.</p>
     * -->
     * <p>If the camera becomes disconnected during initialization
     * after this function call returns,
     * {@link CameraDevice.StateListener#onDisconnected} with a
     * {@link CameraDevice} in the disconnected state (and
     * {@link CameraDevice.StateListener#onOpened} will be skipped).</p>
     *
     * <p>If opening the camera device fails, then the device listener's
     * {@link CameraDevice.StateListener#onError onError} method will be called, and subsequent
     * calls on the camera device will throw a {@link CameraAccessException}.</p>
     *
     * @param cameraId
     *             The unique identifier of the camera device to open
     * @param listener
     *             The listener which is invoked once the camera is opened
     * @param handler
     *             The handler on which the listener should be invoked, or
     *             {@code null} to use the current thread's {@link android.os.Looper looper}.
     *
     * @throws CameraAccessException if the camera is disabled by device policy,
     * or the camera has become or was disconnected.
     *
     * @throws IllegalArgumentException if cameraId or the listener was null,
     * or the cameraId does not match any currently or previously available
     * camera device.
     *
     * @throws SecurityException if the application does not have permission to
     * access the camera
     *
     * @see #getCameraIdList
     * @see android.app.admin.DevicePolicyManager#setCameraDisabled
     */
    public void openCamera(String cameraId, final CameraDevice.StateListener listener,
            Handler handler)
            throws CameraAccessException {

        if (cameraId == null) {
            throw new IllegalArgumentException("cameraId was null");
        } else if (listener == null) {
            throw new IllegalArgumentException("listener was null");
        } else if (handler == null) {
            if (Looper.myLooper() != null) {
                handler = new Handler();
            } else {
                throw new IllegalArgumentException(
                        "Looper doesn't exist in the calling thread");
            }
        }

        openCameraDeviceUserAsync(cameraId, listener, handler);
    }

    /**
     * A listener for camera devices becoming available or
     * unavailable to open.
     *
     * <p>Cameras become available when they are no longer in use, or when a new
     * removable camera is connected. They become unavailable when some
     * application or service starts using a camera, or when a removable camera
     * is disconnected.</p>
     *
     * <p>Extend this listener and pass an instance of the subclass to
     * {@link CameraManager#addAvailabilityListener} to be notified of such availability
     * changes.</p>
     *
     * @see addAvailabilityListener
     */
    public static abstract class AvailabilityListener {

        /**
         * A new camera has become available to use.
         *
         * <p>The default implementation of this method does nothing.</p>
         *
         * @param cameraId The unique identifier of the new camera.
         */
        public void onCameraAvailable(String cameraId) {
            // default empty implementation
        }

        /**
         * A previously-available camera has become unavailable for use.
         *
         * <p>If an application had an active CameraDevice instance for the
         * now-disconnected camera, that application will receive a
         * {@link CameraDevice.StateListener#onDisconnected disconnection error}.</p>
         *
         * <p>The default implementation of this method does nothing.</p>
         *
         * @param cameraId The unique identifier of the disconnected camera.
         */
        public void onCameraUnavailable(String cameraId) {
            // default empty implementation
        }
    }

    private ArrayList<String> getOrCreateDeviceIdListLocked() throws CameraAccessException {
        if (mDeviceIdList == null) {
            int numCameras = 0;

            try {
                numCameras = mCameraService.getNumberOfCameras();
            } catch(CameraRuntimeException e) {
                throw e.asChecked();
            } catch (RemoteException e) {
                // impossible
                return null;
            }

            mDeviceIdList = new ArrayList<String>();
            CameraMetadataNative info = new CameraMetadataNative();
            for (int i = 0; i < numCameras; ++i) {
                // Non-removable cameras use integers starting at 0 for their
                // identifiers
                boolean isDeviceSupported = false;
                try {
                    mCameraService.getCameraCharacteristics(i, info);
                    if (!info.isEmpty()) {
                        isDeviceSupported = true;
                    } else {
                        throw new AssertionError("Expected to get non-empty characteristics");
                    }
                } catch(IllegalArgumentException  e) {
                    // Got a BAD_VALUE from service, meaning that this
                    // device is not supported.
                } catch(CameraRuntimeException e) {
                    throw e.asChecked();
                } catch(RemoteException e) {
                    // impossible
                }

                if (isDeviceSupported) {
                    mDeviceIdList.add(String.valueOf(i));
                }
            }

        }
        return mDeviceIdList;
    }

    private void handleRecoverableSetupErrors(CameraRuntimeException e, String msg) {
        int problem = e.getReason();
        switch (problem) {
            case CameraAccessException.CAMERA_DISCONNECTED:
                String errorMsg = CameraAccessException.getDefaultMessage(problem);
                Log.w(TAG, msg + ": " + errorMsg);
                break;
            default:
                throw new IllegalStateException(msg, e.asChecked());
        }
    }

    /**
     * Queries the camera service if it supports the camera2 api directly, or needs a shim.
     *
     * @param cameraId a non-{@code null} camera identifier
     * @return {@code false} if the legacy shim needs to be used, {@code true} otherwise.
     */
    private boolean supportsCamera2Api(String cameraId) {
        return supportsCameraApi(cameraId, API_VERSION_2);
    }

    /**
     * Queries the camera service if it supports a camera api directly, or needs a shim.
     *
     * @param cameraId a non-{@code null} camera identifier
     * @param apiVersion the version, i.e. {@code API_VERSION_1} or {@code API_VERSION_2}
     * @return {@code true} if connecting will work for that device version.
     */
    private boolean supportsCameraApi(String cameraId, int apiVersion) {
        int id = Integer.parseInt(cameraId);

        /*
         * Possible return values:
         * - NO_ERROR => Camera2 API is supported
         * - CAMERA_DEPRECATED_HAL => Camera2 API is *not* supported (thrown as an exception)
         *
         * Anything else is an unexpected error we don't want to recover from.
         */

        try {
            int res = mCameraService.supportsCameraApi(id, apiVersion);

            if (res != CameraBinderDecorator.NO_ERROR) {
                throw new AssertionError("Unexpected value " + res);
            }

            return true;
        } catch (CameraRuntimeException e) {
            if (e.getReason() == CameraAccessException.CAMERA_DEPRECATED_HAL) {
                return false;
            } else {
                throw e;
            }
        } catch (RemoteException e) {
            throw new AssertionError("Camera service unreachable", e);
        }
    }

    // TODO: this class needs unit tests
    // TODO: extract class into top level
    private class CameraServiceListener extends ICameraServiceListener.Stub {

        // Keep up-to-date with ICameraServiceListener.h

        // Device physically unplugged
        public static final int STATUS_NOT_PRESENT = 0;
        // Device physically has been plugged in
        // and the camera can be used exclusively
        public static final int STATUS_PRESENT = 1;
        // Device physically has been plugged in
        // but it will not be connect-able until enumeration is complete
        public static final int STATUS_ENUMERATING = 2;
        // Camera is in use by another app and cannot be used exclusively
        public static final int STATUS_NOT_AVAILABLE = 0x80000000;

        // Camera ID -> Status map
        private final ArrayMap<String, Integer> mDeviceStatus = new ArrayMap<String, Integer>();

        private static final String TAG = "CameraServiceListener";

        @Override
        public IBinder asBinder() {
            return this;
        }

        private boolean isAvailable(int status) {
            switch (status) {
                case STATUS_PRESENT:
                    return true;
                default:
                    return false;
            }
        }

        private boolean validStatus(int status) {
            switch (status) {
                case STATUS_NOT_PRESENT:
                case STATUS_PRESENT:
                case STATUS_ENUMERATING:
                case STATUS_NOT_AVAILABLE:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onStatusChanged(int status, int cameraId) throws RemoteException {
            synchronized(CameraManager.this.mLock) {

                Log.v(TAG,
                        String.format("Camera id %d has status changed to 0x%x", cameraId, status));

                final String id = String.valueOf(cameraId);

                if (!validStatus(status)) {
                    Log.e(TAG, String.format("Ignoring invalid device %d status 0x%x", cameraId,
                            status));
                    return;
                }

                Integer oldStatus = mDeviceStatus.put(id, status);

                if (oldStatus != null && oldStatus == status) {
                    Log.v(TAG, String.format(
                            "Device status changed to 0x%x, which is what it already was",
                            status));
                    return;
                }

                // TODO: consider abstracting out this state minimization + transition
                // into a separate
                // more easily testable class
                // i.e. (new State()).addState(STATE_AVAILABLE)
                //                   .addState(STATE_NOT_AVAILABLE)
                //                   .addTransition(STATUS_PRESENT, STATE_AVAILABLE),
                //                   .addTransition(STATUS_NOT_PRESENT, STATE_NOT_AVAILABLE)
                //                   .addTransition(STATUS_ENUMERATING, STATE_NOT_AVAILABLE);
                //                   .addTransition(STATUS_NOT_AVAILABLE, STATE_NOT_AVAILABLE);

                // Translate all the statuses to either 'available' or 'not available'
                //  available -> available         => no new update
                //  not available -> not available => no new update
                if (oldStatus != null && isAvailable(status) == isAvailable(oldStatus)) {

                    Log.v(TAG,
                            String.format(
                                    "Device status was previously available (%d), " +
                                            " and is now again available (%d)" +
                                            "so no new client visible update will be sent",
                                    isAvailable(status), isAvailable(status)));
                    return;
                }

                final int listenerCount = mListenerMap.size();
                for (int i = 0; i < listenerCount; i++) {
                    Handler handler = mListenerMap.valueAt(i);
                    final AvailabilityListener listener = mListenerMap.keyAt(i);
                    if (isAvailable(status)) {
                        handler.post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    listener.onCameraAvailable(id);
                                }
                            });
                    } else {
                        handler.post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    listener.onCameraUnavailable(id);
                                }
                            });
                    }
                } // for
            } // synchronized
        } // onStatusChanged
    } // CameraServiceListener
} // CameraManager
