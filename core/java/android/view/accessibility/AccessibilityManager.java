/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.view.accessibility;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.IWindow;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * System level service that serves as an event dispatch for {@link AccessibilityEvent}s,
 * and provides facilities for querying the accessibility state of the system.
 * Accessibility events are generated when something notable happens in the user interface,
 * for example an {@link android.app.Activity} starts, the focus or selection of a
 * {@link android.view.View} changes etc. Parties interested in handling accessibility
 * events implement and register an accessibility service which extends
 * {@link android.accessibilityservice.AccessibilityService}.
 * <p>
 * To obtain a handle to the accessibility manager do the following:
 * </p>
 * <p>
 * <code>
 * <pre>AccessibilityManager accessibilityManager =
 *        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);</pre>
 * </code>
 * </p>
 *
 * @see AccessibilityEvent
 * @see AccessibilityNodeInfo
 * @see android.accessibilityservice.AccessibilityService
 * @see Context#getSystemService
 * @see Context#ACCESSIBILITY_SERVICE
 */
public final class AccessibilityManager {
    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "AccessibilityManager";

    /** @hide */
    public static final int STATE_FLAG_ACCESSIBILITY_ENABLED = 0x00000001;

    /** @hide */
    public static final int STATE_FLAG_TOUCH_EXPLORATION_ENABLED = 0x00000002;

    /** @hide */
    public static final int INVERSION_DISABLED = -1;

    /** @hide */
    public static final int INVERSION_STANDARD = 0;

    /** @hide */
    public static final int INVERSION_HUE_ONLY = 1;

    /** @hide */
    public static final int INVERSION_VALUE_ONLY = 2;

    /** @hide */
    public static final int DALTONIZER_DISABLED = -1;

    /** @hide */
    public static final int DALTONIZER_SIMULATE_MONOCHROMACY = 0;

    /** @hide */
    public static final int DALTONIZER_SIMULATE_PROTANOMALY = 1;

    /** @hide */
    public static final int DALTONIZER_SIMULATE_DEUTERANOMALY = 2;

    /** @hide */
    public static final int DALTONIZER_SIMULATE_TRITANOMALY = 3;

    /** @hide */
    public static final int DALTONIZER_CORRECT_PROTANOMALY = 11;

    /** @hide */
    public static final int DALTONIZER_CORRECT_DEUTERANOMALY = 12;

    /** @hide */
    public static final int DALTONIZER_CORRECT_TRITANOMALY = 13;

    static final Object sInstanceSync = new Object();

    private static AccessibilityManager sInstance;

    private final Object mLock = new Object();

    private IAccessibilityManager mService;

    final int mUserId;

    final Handler mHandler;

    boolean mIsEnabled;

    boolean mIsTouchExplorationEnabled;

    private final CopyOnWriteArrayList<AccessibilityStateChangeListener>
            mAccessibilityStateChangeListeners = new CopyOnWriteArrayList<
                    AccessibilityStateChangeListener>();

    private final CopyOnWriteArrayList<TouchExplorationStateChangeListener>
            mTouchExplorationStateChangeListeners = new CopyOnWriteArrayList<
                    TouchExplorationStateChangeListener>();

    /**
     * Listener for the system accessibility state. To listen for changes to the
     * accessibility state on the device, implement this interface and register
     * it with the system by calling {@link #addAccessibilityStateChangeListener}.
     */
    public interface AccessibilityStateChangeListener {

        /**
         * Called when the accessibility enabled state changes.
         *
         * @param enabled Whether accessibility is enabled.
         */
        public void onAccessibilityStateChanged(boolean enabled);
    }

    /**
     * Listener for the system touch exploration state. To listen for changes to
     * the touch exploration state on the device, implement this interface and
     * register it with the system by calling
     * {@link #addTouchExplorationStateChangeListener}.
     */
    public interface TouchExplorationStateChangeListener {

        /**
         * Called when the touch exploration enabled state changes.
         *
         * @param enabled Whether touch exploration is enabled.
         */
        public void onTouchExplorationStateChanged(boolean enabled);
    }

    private final IAccessibilityManagerClient.Stub mClient =
            new IAccessibilityManagerClient.Stub() {
        public void setState(int state) {
            synchronized (mLock) {
                setStateLocked(state);
            }
        }
    };

