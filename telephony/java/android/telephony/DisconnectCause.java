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

package android.telephony;

/**
 * Contains disconnect call causes generated by the framework and the RIL.
 */
public class DisconnectCause {

    /** The disconnect cause is not valid (Not received a disconnect cause) */
    public static final int NOT_VALID                      = -1;
    /** Has not yet disconnected */
    public static final int NOT_DISCONNECTED               = 0;
    /** An incoming call that was missed and never answered */
    public static final int INCOMING_MISSED                = 1;
    /** Normal; Remote hangup*/
    public static final int NORMAL                         = 2;
    /** Normal; Local hangup */
    public static final int LOCAL                          = 3;
    /** Outgoing call to busy line */
    public static final int BUSY                           = 4;
    /** Outgoing call to congested network */
    public static final int CONGESTION                     = 5;
    /** Not presently used */
    public static final int MMI                            = 6;
    /** Invalid dial string */
    public static final int INVALID_NUMBER                 = 7;
    /** Cannot reach the peer */
    public static final int NUMBER_UNREACHABLE             = 8;
    /** Cannot reach the server */
    public static final int SERVER_UNREACHABLE             = 9;
    /** Invalid credentials */
    public static final int INVALID_CREDENTIALS            = 10;
    /** Calling from out of network is not allowed */
    public static final int OUT_OF_NETWORK                 = 11;
    /** Server error */
    public static final int SERVER_ERROR                   = 12;
    /** Client timed out */
    public static final int TIMED_OUT                      = 13;
    /** Client went out of network range */
    public static final int LOST_SIGNAL                    = 14;
    /** GSM or CDMA ACM limit exceeded */
    public static final int LIMIT_EXCEEDED                 = 15;
    /** An incoming call that was rejected */
    public static final int INCOMING_REJECTED              = 16;
    /** Radio is turned off explicitly */
    public static final int POWER_OFF                      = 17;
    /** Out of service */
    public static final int OUT_OF_SERVICE                 = 18;
    /** No ICC, ICC locked, or other ICC error */
    public static final int ICC_ERROR                      = 19;
    /** Call was blocked by call barring */
    public static final int CALL_BARRED                    = 20;
    /** Call was blocked by fixed dial number */
    public static final int FDN_BLOCKED                    = 21;
    /** Call was blocked by restricted all voice access */
    public static final int CS_RESTRICTED                  = 22;
    /** Call was blocked by restricted normal voice access */
    public static final int CS_RESTRICTED_NORMAL           = 23;
    /** Call was blocked by restricted emergency voice access */
    public static final int CS_RESTRICTED_EMERGENCY        = 24;
    /** Unassigned number */
    public static final int UNOBTAINABLE_NUMBER            = 25;
    /** MS is locked until next power cycle */
    public static final int CDMA_LOCKED_UNTIL_POWER_CYCLE  = 26;
    /** Drop call*/
    public static final int CDMA_DROP                      = 27;
    /** INTERCEPT order received, MS state idle entered */
    public static final int CDMA_INTERCEPT                 = 28;
    /** MS has been redirected, call is cancelled */
    public static final int CDMA_REORDER                   = 29;
    /** Service option rejection */
    public static final int CDMA_SO_REJECT                 = 30;
    /** Requested service is rejected, retry delay is set */
    public static final int CDMA_RETRY_ORDER               = 31;
    /** Unable to obtain access to the CDMA system */
    public static final int CDMA_ACCESS_FAILURE            = 32;
    /** Not a preempted call */
    public static final int CDMA_PREEMPTED                 = 33;
    /** Not an emergency call */
    public static final int CDMA_NOT_EMERGENCY             = 34;
    /** Access Blocked by CDMA network */
    public static final int CDMA_ACCESS_BLOCKED            = 35;
    /** Unknown error or not specified */
    public static final int ERROR_UNSPECIFIED              = 36;
    /**
     * Only emergency numbers are allowed, but we tried to dial
     * a non-emergency number.
     */
    // TODO: This should be the same as NOT_EMERGENCY
    public static final int EMERGENCY_ONLY                 = 37;
    /**
     * The supplied CALL Intent didn't contain a valid phone number.
     */
    public static final int NO_PHONE_NUMBER_SUPPLIED       = 38;
    /**
     * Our initial phone number was actually an MMI sequence.
     */
    public static final int DIALED_MMI                     = 39;
    /**
     * We tried to call a voicemail: URI but the device has no
     * voicemail number configured.
     */
    public static final int VOICEMAIL_NUMBER_MISSING       = 40;
    /**
     * This status indicates that InCallScreen should display the
     * CDMA-specific "call lost" dialog.  (If an outgoing call fails,
     * and the CDMA "auto-retry" feature is enabled, *and* the retried
     * call fails too, we display this specific dialog.)
     *
     * TODO: this is currently unused, since the "call lost" dialog
     * needs to be triggered by a *disconnect* event, rather than when
     * the InCallScreen first comes to the foreground.  For now we use
     * the needToShowCallLostDialog field for this (see below.)
     */
    public static final int CDMA_CALL_LOST                 = 41;
    /**
     * This status indicates that the call was placed successfully,
     * but additionally, the InCallScreen needs to display the
     * "Exiting ECM" dialog.
     *
     * (Details: "Emergency callback mode" is a CDMA-specific concept
     * where the phone disallows data connections over the cell
     * network for some period of time after you make an emergency
     * call.  If the phone is in ECM and you dial a non-emergency
     * number, that automatically *cancels* ECM, but we additionally
     * need to warn the user that ECM has been canceled (see bug
     * 4207607.))
     *
     * TODO: Rethink where the best place to put this is. It is not a notification
     * of a failure of the connection -- it is an additional message that accompanies
     * a successful connection giving the user important information about what happened.
     *
     * {@hide}
     */
    public static final int EXITED_ECM                     = 42;

