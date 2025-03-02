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

package android.telecomm;

import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;

/**
 * Defines constants for use with the Telecomm system.
 */
public final class TelecommConstants {
    /**
     * <p>Activity action: Starts the UI for handing an incoming call. This intent starts the
     * in-call UI by notifying the Telecomm system that an incoming call exists for a specific call
     * service (see {@link android.telecomm.CallService}). Telecomm reads the Intent extras to find
     * and bind to the appropriate {@link android.telecomm.CallService} which Telecomm will
     * ultimately use to control and get information about the call.</p>
     *
     * <p>Input: get*Extra field {@link #EXTRA_CALL_SERVICE_DESCRIPTOR} contains the component name
     * of the {@link android.telecomm.CallService} that Telecomm should bind to. Telecomm will then
     * ask the call service for more information about the call prior to showing any UI.
     *
     * TODO(santoscordon): Needs permissions.
     * TODO(santoscordon): Consider moving this into a simple method call on a system service.
     */
    public static final String ACTION_INCOMING_CALL = "android.intent.action.INCOMING_CALL";

    /**
     * The service action used to bind to {@link CallServiceProvider} implementations.
     */
    public static final String ACTION_CALL_SERVICE_PROVIDER = CallServiceProvider.class.getName();

    /**
     * The service action used to bind to {@link CallService} implementations.
     */
    public static final String ACTION_CALL_SERVICE = CallService.class.getName();

    /**
     * The service action used to bind to {@link CallServiceSelector} implementations.
     */
    public static final String ACTION_CALL_SERVICE_SELECTOR = CallServiceSelector.class.getName();

    /**
     * Activity action: Ask the user to change the default phone application. This will show a
     * dialog that asks the user whether they want to replace the current default phone application
     * with the one defined in {@link #EXTRA_PACKAGE_NAME}.
     */
    public static final String ACTION_CHANGE_DEFAULT_PHONE =
            "android.telecomm.ACTION_CHANGE_DEFAULT_PHONE";

    /**
     * The PackageName string passed in as an extra for {@link #ACTION_CHANGE_DEFAULT_PHONE}.
     *
     * @see #ACTION_CHANGE_DEFAULT_PHONE
     */
    public static final String EXTRA_PACKAGE_NAME = "package";

    /**
     * Optional extra for {@link Intent#ACTION_CALL} containing a boolean that determines whether
     * the speakerphone should be automatically turned on for an outgoing call.
     */
    public static final String EXTRA_START_CALL_WITH_SPEAKERPHONE =
            "android.intent.extra.START_CALL_WITH_SPEAKERPHONE";

    /**
     * Extra for {@link #ACTION_INCOMING_CALL} containing the {@link CallServiceDescriptor} that
     * describes the call service to use for the incoming call.
     */
    public static final String EXTRA_CALL_SERVICE_DESCRIPTOR =
            "android.intent.extra.CALL_SERVICE_DESCRIPTOR";

    /**
     * Optional extra for {@link #ACTION_INCOMING_CALL} containing a {@link Bundle} which contains
     * metadata about the call. This {@link Bundle} will be returned to the {@link CallService} as
     * part of {@link CallService#setIncomingCallId(String,Bundle)}.
     */
    public static final String EXTRA_INCOMING_CALL_EXTRAS =
            "android.intent.extra.INCOMING_CALL_EXTRAS";

    /**
     * Optional extra for {@link TelephonyManager#ACTION_PHONE_STATE_CHANGED} containing the
     * disconnect code.
     */
    public static final String EXTRA_CALL_DISCONNECT_CAUSE =
            "android.telecomm.extra.CALL_DISCONNECT_CAUSE";

    /**
     * Optional extra for {@link TelephonyManager#ACTION_PHONE_STATE_CHANGED} containing the
     * disconnect message.
     */
    public static final String EXTRA_CALL_DISCONNECT_MESSAGE =
            "android.telecomm.extra.CALL_DISCONNECT_MESSAGE";

    /**
     * The dual tone multi-frequency signaling character sent to indicate the dialing system should
     * pause for a predefined period.
     */
    public static final char DTMF_CHARACTER_PAUSE = ',';

    /**
     * The dual-tone multi-frequency signaling character sent to indicate the dialing system should
     * wait for user confirmation before proceeding.
     */
    public static final char DTMF_CHARACTER_WAIT = ';';
}