    /**
     * Get an AccessibilityManager instance (create one if necessary).
     *
     * @param context Context in which this manager operates.
     *
     * @hide
     */
    public static AccessibilityManager getInstance(Context context) {
        synchronized (sInstanceSync) {
            if (sInstance == null) {
                final int userId;
                if (Binder.getCallingUid() == Process.SYSTEM_UID
                        || context.checkCallingOrSelfPermission(
                                Manifest.permission.INTERACT_ACROSS_USERS)
                                        == PackageManager.PERMISSION_GRANTED
                        || context.checkCallingOrSelfPermission(
                                Manifest.permission.INTERACT_ACROSS_USERS_FULL)
                                        == PackageManager.PERMISSION_GRANTED) {
                    userId = UserHandle.USER_CURRENT;
                } else {
                    userId = UserHandle.myUserId();
                }
                IBinder iBinder = ServiceManager.getService(Context.ACCESSIBILITY_SERVICE);
                IAccessibilityManager service = iBinder == null
                        ? null : IAccessibilityManager.Stub.asInterface(iBinder);
                sInstance = new AccessibilityManager(context, service, userId);
            }
        }
        return sInstance;
    }

    /**
     * Create an instance.
     *
     * @param context A {@link Context}.
     * @param service An interface to the backing service.
     * @param userId User id under which to run.
     *
     * @hide
     */
    public AccessibilityManager(Context context, IAccessibilityManager service, int userId) {
        mHandler = new MyHandler(context.getMainLooper());
        mService = service;
        mUserId = userId;
        synchronized (mLock) {
            tryConnectToServiceLocked();
        }
    }

    /**
     * @hide
     */
    public IAccessibilityManagerClient getClient() {
        return mClient;
    }

    /**
     * Returns if the accessibility in the system is enabled.
     *
     * @return True if accessibility is enabled, false otherwise.
     */
    public boolean isEnabled() {
        synchronized (mLock) {
            IAccessibilityManager service = getServiceLocked();
            if (service == null) {
                return false;
            }
            return mIsEnabled;
        }
    }

    /**
     * Returns if the touch exploration in the system is enabled.
     *
     * @return True if touch exploration is enabled, false otherwise.
     */
    public boolean isTouchExplorationEnabled() {
        synchronized (mLock) {
            IAccessibilityManager service = getServiceLocked();
            if (service == null) {
                return false;
            }
            return mIsTouchExplorationEnabled;
        }
    }

    /**
     * Sends an {@link AccessibilityEvent}.
     *
     * @param event The event to send.
     *
     * @throws IllegalStateException if accessibility is not enabled.
     *
     * <strong>Note:</strong> The preferred mechanism for sending custom accessibility
     * events is through calling
     * {@link android.view.ViewParent#requestSendAccessibilityEvent(View, AccessibilityEvent)}
     * instead of this method to allow predecessors to augment/filter events sent by
     * their descendants.
     */
    public void sendAccessibilityEvent(AccessibilityEvent event) {
        final IAccessibilityManager service;
        final int userId;
        synchronized (mLock) {
            service = getServiceLocked();
            if (service == null) {
                return;
            }
            if (!mIsEnabled) {
                throw new IllegalStateException("Accessibility off. Did you forget to check that?");
            }
            userId = mUserId;
        }
        boolean doRecycle = false;
        try {
            event.setEventTime(SystemClock.uptimeMillis());
            // it is possible that this manager is in the same process as the service but
            // client using it is called through Binder from another process. Example: MMS
            // app adds a SMS notification and the NotificationManagerService calls this method
            long identityToken = Binder.clearCallingIdentity();
            doRecycle = service.sendAccessibilityEvent(event, userId);
            Binder.restoreCallingIdentity(identityToken);
            if (DEBUG) {
                Log.i(LOG_TAG, event + " sent");
            }
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error during sending " + event + " ", re);
        } finally {
            if (doRecycle) {
                event.recycle();
            }
        }
    }

    /**
     * Requests feedback interruption from all accessibility services.
     */
    public void interrupt() {
        final IAccessibilityManager service;
        final int userId;
        synchronized (mLock) {
            service = getServiceLocked();
            if (service == null) {
                return;
            }
            if (!mIsEnabled) {
                throw new IllegalStateException("Accessibility off. Did you forget to check that?");
            }
            userId = mUserId;
        }
        try {
            service.interrupt(userId);
            if (DEBUG) {
                Log.i(LOG_TAG, "Requested interrupt from all services");
            }
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error while requesting interrupt from all services. ", re);
        }
    }

