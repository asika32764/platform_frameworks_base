/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.server;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.CellLocation;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.CellInfo;
import android.telephony.VoLteServiceState;
import android.telephony.TelephonyManager;
import android.telephony.DisconnectCause;
import android.telephony.PreciseCallState;
import android.telephony.PreciseDataConnectionState;
import android.telephony.PreciseDisconnectCause;
import android.text.TextUtils;
import android.util.Slog;

import java.util.ArrayList;
import java.util.List;
import java.io.FileDescriptor;
import java.io.PrintWriter;

import com.android.internal.app.IBatteryStats;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.IPhoneStateListener;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyIntents;
import com.android.server.am.BatteryStatsService;

/**
 * Since phone process can be restarted, this class provides a centralized place
 * that applications can register and be called back from.
 */
class TelephonyRegistry extends ITelephonyRegistry.Stub {
    private static final String TAG = "TelephonyRegistry";
    private static final boolean DBG = false; // STOPSHIP if true
    private static final boolean DBG_LOC = false; // STOPSHIP if true
    private static final boolean VDBG = false; // STOPSHIP if true

    private static class Record {
        String pkgForDebug;

        IBinder binder;

        IPhoneStateListener callback;

        int callerUid;

        int events;

        long subId;

        boolean isLegacyApp;

        @Override
        public String toString() {
            return "{pkgForDebug=" + pkgForDebug + " callerUid=" + callerUid + " subId=" + subId +
                    " events=" + Integer.toHexString(events) + "}";
        }
    }

    private final Context mContext;

    // access should be inside synchronized (mRecords) for these two fields
    private final ArrayList<IBinder> mRemoveList = new ArrayList<IBinder>();
    private final ArrayList<Record> mRecords = new ArrayList<Record>();

    private final IBatteryStats mBatteryStats;

    private int mNumPhones;

    private int[] mCallState;

    private String[] mCallIncomingNumber;

    private ServiceState[] mServiceState;

    private SignalStrength[] mSignalStrength;

    private boolean[] mMessageWaiting;

    private boolean[] mCallForwarding;

    private int[] mDataActivity;

    private int[] mDataConnectionState;

    private boolean[] mDataConnectionPossible;

    private String[] mDataConnectionReason;

    private String[] mDataConnectionApn;

    private ArrayList<String> mConnectedApns;

    private LinkProperties[] mDataConnectionLinkProperties;

    private NetworkCapabilities[] mDataConnectionNetworkCapabilities;

    private Bundle[] mCellLocation;

    private int[] mDataConnectionNetworkType;

    private int mOtaspMode = ServiceStateTracker.OTASP_UNKNOWN;

    private ArrayList<List<CellInfo>> mCellInfo = null;

    private VoLteServiceState mVoLteServiceState = new VoLteServiceState();

    private long mDefaultSubId;

    private DataConnectionRealTimeInfo mDcRtInfo = new DataConnectionRealTimeInfo();

    private int mRingingCallState = PreciseCallState.PRECISE_CALL_STATE_IDLE;

    private int mForegroundCallState = PreciseCallState.PRECISE_CALL_STATE_IDLE;

    private int mBackgroundCallState = PreciseCallState.PRECISE_CALL_STATE_IDLE;

    private PreciseCallState mPreciseCallState = new PreciseCallState();

    private PreciseDataConnectionState mPreciseDataConnectionState =
                new PreciseDataConnectionState();

    static final int PHONE_STATE_PERMISSION_MASK =
                PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR |
                PhoneStateListener.LISTEN_CALL_STATE |
                PhoneStateListener.LISTEN_DATA_ACTIVITY |
                PhoneStateListener.LISTEN_DATA_CONNECTION_STATE |
                PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR |
                PhoneStateListener.LISTEN_VOLTE_STATE;;

    static final int PRECISE_PHONE_STATE_PERMISSION_MASK =
                PhoneStateListener.LISTEN_PRECISE_CALL_STATE |
                PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE;

