/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server.display;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayEventReceiver;
import android.view.Surface;
import android.view.SurfaceControl;

import java.io.PrintWriter;

/**
 * A display adapter for the local displays managed by Surface Flinger.
 * <p>
 * Display adapters are guarded by the {@link DisplayManagerService.SyncRoot} lock.
 * </p>
 */
final class LocalDisplayAdapter extends DisplayAdapter {
    private static final String TAG = "LocalDisplayAdapter";

    private static final int[] BUILT_IN_DISPLAY_IDS_TO_SCAN = new int[] {
            SurfaceControl.BUILT_IN_DISPLAY_ID_MAIN,
            SurfaceControl.BUILT_IN_DISPLAY_ID_HDMI,
    };

    private final SparseArray<LocalDisplayDevice> mDevices =
            new SparseArray<LocalDisplayDevice>();
    private HotplugDisplayEventReceiver mHotplugReceiver;

    // Called with SyncRoot lock held.
    public LocalDisplayAdapter(DisplayManagerService.SyncRoot syncRoot,
            Context context, Handler handler, Listener listener) {
        super(syncRoot, context, handler, listener, TAG);
    }

    @Override
    public void registerLocked() {
        super.registerLocked();

        mHotplugReceiver = new HotplugDisplayEventReceiver(getHandler().getLooper());

        for (int builtInDisplayId : BUILT_IN_DISPLAY_IDS_TO_SCAN) {
            tryConnectDisplayLocked(builtInDisplayId);
        }
    }

    private void tryConnectDisplayLocked(int builtInDisplayId) {
        IBinder displayToken = SurfaceControl.getBuiltInDisplay(builtInDisplayId);
        if (displayToken != null) {
            SurfaceControl.PhysicalDisplayInfo[] configs =
                    SurfaceControl.getDisplayConfigs(displayToken);
            if (configs == null) {
                // There are no valid configs for this device, so we can't use it
                Slog.w(TAG, "No valid configs found for display device " +
                        builtInDisplayId);
                return;
            }
            int activeConfig = SurfaceControl.getActiveConfig(displayToken);
            if (activeConfig < 0) {
                // There is no active config, and for now we don't have the
                // policy to set one.
                Slog.w(TAG, "No active config found for display device " +
                        builtInDisplayId);
                return;
            }
            LocalDisplayDevice device = mDevices.get(builtInDisplayId);
            if (device == null) {
                // Display was added.
                device = new LocalDisplayDevice(displayToken, builtInDisplayId,
                        configs[activeConfig]);
                mDevices.put(builtInDisplayId, device);
                sendDisplayDeviceEventLocked(device, DISPLAY_DEVICE_EVENT_ADDED);
            } else if (device.updatePhysicalDisplayInfoLocked(configs[activeConfig])) {
                // Display properties changed.
                sendDisplayDeviceEventLocked(device, DISPLAY_DEVICE_EVENT_CHANGED);
            }
        } else {
            // The display is no longer available. Ignore the attempt to add it.
            // If it was connected but has already been disconnected, we'll get a
            // disconnect event that will remove it from mDevices.
        }
    }

    private void tryDisconnectDisplayLocked(int builtInDisplayId) {
        LocalDisplayDevice device = mDevices.get(builtInDisplayId);
        if (device != null) {
            // Display was removed.
            mDevices.remove(builtInDisplayId);
            sendDisplayDeviceEventLocked(device, DISPLAY_DEVICE_EVENT_REMOVED);
        }
    }

    static int getPowerModeForState(int state) {
        switch (state) {
            case Display.STATE_OFF:
                return SurfaceControl.POWER_MODE_OFF;
            case Display.STATE_DOZING:
                return SurfaceControl.POWER_MODE_DOZE;
            default:
                return SurfaceControl.POWER_MODE_NORMAL;
        }
    }

    private final class LocalDisplayDevice extends DisplayDevice {
        private final int mBuiltInDisplayId;
        private final SurfaceControl.PhysicalDisplayInfo mPhys;

        private DisplayDeviceInfo mInfo;
        private boolean mHavePendingChanges;
        private int mState = Display.STATE_UNKNOWN;

        public LocalDisplayDevice(IBinder displayToken, int builtInDisplayId,
                SurfaceControl.PhysicalDisplayInfo phys) {
            super(LocalDisplayAdapter.this, displayToken);
            mBuiltInDisplayId = builtInDisplayId;
            mPhys = new SurfaceControl.PhysicalDisplayInfo(phys);
        }