    /**
     * Returns the {@link ServiceInfo}s of the installed accessibility services.
     *
     * @return An unmodifiable list with {@link ServiceInfo}s.
     *
     * @deprecated Use {@link #getInstalledAccessibilityServiceList()}
     */
    @Deprecated
    public List<ServiceInfo> getAccessibilityServiceList() {
        List<AccessibilityServiceInfo> infos = getInstalledAccessibilityServiceList();
        List<ServiceInfo> services = new ArrayList<ServiceInfo>();
        final int infoCount = infos.size();
        for (int i = 0; i < infoCount; i++) {
            AccessibilityServiceInfo info = infos.get(i);
            services.add(info.getResolveInfo().serviceInfo);
        }
        return Collections.unmodifiableList(services);
    }

    /**
     * Returns the {@link AccessibilityServiceInfo}s of the installed accessibility services.
     *
     * @return An unmodifiable list with {@link AccessibilityServiceInfo}s.
     */
    public List<AccessibilityServiceInfo> getInstalledAccessibilityServiceList() {
        final IAccessibilityManager service;
        final int userId;
        synchronized (mLock) {
            service = getServiceLocked();
            if (service == null) {
                return Collections.emptyList();
            }
            userId = mUserId;
        }

        List<AccessibilityServiceInfo> services = null;
        try {
            services = service.getInstalledAccessibilityServiceList(userId);
            if (DEBUG) {
                Log.i(LOG_TAG, "Installed AccessibilityServices " + services);
            }
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error while obtaining the installed AccessibilityServices. ", re);
        }
        if (services != null) {
            return Collections.unmodifiableList(services);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Returns the {@link AccessibilityServiceInfo}s of the enabled accessibility services
     * for a given feedback type.
     *
     * @param feedbackTypeFlags The feedback type flags.
     * @return An unmodifiable list with {@link AccessibilityServiceInfo}s.
     *
     * @see AccessibilityServiceInfo#FEEDBACK_AUDIBLE
     * @see AccessibilityServiceInfo#FEEDBACK_GENERIC
     * @see AccessibilityServiceInfo#FEEDBACK_HAPTIC
     * @see AccessibilityServiceInfo#FEEDBACK_SPOKEN
     * @see AccessibilityServiceInfo#FEEDBACK_VISUAL
     */
    public List<AccessibilityServiceInfo> getEnabledAccessibilityServiceList(
            int feedbackTypeFlags) {
        final IAccessibilityManager service;
        final int userId;
        synchronized (mLock) {
            service = getServiceLocked();
            if (service == null) {
                return Collections.emptyList();
            }
            userId = mUserId;
        }

        List<AccessibilityServiceInfo> services = null;
        try {
            services = service.getEnabledAccessibilityServiceList(feedbackTypeFlags, userId);
            if (DEBUG) {
                Log.i(LOG_TAG, "Installed AccessibilityServices " + services);
            }
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error while obtaining the installed AccessibilityServices. ", re);
        }
        if (services != null) {
            return Collections.unmodifiableList(services);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Registers an {@link AccessibilityStateChangeListener} for changes in
     * the global accessibility state of the system.
     *
     * @param listener The listener.
     * @return True if successfully registered.
     */
    public boolean addAccessibilityStateChangeListener(
            AccessibilityStateChangeListener listener) {
        // Final CopyOnArrayList - no lock needed.
        return mAccessibilityStateChangeListeners.add(listener);
    }

    /**
     * Unregisters an {@link AccessibilityStateChangeListener}.
     *
     * @param listener The listener.
     * @return True if successfully unregistered.
     */
    public boolean removeAccessibilityStateChangeListener(
            AccessibilityStateChangeListener listener) {
        // Final CopyOnArrayList - no lock needed.
        return mAccessibilityStateChangeListeners.remove(listener);
    }

    /**
     * Registers a {@link TouchExplorationStateChangeListener} for changes in
     * the global touch exploration state of the system.
     *
     * @param listener The listener.
     * @return True if successfully registered.
     */
    public boolean addTouchExplorationStateChangeListener(
            TouchExplorationStateChangeListener listener) {
        // Final CopyOnArrayList - no lock needed.
        return mTouchExplorationStateChangeListeners.add(listener);
    }

    /**
     * Unregisters a {@link TouchExplorationStateChangeListener}.
     *
     * @param listener The listener.
     * @return True if successfully unregistered.
     */
    public boolean removeTouchExplorationStateChangeListener(
            TouchExplorationStateChangeListener listener) {
        // Final CopyOnArrayList - no lock needed.
        return mTouchExplorationStateChangeListeners.remove(listener);
    }

    /**
     * Sets the current state and notifies listeners, if necessary.
     *
     * @param stateFlags The state flags.
     */
    private void setStateLocked(int stateFlags) {
        final boolean enabled = (stateFlags & STATE_FLAG_ACCESSIBILITY_ENABLED) != 0;
        final boolean touchExplorationEnabled =
                (stateFlags & STATE_FLAG_TOUCH_EXPLORATION_ENABLED) != 0;

        final boolean wasEnabled = mIsEnabled;
        final boolean wasTouchExplorationEnabled = mIsTouchExplorationEnabled;

        // Ensure listeners get current state from isZzzEnabled() calls.
        mIsEnabled = enabled;
        mIsTouchExplorationEnabled = touchExplorationEnabled;

        if (wasEnabled != enabled) {
            mHandler.sendEmptyMessage(MyHandler.MSG_NOTIFY_ACCESSIBILITY_STATE_CHANGED);
        }

        if (wasTouchExplorationEnabled != touchExplorationEnabled) {
            mHandler.sendEmptyMessage(MyHandler.MSG_NOTIFY_EXPLORATION_STATE_CHANGED);
        }
    }

    /**
     * Adds an accessibility interaction connection interface for a given window.
     * @param windowToken The window token to which a connection is added.
     * @param connection The connection.
     *
     * @hide
     */
    public int addAccessibilityInteractionConnection(IWindow windowToken,
            IAccessibilityInteractionConnection connection) {
        final IAccessibilityManager service;
        final int userId;
        synchronized (mLock) {
            service = getServiceLocked();
            if (service == null) {
                return View.NO_ID;
            }
            userId = mUserId;
        }
        try {
            return service.addAccessibilityInteractionConnection(windowToken, connection, userId);
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error while adding an accessibility interaction connection. ", re);
        }
        return View.NO_ID;
    }

    /**
     * Removed an accessibility interaction connection interface for a given window.
     * @param windowToken The window token to which a connection is removed.
     *
     * @hide
     */
    public void removeAccessibilityInteractionConnection(IWindow windowToken) {
        final IAccessibilityManager service;
        synchronized (mLock) {
            service = getServiceLocked();
            if (service == null) {
                return;
            }
        }
        try {
            service.removeAccessibilityInteractionConnection(windowToken);
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error while removing an accessibility interaction connection. ", re);
        }
    }

    private  IAccessibilityManager getServiceLocked() {
        if (mService == null) {
            tryConnectToServiceLocked();
        }
        return mService;
    }

    private void tryConnectToServiceLocked() {
        IBinder iBinder = ServiceManager.getService(Context.ACCESSIBILITY_SERVICE);
        if (iBinder == null) {
            return;
        }
        IAccessibilityManager service = IAccessibilityManager.Stub.asInterface(iBinder);
        try {
            final int stateFlags = service.addClient(mClient, mUserId);
            setStateLocked(stateFlags);
            mService = service;
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "AccessibilityManagerService is dead", re);
        }
    }

    /**
     * Notifies the registered {@link AccessibilityStateChangeListener}s.
     */
    private void handleNotifyAccessibilityStateChanged() {
        final boolean isEnabled;
        synchronized (mLock) {
            isEnabled = mIsEnabled;
        }
        final int listenerCount = mAccessibilityStateChangeListeners.size();
        for (int i = 0; i < listenerCount; i++) {
            mAccessibilityStateChangeListeners.get(i).onAccessibilityStateChanged(isEnabled);
        }
    }

    /**
     * Notifies the registered {@link TouchExplorationStateChangeListener}s.
     */
    private void handleNotifyTouchExplorationStateChanged() {
        final boolean isTouchExplorationEnabled;
        synchronized (mLock) {
            isTouchExplorationEnabled = mIsTouchExplorationEnabled;
        }
        final int listenerCount = mTouchExplorationStateChangeListeners.size();
        for (int i = 0; i < listenerCount; i++) {
            mTouchExplorationStateChangeListeners.get(i)
                    .onTouchExplorationStateChanged(isTouchExplorationEnabled);
        }
    }

    private final class MyHandler extends Handler {
        public static final int MSG_NOTIFY_ACCESSIBILITY_STATE_CHANGED = 1;
        public static final int MSG_NOTIFY_EXPLORATION_STATE_CHANGED = 2;

        public MyHandler(Looper looper) {
            super(looper, null, false);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_NOTIFY_ACCESSIBILITY_STATE_CHANGED: {
                    handleNotifyAccessibilityStateChanged();
                } break;

                case MSG_NOTIFY_EXPLORATION_STATE_CHANGED: {
                    handleNotifyTouchExplorationStateChanged();
                }
            }
        }
    }
}