    /** Smallest valid value for call disconnect codes. */
    public static final int MINIMUM_VALID_VALUE = NOT_DISCONNECTED;
    /** Largest valid value for call disconnect codes. */
    public static final int MAXIMUM_VALID_VALUE = EXITED_ECM;

    /** Private constructor to avoid class instantiation. */
    private DisconnectCause() {
        // Do nothing.
    }

    /** Returns descriptive string for the specified disconnect cause. */
    public static String toString(int cause) {
        switch (cause) {
        case NOT_DISCONNECTED:
            return "NOT_DISCONNECTED";
        case INCOMING_MISSED:
            return "INCOMING_MISSED";
        case NORMAL:
            return "NORMAL";
        case LOCAL:
            return "LOCAL";
        case BUSY:
            return "BUSY";
        case CONGESTION:
            return "CONGESTION";
        case INVALID_NUMBER:
            return "INVALID_NUMBER";
        case NUMBER_UNREACHABLE:
            return "NUMBER_UNREACHABLE";
        case SERVER_UNREACHABLE:
            return "SERVER_UNREACHABLE";
        case INVALID_CREDENTIALS:
            return "INVALID_CREDENTIALS";
        case OUT_OF_NETWORK:
            return "OUT_OF_NETWORK";
        case SERVER_ERROR:
            return "SERVER_ERROR";
        case TIMED_OUT:
            return "TIMED_OUT";
        case LOST_SIGNAL:
            return "LOST_SIGNAL";
        case LIMIT_EXCEEDED:
            return "LIMIT_EXCEEDED";
        case INCOMING_REJECTED:
            return "INCOMING_REJECTED";
        case POWER_OFF:
            return "POWER_OFF";
        case OUT_OF_SERVICE:
            return "OUT_OF_SERVICE";
        case ICC_ERROR:
            return "ICC_ERROR";
        case CALL_BARRED:
            return "CALL_BARRED";
        case FDN_BLOCKED:
            return "FDN_BLOCKED";
        case CS_RESTRICTED:
            return "CS_RESTRICTED";
        case CS_RESTRICTED_NORMAL:
            return "CS_RESTRICTED_NORMAL";
        case CS_RESTRICTED_EMERGENCY:
            return "CS_RESTRICTED_EMERGENCY";
        case UNOBTAINABLE_NUMBER:
            return "UNOBTAINABLE_NUMBER";
        case CDMA_LOCKED_UNTIL_POWER_CYCLE:
            return "CDMA_LOCKED_UNTIL_POWER_CYCLE";
        case CDMA_DROP:
            return "CDMA_DROP";
        case CDMA_INTERCEPT:
            return "CDMA_INTERCEPT";
        case CDMA_REORDER:
            return "CDMA_REORDER";
        case CDMA_SO_REJECT:
            return "CDMA_SO_REJECT";
        case CDMA_RETRY_ORDER:
            return "CDMA_RETRY_ORDER";
        case CDMA_ACCESS_FAILURE:
            return "CDMA_ACCESS_FAILURE";
        case CDMA_PREEMPTED:
            return "CDMA_PREEMPTED";
        case CDMA_NOT_EMERGENCY:
            return "CDMA_NOT_EMERGENCY";
        case CDMA_ACCESS_BLOCKED:
            return "CDMA_ACCESS_BLOCKED";
        case EMERGENCY_ONLY:
            return "EMERGENCY_ONLY";
        case NO_PHONE_NUMBER_SUPPLIED:
            return "NO_PHONE_NUMBER_SUPPLIED";
        case DIALED_MMI:
            return "DIALED_MMI";
        case VOICEMAIL_NUMBER_MISSING:
            return "VOICEMAIL_NUMBER_MISSING";
        case CDMA_CALL_LOST:
            return "CDMA_CALL_LOST";
        case EXITED_ECM:
            return "EXITED_ECM";
        case ERROR_UNSPECIFIED:
            return "ERROR_UNSPECIFIED";
        default:
            return "INVALID";
        }
    }
}
