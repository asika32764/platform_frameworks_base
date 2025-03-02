/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telecomm;

/** Defines actions a call currently supports. */
public final class CallCapabilities {
    /** Call can currently be put on hold or unheld. */
    public static final int HOLD               = 0x00000001;

    /** Call supports the hold feature. */
    public static final int SUPPORT_HOLD       = 0x00000002;

    /** Call can currently be merged. */
    public static final int MERGE_CALLS        = 0x00000004;

    /** Call can currently be swapped with another call. */
    public static final int SWAP_CALLS         = 0x00000008;

    /** Call currently supports adding another call to this one. */
    public static final int ADD_CALL           = 0x00000010;

    /** Call supports responding via text option. */
    public static final int RESPOND_VIA_TEXT   = 0x00000020;

    /** Call can be muted. */
    public static final int MUTE               = 0x00000040;

    /** Call supports generic conference mode. */
    public static final int GENERIC_CONFERENCE = 0x00000080;

    /** Call currently supports switch between connections. */
    public static final int CONNECTION_HANDOFF = 0x00000100;

    public static final int ALL = HOLD | SUPPORT_HOLD | MERGE_CALLS | SWAP_CALLS | ADD_CALL
            | RESPOND_VIA_TEXT | MUTE | GENERIC_CONFERENCE | CONNECTION_HANDOFF;

    public static String toString(int capabilities) {
        StringBuilder builder = new StringBuilder();
        builder.append("[Capabilities:");
        if ((capabilities & HOLD) != 0) {
            builder.append(" HOLD");
        }
        if ((capabilities & SUPPORT_HOLD) != 0) {
            builder.append(" SUPPORT_HOLD");
        }
        if ((capabilities & MERGE_CALLS) != 0) {
            builder.append(" MERGE_CALLS");
        }
        if ((capabilities & SWAP_CALLS) != 0) {
            builder.append(" SWAP_CALLS");
        }
        if ((capabilities & ADD_CALL) != 0) {
            builder.append(" ADD_CALL");
        }
        if ((capabilities & RESPOND_VIA_TEXT) != 0) {
            builder.append(" RESPOND_VIA_TEXT");
        }
        if ((capabilities & MUTE) != 0) {
            builder.append(" MUTE");
        }
        if ((capabilities & GENERIC_CONFERENCE) != 0) {
            builder.append(" GENERIC_CONFERENCE");
        }
        if ((capabilities & CONNECTION_HANDOFF) != 0) {
            builder.append(" HANDOFF");
        }
        builder.append("]");
        return builder.toString();
    }

    private CallCapabilities() {}
}