    private static final int MSG_USER_SWITCHED = 1;
    private static final int MSG_UPDATE_DEFAULT_SUB = 2;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_USER_SWITCHED: {
                    Slog.d(TAG, "MSG_USER_SWITCHED userId=" + msg.arg1);
                    int numPhones = TelephonyManager.getDefault().getPhoneCount();
                    for (int sub = 0; sub < numPhones; sub++) {
                        TelephonyRegistry.this.notifyCellLocationUsingSubId(sub, mCellLocation[sub]);
                    }
                    break;
                }
                case MSG_UPDATE_DEFAULT_SUB: {
                    Slog.d(TAG, "MSG_UPDATE_DEFAULT_SUB subid=" + mDefaultSubId);
                    // Default subscription id changed, update the changed default subscription
                    // id in  all the legacy application listener records.
                    synchronized (mRecords) {
                        for (Record r : mRecords) {
                            // FIXME: Be sure we're using isLegacyApp correctly!
                            if (r.isLegacyApp == true) {
                                r.subId = mDefaultSubId;
                            }
                        }
                    }
                    break;
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Slog.d(TAG, "mBroadcastReceiver: action=" + action);
            if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                if (DBG) Slog.d(TAG, "onReceive: userHandle=" + userHandle);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCHED, userHandle, 0));
            } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_SUBSCRIPTION_CHANGED)) {
                mDefaultSubId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.getDefaultSubId());
                if (DBG) Slog.d(TAG, "onReceive: mDefaultSubId=" + mDefaultSubId);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_DEFAULT_SUB, 0, 0));
            }
        }
    };

    // we keep a copy of all of the state so we can send it out when folks
    // register for it
    //
    // In these calls we call with the lock held. This is safe becasuse remote
    // calls go through a oneway interface and local calls going through a
    // handler before they get to app code.

    TelephonyRegistry(Context context) {
        CellLocation  location = CellLocation.getEmpty();

        mContext = context;
        mBatteryStats = BatteryStatsService.getService();
        mConnectedApns = new ArrayList<String>();

        // Initialize default subscription to be used for single standby.
        mDefaultSubId = SubscriptionManager.getDefaultSubId();

        int numPhones = TelephonyManager.getDefault().getPhoneCount();
        if (DBG) Slog.d(TAG, "TelephonyRegistor: ctor numPhones=" + numPhones);
        mNumPhones = numPhones;
        mCallState = new int[numPhones];
        mDataActivity = new int[numPhones];
        mDataConnectionState = new int[numPhones];
        mDataConnectionNetworkType = new int[numPhones];
        mCallIncomingNumber = new String[numPhones];
        mServiceState = new ServiceState[numPhones];
        mSignalStrength = new SignalStrength[numPhones];
        mMessageWaiting = new boolean[numPhones];
        mDataConnectionPossible = new boolean[numPhones];
        mDataConnectionReason = new String[numPhones];
        mDataConnectionApn = new String[numPhones];
        mCallForwarding = new boolean[numPhones];
        mCellLocation = new Bundle[numPhones];
        mDataConnectionLinkProperties = new LinkProperties[numPhones];
        mDataConnectionNetworkCapabilities = new NetworkCapabilities[numPhones];
        mCellInfo = new ArrayList<List<CellInfo>>();
        for (int i = 0; i < numPhones; i++) {
            mCallState[i] =  TelephonyManager.CALL_STATE_IDLE;
            mDataActivity[i] = TelephonyManager.DATA_ACTIVITY_NONE;
            mDataConnectionState[i] = TelephonyManager.DATA_UNKNOWN;
            mCallIncomingNumber[i] =  "";
            mServiceState[i] =  new ServiceState();
            mSignalStrength[i] =  new SignalStrength();
            mMessageWaiting[i] =  false;
            mCallForwarding[i] =  false;
            mDataConnectionPossible[i] = false;
            mDataConnectionReason[i] =  "";
            mDataConnectionApn[i] =  "";
            mCellLocation[i] = new Bundle();
            mCellInfo.add(i, null);
        }

        // Note that location can be null for non-phone builds like
        // like the generic one.
        if (location != null) {
            for (int i = 0; i < numPhones; i++) {
                location.fillInNotifierBundle(mCellLocation[i]);
            }
        }
        mConnectedApns = new ArrayList<String>();
    }

    public void systemRunning() {
        // Watch for interesting updates
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(TelephonyIntents.ACTION_DEFAULT_SUBSCRIPTION_CHANGED);
        Slog.d(TAG, "systemRunning register for intents");
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void listen(String pkgForDebug, IPhoneStateListener callback, int events,
            boolean notifyNow) {
        listen(pkgForDebug, callback, events, notifyNow, mDefaultSubId, true);
    }

    @Override
    public void listenUsingSubId(long subId, String pkgForDebug, IPhoneStateListener callback,
            int events, boolean notifyNow) {
        listen(pkgForDebug, callback, events, notifyNow, subId, false);
    }

    private void listen(String pkgForDebug, IPhoneStateListener callback, int events,
            boolean notifyNow, long subId, boolean isLegacyApp) {
        int callerUid = UserHandle.getCallingUserId();
        int myUid = UserHandle.myUserId();
        if (VDBG) {
            Slog.d(TAG, "listen: E pkg=" + pkgForDebug + " events=0x" + Integer.toHexString(events)
                + " notifyNow=" + notifyNow + " subId=" + subId
                + " isLegacyApp=" + isLegacyApp
                + " myUid=" + myUid
                + " callerUid=" + callerUid);
        }
        if (events != 0) {
            /* Checks permission and throws Security exception */
            checkListenerPermission(events);

            synchronized (mRecords) {
                // register
                Record r = null;
                find_and_add: {
                    IBinder b = callback.asBinder();
                    final int N = mRecords.size();
                    for (int i = 0; i < N; i++) {
                        r = mRecords.get(i);
                        if (b == r.binder) {
                            break find_and_add;
                        }
                    }
                    r = new Record();
                    r.binder = b;
                    r.callback = callback;
                    r.pkgForDebug = pkgForDebug;
                    r.callerUid = callerUid;
                    r.subId = subId;
                    r.isLegacyApp = isLegacyApp;
                    // Legacy applications pass invalid subId(-1), based on
                    // the received subId value update the isLegacyApp field
                    if ((r.subId <= 0) || (r.subId == SubscriptionManager.INVALID_SUB_ID)) {
                        r.subId = mDefaultSubId;
                        r.isLegacyApp = true; // r.subId is to be update when default changes.
                    }
                    if (r.subId == SubscriptionManager.DEFAULT_SUB_ID) {
                        r.subId = mDefaultSubId;
                        r.isLegacyApp = true; // r.subId is to be update when default changes.
                        if (DBG) Slog.i(TAG, "listen: DEFAULT_SUB_ID");
                    }
                    mRecords.add(r);
                    if (DBG) Slog.i(TAG, "listen: add new record");
                }
                int phoneId = SubscriptionManager.getPhoneId(subId);
                r.events = events;
                if (DBG) Slog.i(TAG, "listen: set events record=" + r);
                if (notifyNow && validatePhoneId(phoneId)) {
                    if ((events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
                        try {
                            r.callback.onServiceStateChanged(
                                    new ServiceState(mServiceState[phoneId]));
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) {
                        try {
                            int gsmSignalStrength = mSignalStrength[phoneId]
                                    .getGsmSignalStrength();
                            r.callback.onSignalStrengthChanged((gsmSignalStrength == 99 ? -1
                                    : gsmSignalStrength));
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) {
                        try {
                            r.callback.onMessageWaitingIndicatorChanged(
                                    mMessageWaiting[phoneId]);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) {
                        try {
                            r.callback.onCallForwardingIndicatorChanged(
                                    mCallForwarding[phoneId]);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if (validateEventsAndUserLocked(r, PhoneStateListener.LISTEN_CELL_LOCATION)) {
                        try {
                            if (DBG_LOC) Slog.d(TAG, "listen: mCellLocation = "
                                    + mCellLocation[phoneId]);
                            r.callback.onCellLocationChanged(
                                    new Bundle(mCellLocation[phoneId]));
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
                        try {
                            r.callback.onCallStateChanged(mCallState[phoneId],
                                     mCallIncomingNumber[phoneId]);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) {
                        try {
                            r.callback.onDataConnectionStateChanged(mDataConnectionState[phoneId],
                                mDataConnectionNetworkType[phoneId]);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
                        try {
                            r.callback.onDataActivity(mDataActivity[phoneId]);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) != 0) {
                        try {
                            r.callback.onSignalStrengthsChanged(mSignalStrength[phoneId]);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_OTASP_CHANGED) != 0) {
                        try {
                            r.callback.onOtaspChanged(mOtaspMode);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if (validateEventsAndUserLocked(r, PhoneStateListener.LISTEN_CELL_INFO)) {
                        try {
                            if (DBG_LOC) Slog.d(TAG, "listen: mCellInfo[" + phoneId + "] = "
                                    + mCellInfo.get(phoneId));
                            r.callback.onCellInfoChanged(mCellInfo.get(phoneId));
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_DATA_CONNECTION_REAL_TIME_INFO) != 0) {
                        try {
                            r.callback.onDataConnectionRealTimeInfoChanged(mDcRtInfo);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_PRECISE_CALL_STATE) != 0) {
                        try {
                            r.callback.onPreciseCallStateChanged(mPreciseCallState);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE) != 0) {
                        try {
                            r.callback.onPreciseDataConnectionStateChanged(
                                    mPreciseDataConnectionState);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                }
            }
        } else {
            remove(callback.asBinder());
        }
    }

    private void remove(IBinder binder) {
        synchronized (mRecords) {
            final int recordCount = mRecords.size();
            for (int i = 0; i < recordCount; i++) {
                if (mRecords.get(i).binder == binder) {
                    mRecords.remove(i);
                    return;
                }
            }
        }
    }

    public void notifyCallState(int state, String incomingNumber) {
        if (!checkNotifyPermission("notifyCallState()")) {
            return;
        }
        synchronized (mRecords) {
            for (Record r : mRecords) {
                if (((r.events & PhoneStateListener.LISTEN_CALL_STATE) != 0) &&
                    (r.isLegacyApp == true)) {
                    // FIXME: why does isLegacyApp need to be true?
                    try {
                        r.callback.onCallStateChanged(state, incomingNumber);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastCallStateChanged(state, incomingNumber, mDefaultSubId);
    }

    public void notifyCallStateUsingSubId(long subId, int state, String incomingNumber) {
        if (!checkNotifyPermission("notifyCallState()")) {
            return;
        }
        if (VDBG) {
            Slog.d(TAG, "notifyCallStateUsingSubId: subId=" + subId
                + " state=" + state + " incomingNumber=" + incomingNumber);
        }
        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (validatePhoneId(phoneId)) {
                mCallState[phoneId] = state;
                mCallIncomingNumber[phoneId] = incomingNumber;
                for (Record r : mRecords) {
                    if (((r.events & PhoneStateListener.LISTEN_CALL_STATE) != 0) &&
                        (r.subId == subId) && (r.isLegacyApp == false)) {
                        // FIXME: why isLegacyApp false?
                        try {
                            r.callback.onCallStateChanged(state, incomingNumber);
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastCallStateChanged(state, incomingNumber, subId);
    }

     public void notifyServiceState(ServiceState state) {
         notifyServiceStateUsingSubId(mDefaultSubId, state);
     }

    public void notifyServiceStateUsingSubId(long subId, ServiceState state) {
        if (!checkNotifyPermission("notifyServiceState()")){
            return;
        }
        if (subId == SubscriptionManager.DEFAULT_SUB_ID) {
            subId = mDefaultSubId;
            Slog.d(TAG, "notifyServiceStateUsingSubId: using mDefaultSubId=" + mDefaultSubId);
        }
        if (VDBG) {
            Slog.d(TAG, "notifyServiceStateUsingSubId: subId=" + subId
                + " state=" + state);
        }
        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (validatePhoneId(phoneId)) {
                mServiceState[phoneId] = state;
                for (Record r : mRecords) {
                    // FIXME: use DEFAULT_SUB_ID instead??
                    if (((r.events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) &&
                            (r.subId == subId)) {
                        try {
                            r.callback.onServiceStateChanged(new ServiceState(state));
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                }
            } else {
                Slog.d(TAG, "notifyServiceStateUsingSubId: INVALID phoneId=" + phoneId);
            }
            handleRemoveListLocked();
        }
        broadcastServiceStateChanged(state, subId);
    }

    public void notifySignalStrength(SignalStrength signalStrength) {
        notifySignalStrengthUsingSubId(mDefaultSubId, signalStrength);
    }

    public void notifySignalStrengthUsingSubId(long subId, SignalStrength signalStrength) {
        if (!checkNotifyPermission("notifySignalStrength()")) {
            return;
        }
        if (VDBG) {
            Slog.d(TAG, "notifySignalStrengthUsingSubId: subId=" + subId
                + " signalStrength=" + signalStrength);
        }
        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (validatePhoneId(phoneId)) {
                mSignalStrength[phoneId] = signalStrength;
                for (Record r : mRecords) {
                    if (((r.events & PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) != 0) &&
                        (r.subId == subId)){
                        try {
                            r.callback.onSignalStrengthsChanged(new SignalStrength(signalStrength));
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                    if (((r.events & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) &&
                        (r.subId == subId)) {
                        try {
                            int gsmSignalStrength = signalStrength.getGsmSignalStrength();
                            r.callback.onSignalStrengthChanged((gsmSignalStrength == 99 ? -1
                                    : gsmSignalStrength));
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastSignalStrengthChanged(signalStrength, subId);
    }

    public void notifyCellInfo(List<CellInfo> cellInfo) {
         notifyCellInfoUsingSubId(mDefaultSubId, cellInfo);
    }

    public void notifyCellInfoUsingSubId(long subId, List<CellInfo> cellInfo) {
        if (!checkNotifyPermission("notifyCellInfo()")) {
            return;
        }
        if (VDBG) {
            Slog.d(TAG, "notifyCellInfoUsingSubId: subId=" + subId
                + " cellInfo=" + cellInfo);
        }

        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (validatePhoneId(phoneId)) {
                mCellInfo.set(phoneId, cellInfo);
                for (Record r : mRecords) {
                    if (validateEventsAndUserLocked(r, PhoneStateListener.LISTEN_CELL_INFO)
                            && r.subId == subId) {
                        try {
                            if (DBG_LOC) {
                                Slog.d(TAG, "notifyCellInfo: mCellInfo=" + cellInfo + " r=" + r);
                            }
                            r.callback.onCellInfoChanged(cellInfo);
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    public void notifyDataConnectionRealTimeInfo(DataConnectionRealTimeInfo dcRtInfo) {
        if (!checkNotifyPermission("notifyDataConnectionRealTimeInfo()")) {
            return;
        }

        synchronized (mRecords) {
            mDcRtInfo = dcRtInfo;
            for (Record r : mRecords) {
                if (validateEventsAndUserLocked(r,
                        PhoneStateListener.LISTEN_DATA_CONNECTION_REAL_TIME_INFO)) {
                    try {
                        if (DBG_LOC) {
                            Slog.d(TAG, "notifyDataConnectionRealTimeInfo: mDcRtInfo="
                                    + mDcRtInfo + " r=" + r);
                        }
                        r.callback.onDataConnectionRealTimeInfoChanged(mDcRtInfo);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    public void notifyMessageWaitingChanged(boolean mwi) {
        notifyMessageWaitingChangedUsingSubId(mDefaultSubId, mwi);
    }

    public void notifyMessageWaitingChangedUsingSubId(long subId, boolean mwi) {
        if (!checkNotifyPermission("notifyMessageWaitingChanged()")) {
            return;
        }
        if (VDBG) {
            Slog.d(TAG, "notifyMessageWaitingChangedUsingSubId: subId=" + subId
                + " mwi=" + mwi);
        }
        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (validatePhoneId(phoneId)) {
                mMessageWaiting[phoneId] = mwi;
                for (Record r : mRecords) {
                    if (((r.events & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) &&
                        (r.subId == subId)) {
                        try {
                            r.callback.onMessageWaitingIndicatorChanged(mwi);
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    public void notifyCallForwardingChanged(boolean cfi) {
        notifyCallForwardingChangedUsingSubId(mDefaultSubId, cfi);
    }

    public void notifyCallForwardingChangedUsingSubId(long subId, boolean cfi) {
        if (!checkNotifyPermission("notifyCallForwardingChanged()")) {
            return;
        }
        if (VDBG) {
            Slog.d(TAG, "notifyCallForwardingChangedUsingSubId: subId=" + subId
                + " cfi=" + cfi);
        }
        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (validatePhoneId(phoneId)) {
                mCallForwarding[phoneId] = cfi;
                for (Record r : mRecords) {
                    if (((r.events & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) &&
                        (r.subId == subId)) {
                        try {
                            r.callback.onCallForwardingIndicatorChanged(cfi);
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    public void notifyDataActivity(int state) {
        notifyDataActivityUsingSubId(mDefaultSubId, state);
    }

    public void notifyDataActivityUsingSubId(long subId, int state) {
        if (!checkNotifyPermission("notifyDataActivity()" )) {
            return;
        }
        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            mDataActivity[phoneId] = state;
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
                    try {
                        r.callback.onDataActivity(state);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    public void notifyDataConnection(int state, boolean isDataConnectivityPossible,
            String reason, String apn, String apnType, LinkProperties linkProperties,
            NetworkCapabilities networkCapabilities, int networkType, boolean roaming) {
        notifyDataConnectionUsingSubId(mDefaultSubId, state, isDataConnectivityPossible,
            reason, apn, apnType, linkProperties,
            networkCapabilities, networkType, roaming);
    }

    public void notifyDataConnectionUsingSubId(long subId, int state,
            boolean isDataConnectivityPossible, String reason, String apn, String apnType,
            LinkProperties linkProperties, NetworkCapabilities networkCapabilities,
            int networkType, boolean roaming) {
        if (!checkNotifyPermission("notifyDataConnection()" )) {
            return;
        }
        if (VDBG) {
            Slog.i(TAG, "notifyDataConnectionUsingSubId: subId=" + subId
                + " state=" + state + " isDataConnectivityPossible=" + isDataConnectivityPossible
                + " reason='" + reason
                + "' apn='" + apn + "' apnType=" + apnType + " networkType=" + networkType
                + " mRecords.size()=" + mRecords.size() + " mRecords=" + mRecords);
        }
        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            boolean modified = false;
            if (state == TelephonyManager.DATA_CONNECTED) {
                if (!mConnectedApns.contains(apnType)) {
                    mConnectedApns.add(apnType);
                    if (mDataConnectionState[phoneId] != state) {
                        mDataConnectionState[phoneId] = state;
                        modified = true;
                    }
                }
            } else {
                if (mConnectedApns.remove(apnType)) {
                    if (mConnectedApns.isEmpty()) {
                        mDataConnectionState[phoneId] = state;
                        modified = true;
                    } else {
                        // leave mDataConnectionState as is and
                        // send out the new status for the APN in question.
                    }
                }
            }
            mDataConnectionPossible[phoneId] = isDataConnectivityPossible;
            mDataConnectionReason[phoneId] = reason;
            mDataConnectionLinkProperties[phoneId] = linkProperties;
            mDataConnectionNetworkCapabilities[phoneId] = networkCapabilities;
            if (mDataConnectionNetworkType[phoneId] != networkType) {
                mDataConnectionNetworkType[phoneId] = networkType;
                // need to tell registered listeners about the new network type
                modified = true;
            }
            if (modified) {
                if (DBG) {
                    Slog.d(TAG, "onDataConnectionStateChanged(" + mDataConnectionState[phoneId]
                        + ", " + mDataConnectionNetworkType[phoneId] + ")");
                }
                for (Record r : mRecords) {
                    if (((r.events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) &&
                            (r.subId == subId)) {
                        try {
                            Slog.d(TAG,"Notify data connection state changed on sub: " +
                                    subId);
                            r.callback.onDataConnectionStateChanged(mDataConnectionState[phoneId],
                                    mDataConnectionNetworkType[phoneId]);
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                }
                handleRemoveListLocked();
            }
            mPreciseDataConnectionState = new PreciseDataConnectionState(state, networkType,
                    apnType, apn, reason, linkProperties, "");
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE) != 0) {
                    try {
                        r.callback.onPreciseDataConnectionStateChanged(mPreciseDataConnectionState);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastDataConnectionStateChanged(state, isDataConnectivityPossible, reason, apn,
                apnType, linkProperties, networkCapabilities, roaming, subId);
        broadcastPreciseDataConnectionStateChanged(state, networkType, apnType, apn, reason,
                linkProperties, "");
    }

    public void notifyDataConnectionFailed(String reason, String apnType) {
         notifyDataConnectionFailedUsingSubId(mDefaultSubId, reason, apnType);
    }

    public void notifyDataConnectionFailedUsingSubId(long subId,
            String reason, String apnType) {
        if (!checkNotifyPermission("notifyDataConnectionFailed()")) {
            return;
        }
        if (VDBG) {
            Slog.d(TAG, "notifyDataConnectionFailedUsingSubId: subId=" + subId
                + " reason=" + reason + " apnType=" + apnType);
        }
        synchronized (mRecords) {
            mPreciseDataConnectionState = new PreciseDataConnectionState(
                    TelephonyManager.DATA_UNKNOWN,TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    apnType, "", reason, null, "");
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE) != 0) {
                    try {
                        r.callback.onPreciseDataConnectionStateChanged(mPreciseDataConnectionState);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastDataConnectionFailed(reason, apnType, subId);
        broadcastPreciseDataConnectionStateChanged(TelephonyManager.DATA_UNKNOWN,
                TelephonyManager.NETWORK_TYPE_UNKNOWN, apnType, "", reason, null, "");
    }

    public void notifyCellLocation(Bundle cellLocation) {
         notifyCellLocationUsingSubId(mDefaultSubId, cellLocation);
    }

    public void notifyCellLocationUsingSubId(long subId, Bundle cellLocation) {
        Slog.d(TAG, "notifyCellLocationUsingSubId: subId=" + subId
                + " cellLocation=" + cellLocation);
        if (!checkNotifyPermission("notifyCellLocation()")) {
            return;
        }
        if (VDBG) {
            Slog.d(TAG, "notifyCellLocationUsingSubId: subId=" + subId
                + " cellLocation=" + cellLocation);
        }
        synchronized (mRecords) {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (validatePhoneId(phoneId)) {
                mCellLocation[phoneId] = cellLocation;
                for (Record r : mRecords) {
                    if (validateEventsAndUserLocked(r, PhoneStateListener.LISTEN_CELL_LOCATION)
                            && r.subId == subId) {
                        try {
                            if (DBG_LOC) {
                                Slog.d(TAG, "notifyCellLocation: cellLocation=" + cellLocation
                                        + " r=" + r);
                            }
                            r.callback.onCellLocationChanged(new Bundle(cellLocation));
                        } catch (RemoteException ex) {
                            mRemoveList.add(r.binder);
                        }
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    public void notifyOtaspChanged(int otaspMode) {
        if (!checkNotifyPermission("notifyOtaspChanged()" )) {
            return;
        }
        synchronized (mRecords) {
            mOtaspMode = otaspMode;
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_OTASP_CHANGED) != 0) {
                    try {
                        r.callback.onOtaspChanged(otaspMode);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    public void notifyPreciseCallState(int ringingCallState, int foregroundCallState,
            int backgroundCallState) {
        if (!checkNotifyPermission("notifyPreciseCallState()")) {
            return;
        }
        synchronized (mRecords) {
            mRingingCallState = ringingCallState;
            mForegroundCallState = foregroundCallState;
            mBackgroundCallState = backgroundCallState;
            mPreciseCallState = new PreciseCallState(ringingCallState, foregroundCallState,
                    backgroundCallState,
                    DisconnectCause.NOT_VALID,
                    PreciseDisconnectCause.NOT_VALID);
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_PRECISE_CALL_STATE) != 0) {
                    try {
                        r.callback.onPreciseCallStateChanged(mPreciseCallState);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastPreciseCallStateChanged(ringingCallState, foregroundCallState, backgroundCallState,
                DisconnectCause.NOT_VALID,
                PreciseDisconnectCause.NOT_VALID);
    }

    public void notifyDisconnectCause(int disconnectCause, int preciseDisconnectCause) {
        if (!checkNotifyPermission("notifyDisconnectCause()")) {
            return;
        }
        synchronized (mRecords) {
            mPreciseCallState = new PreciseCallState(mRingingCallState, mForegroundCallState,
                    mBackgroundCallState, disconnectCause, preciseDisconnectCause);
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_PRECISE_CALL_STATE) != 0) {
                    try {
                        r.callback.onPreciseCallStateChanged(mPreciseCallState);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastPreciseCallStateChanged(mRingingCallState, mForegroundCallState,
                mBackgroundCallState, disconnectCause, preciseDisconnectCause);
    }

    public void notifyPreciseDataConnectionFailed(String reason, String apnType,
            String apn, String failCause) {
        if (!checkNotifyPermission("notifyPreciseDataConnectionFailed()")) {
            return;
        }
        synchronized (mRecords) {
            mPreciseDataConnectionState = new PreciseDataConnectionState(
                    TelephonyManager.DATA_UNKNOWN, TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    apnType, apn, reason, null, failCause);
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE) != 0) {
                    try {
                        r.callback.onPreciseDataConnectionStateChanged(mPreciseDataConnectionState);
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastPreciseDataConnectionStateChanged(TelephonyManager.DATA_UNKNOWN,
                TelephonyManager.NETWORK_TYPE_UNKNOWN, apnType, apn, reason, null, failCause);
    }

    public void notifyVoLteServiceStateChanged(VoLteServiceState lteState) {
        if (!checkNotifyPermission("notifyVoLteServiceStateChanged()")) {
            return;
        }
        synchronized (mRecords) {
            mVoLteServiceState = lteState;
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_VOLTE_STATE) != 0) {
                    try {
                        r.callback.onVoLteServiceStateChanged(
                                new VoLteServiceState(mVoLteServiceState));
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump telephony.registry from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        synchronized (mRecords) {
            final int recordCount = mRecords.size();
            pw.println("last known state:");
            pw.println("  mCallState=" + mCallState);
            pw.println("  mCallIncomingNumber=" + mCallIncomingNumber);
            pw.println("  mServiceState=" + mServiceState);
            pw.println("  mSignalStrength=" + mSignalStrength);
            pw.println("  mMessageWaiting=" + mMessageWaiting);
            pw.println("  mCallForwarding=" + mCallForwarding);
            pw.println("  mDataActivity=" + mDataActivity);
            pw.println("  mDataConnectionState=" + mDataConnectionState);
            pw.println("  mDataConnectionPossible=" + mDataConnectionPossible);
            pw.println("  mDataConnectionReason=" + mDataConnectionReason);
            pw.println("  mDataConnectionApn=" + mDataConnectionApn);
            pw.println("  mDataConnectionLinkProperties=" + mDataConnectionLinkProperties);
            pw.println("  mDataConnectionNetworkCapabilities=" +
                    mDataConnectionNetworkCapabilities);
            pw.println("  mDefaultSubId=" + mDefaultSubId);
            pw.println("  mCellLocation=" + mCellLocation);
            pw.println("  mCellInfo=" + mCellInfo);
            pw.println("  mDcRtInfo=" + mDcRtInfo);
            pw.println("registrations: count=" + recordCount);
            for (Record r : mRecords) {
                pw.println("  " + r.pkgForDebug + " 0x" + Integer.toHexString(r.events));
            }
        }
    }

    //
    // the legacy intent broadcasting
    //

    private void broadcastServiceStateChanged(ServiceState state, long subId) {
        long ident = Binder.clearCallingIdentity();
        try {
            mBatteryStats.notePhoneState(state.getState());
        } catch (RemoteException re) {
            // Can't do much
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        Intent intent = new Intent(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        Bundle data = new Bundle();
        state.fillInNotifierBundle(data);
        intent.putExtras(data);
        // Pass the subscription along with the intent.
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void broadcastSignalStrengthChanged(SignalStrength signalStrength, long subId) {
        long ident = Binder.clearCallingIdentity();
        try {
            mBatteryStats.notePhoneSignalStrength(signalStrength);
        } catch (RemoteException e) {
            /* The remote entity disappeared, we can safely ignore the exception. */
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        Intent intent = new Intent(TelephonyIntents.ACTION_SIGNAL_STRENGTH_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        Bundle data = new Bundle();
        signalStrength.fillInNotifierBundle(data);
        intent.putExtras(data);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void broadcastCallStateChanged(int state, String incomingNumber, long subId) {
        long ident = Binder.clearCallingIdentity();
        try {
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                mBatteryStats.notePhoneOff();
            } else {
                mBatteryStats.notePhoneOn();
            }
        } catch (RemoteException e) {
            /* The remote entity disappeared, we can safely ignore the exception. */
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(PhoneConstants.STATE_KEY,
                DefaultPhoneNotifier.convertCallState(state).toString());
        if (!TextUtils.isEmpty(incomingNumber)) {
            intent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, incomingNumber);
        }
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                android.Manifest.permission.READ_PHONE_STATE);
    }

    private void broadcastDataConnectionStateChanged(int state,
            boolean isDataConnectivityPossible,
            String reason, String apn, String apnType, LinkProperties linkProperties,
            NetworkCapabilities networkCapabilities, boolean roaming, long subId) {
        // Note: not reporting to the battery stats service here, because the
        // status bar takes care of that after taking into account all of the
        // required info.
        Intent intent = new Intent(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        intent.putExtra(PhoneConstants.STATE_KEY,
                DefaultPhoneNotifier.convertDataState(state).toString());
        if (!isDataConnectivityPossible) {
            intent.putExtra(PhoneConstants.NETWORK_UNAVAILABLE_KEY, true);
        }
        if (reason != null) {
            intent.putExtra(PhoneConstants.STATE_CHANGE_REASON_KEY, reason);
        }
        if (linkProperties != null) {
            intent.putExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY, linkProperties);
            String iface = linkProperties.getInterfaceName();
            if (iface != null) {
                intent.putExtra(PhoneConstants.DATA_IFACE_NAME_KEY, iface);
            }
        }
        if (networkCapabilities != null) {
            intent.putExtra(PhoneConstants.DATA_NETWORK_CAPABILITIES_KEY, networkCapabilities);
        }
        if (roaming) intent.putExtra(PhoneConstants.DATA_NETWORK_ROAMING_KEY, true);

        intent.putExtra(PhoneConstants.DATA_APN_KEY, apn);
        intent.putExtra(PhoneConstants.DATA_APN_TYPE_KEY, apnType);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void broadcastDataConnectionFailed(String reason, String apnType,
            long subId) {
        Intent intent = new Intent(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);
        intent.putExtra(PhoneConstants.FAILURE_REASON_KEY, reason);
        intent.putExtra(PhoneConstants.DATA_APN_TYPE_KEY, apnType);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void broadcastPreciseCallStateChanged(int ringingCallState, int foregroundCallState,
            int backgroundCallState, int disconnectCause, int preciseDisconnectCause) {
        Intent intent = new Intent(TelephonyManager.ACTION_PRECISE_CALL_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_RINGING_CALL_STATE, ringingCallState);
        intent.putExtra(TelephonyManager.EXTRA_FOREGROUND_CALL_STATE, foregroundCallState);
        intent.putExtra(TelephonyManager.EXTRA_BACKGROUND_CALL_STATE, backgroundCallState);
        intent.putExtra(TelephonyManager.EXTRA_DISCONNECT_CAUSE, disconnectCause);
        intent.putExtra(TelephonyManager.EXTRA_PRECISE_DISCONNECT_CAUSE, preciseDisconnectCause);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                android.Manifest.permission.READ_PRECISE_PHONE_STATE);
    }

    private void broadcastPreciseDataConnectionStateChanged(int state, int networkType,
            String apnType, String apn, String reason, LinkProperties linkProperties, String failCause) {
        Intent intent = new Intent(TelephonyManager.ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED);
        intent.putExtra(PhoneConstants.STATE_KEY, state);
        intent.putExtra(PhoneConstants.DATA_NETWORK_TYPE_KEY, networkType);
        if (reason != null) intent.putExtra(PhoneConstants.STATE_CHANGE_REASON_KEY, reason);
        if (apnType != null) intent.putExtra(PhoneConstants.DATA_APN_TYPE_KEY, apnType);
        if (apn != null) intent.putExtra(PhoneConstants.DATA_APN_KEY, apn);
        if (linkProperties != null) intent.putExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY, linkProperties);
        if (failCause != null) intent.putExtra(PhoneConstants.DATA_FAILURE_CAUSE_KEY, failCause);

        mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                android.Manifest.permission.READ_PRECISE_PHONE_STATE);
    }

    private boolean checkNotifyPermission(String method) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        String msg = "Modify Phone State Permission Denial: " + method + " from pid="
                + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid();
        if (DBG) Slog.w(TAG, msg);
        return false;
    }

    private void checkListenerPermission(int events) {
        if ((events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION, null);

        }

        if ((events & PhoneStateListener.LISTEN_CELL_INFO) != 0) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION, null);

        }

        if ((events & PHONE_STATE_PERMISSION_MASK) != 0) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.READ_PHONE_STATE, null);
        }

        if ((events & PRECISE_PHONE_STATE_PERMISSION_MASK) != 0) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.READ_PRECISE_PHONE_STATE, null);

        }
    }

    private void handleRemoveListLocked() {
        if (mRemoveList.size() > 0) {
            for (IBinder b: mRemoveList) {
                remove(b);
            }
            mRemoveList.clear();
        }
    }

    private boolean validateEventsAndUserLocked(Record r, int events) {
        int foregroundUser;
        long callingIdentity = Binder.clearCallingIdentity();
        boolean valid = false;
        try {
            foregroundUser = ActivityManager.getCurrentUser();
            valid = r.callerUid ==  foregroundUser && (r.events & events) != 0;
            if (DBG | DBG_LOC) {
                Slog.d(TAG, "validateEventsAndUserLocked: valid=" + valid
                        + " r.callerUid=" + r.callerUid + " foregroundUser=" + foregroundUser
                        + " r.events=" + r.events + " events=" + events);
            }
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
        return valid;
    }

    private boolean validatePhoneId(int phoneId) {
        boolean valid = (phoneId >= 0) && (phoneId < mNumPhones);
        if (VDBG) Slog.d(TAG, "validatePhoneId: " + valid);
        return valid;
    }
}
