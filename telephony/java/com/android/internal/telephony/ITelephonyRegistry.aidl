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

package com.android.internal.telephony;

import android.content.Intent;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.telephony.CellInfo;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.CellInfo;
import android.telephony.VoLteServiceState;
import com.android.internal.telephony.IPhoneStateListener;

interface ITelephonyRegistry {
    void listen(String pkg, IPhoneStateListener callback, int events, boolean notifyNow);
    void listenUsingSubId(in long subId, String pkg, IPhoneStateListener callback, int events,
            boolean notifyNow);
    void notifyCallState(int state, String incomingNumber);
    void notifyCallStateUsingSubId(in long subId, int state, String incomingNumber);
    void notifyServiceState(in ServiceState state);
    void notifyServiceStateUsingSubId(in long subId, in ServiceState state);
    void notifySignalStrength(in SignalStrength signalStrength);
    void notifySignalStrengthUsingSubId(in long subId, in SignalStrength signalStrength);
    void notifyMessageWaitingChanged(boolean mwi);
    void notifyMessageWaitingChangedUsingSubId(in long subId, boolean mwi);
    void notifyCallForwardingChanged(boolean cfi);
    void notifyCallForwardingChangedUsingSubId(in long subId, boolean cfi);
    void notifyDataActivity(int state);
    void notifyDataActivityUsingSubId(in long subId, int state);
    void notifyDataConnection(int state, boolean isDataConnectivityPossible,
            String reason, String apn, String apnType, in LinkProperties linkProperties,
            in NetworkCapabilities networkCapabilities, int networkType, boolean roaming);
    void notifyDataConnectionUsingSubId(long subId, int state, boolean isDataConnectivityPossible,
            String reason, String apn, String apnType, in LinkProperties linkProperties,
            in NetworkCapabilities networkCapabilities, int networkType, boolean roaming);
    void notifyDataConnectionFailed(String reason, String apnType);
    void notifyDataConnectionFailedUsingSubId(long subId, String reason, String apnType);
    void notifyCellLocation(in Bundle cellLocation);
    void notifyCellLocationUsingSubId(in long subId, in Bundle cellLocation);
    void notifyOtaspChanged(in int otaspMode);
    void notifyCellInfo(in List<CellInfo> cellInfo);
    void notifyPreciseCallState(int ringingCallState, int foregroundCallState,
            int backgroundCallState);
    void notifyDisconnectCause(int disconnectCause, int preciseDisconnectCause);
    void notifyPreciseDataConnectionFailed(String reason, String apnType, String apn,
            String failCause);
    void notifyCellInfoUsingSubId(in long subId, in List<CellInfo> cellInfo);
    void notifyDataConnectionRealTimeInfo(in DataConnectionRealTimeInfo dcRtInfo);
    void notifyVoLteServiceStateChanged(in VoLteServiceState lteState);
}