        public boolean updatePhysicalDisplayInfoLocked(SurfaceControl.PhysicalDisplayInfo phys) {
            if (!mPhys.equals(phys)) {
                mPhys.copyFrom(phys);
                mHavePendingChanges = true;
                return true;
            }
            return false;
        }

        @Override
        public void applyPendingDisplayDeviceInfoChangesLocked() {
            if (mHavePendingChanges) {
                mInfo = null;
                mHavePendingChanges = false;
            }
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            if (mInfo == null) {
                mInfo = new DisplayDeviceInfo();
                mInfo.width = mPhys.width;
                mInfo.height = mPhys.height;
                mInfo.refreshRate = mPhys.refreshRate;
                mInfo.appVsyncOffsetNanos = mPhys.appVsyncOffsetNanos;
                mInfo.presentationDeadlineNanos = mPhys.presentationDeadlineNanos;
                mInfo.state = mState;

                // Assume that all built-in displays that have secure output (eg. HDCP) also
                // support compositing from gralloc protected buffers.
                if (mPhys.secure) {
                    mInfo.flags = DisplayDeviceInfo.FLAG_SECURE
                            | DisplayDeviceInfo.FLAG_SUPPORTS_PROTECTED_BUFFERS;
                }

                if (mBuiltInDisplayId == SurfaceControl.BUILT_IN_DISPLAY_ID_MAIN) {
                    mInfo.name = getContext().getResources().getString(
                            com.android.internal.R.string.display_manager_built_in_display_name);
                    mInfo.flags |= DisplayDeviceInfo.FLAG_DEFAULT_DISPLAY
                            | DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT;
                    mInfo.type = Display.TYPE_BUILT_IN;
                    mInfo.densityDpi = (int)(mPhys.density * 160 + 0.5f);
                    mInfo.xDpi = mPhys.xDpi;
                    mInfo.yDpi = mPhys.yDpi;
                    mInfo.touch = DisplayDeviceInfo.TOUCH_INTERNAL;
                } else {
                    mInfo.type = Display.TYPE_HDMI;
                    mInfo.flags |= DisplayDeviceInfo.FLAG_PRESENTATION;
                    mInfo.name = getContext().getResources().getString(
                            com.android.internal.R.string.display_manager_hdmi_display_name);
                    mInfo.touch = DisplayDeviceInfo.TOUCH_EXTERNAL;
                    mInfo.setAssumedDensityForExternalDisplay(mPhys.width, mPhys.height);

                    // For demonstration purposes, allow rotation of the external display.
                    // In the future we might allow the user to configure this directly.
                    if ("portrait".equals(SystemProperties.get("persist.demo.hdmirotation"))) {
                        mInfo.rotation = Surface.ROTATION_270;
                    }

                    // For demonstration purposes, allow rotation of the external display
                    // to follow the built-in display.
                    if (SystemProperties.getBoolean("persist.demo.hdmirotates", false)) {
                        mInfo.flags |= DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT;
                    }
                }
            }
            return mInfo;
        }

        @Override
        public void requestDisplayStateLocked(int state) {
            if (mState != state) {
                SurfaceControl.setDisplayPowerMode(getDisplayTokenLocked(),
                        getPowerModeForState(state));
                mState = state;
                updateDeviceInfoLocked();
            }
        }

        @Override
        public void dumpLocked(PrintWriter pw) {
            super.dumpLocked(pw);
            pw.println("mBuiltInDisplayId=" + mBuiltInDisplayId);
            pw.println("mPhys=" + mPhys);
            pw.println("mState=" + Display.stateToString(mState));
        }

        private void updateDeviceInfoLocked() {
            mInfo = null;
            sendDisplayDeviceEventLocked(this, DISPLAY_DEVICE_EVENT_CHANGED);
        }
    }

    private final class HotplugDisplayEventReceiver extends DisplayEventReceiver {
        public HotplugDisplayEventReceiver(Looper looper) {
            super(looper);
        }

        @Override
        public void onHotplug(long timestampNanos, int builtInDisplayId, boolean connected) {
            synchronized (getSyncRoot()) {
                if (connected) {
                    tryConnectDisplayLocked(builtInDisplayId);
                } else {
                    tryDisconnectDisplayLocked(builtInDisplayId);
                }
            }
        }
    }
}
