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

/**
 * Defines call-state constants of the different states in which a call can exist. Although states
 * have the notion of normal transitions, due to the volatile nature of telephony systems, code
 * that uses these states should be resilient to unexpected state changes outside of what is
 * considered traditional.
 */
public enum CallState {
    /**
     * Indicates that a call is new and not connected. This is used as the default state internally
     * within Telecomm and should not be used between Telecomm and call services. Call services are
     * not expected to ever interact with NEW calls, but {@link InCallService}s will see calls in
     * this state.
     */
    NEW,

    /**
     * Indicates that a call is outgoing and in the dialing state. A call transitions to this state
     * once an outgoing call has begun (e.g., user presses the dial button in Dialer). Calls in this
     * state usually transition to {@link #ACTIVE} if the call was answered or {@link #DISCONNECTED}
     * if the call was disconnected somehow (e.g., failure or cancellation of the call by the user).
     */
    DIALING,

    /**
     * Indicates that a call is incoming and the user still has the option of answering, rejecting,
     * or doing nothing with the call. This state is usually associated with some type of audible
     * ringtone. Normal transitions are to {@link #ACTIVE} if answered or {@link #DISCONNECTED}
     * otherwise.
     */
    RINGING,

    /**
     * Indicates that the call is active but in a "post-dial" state where Telecomm is now sending
     * some dual-tone multi-frequency signaling (DTMF) tones appended to the dialed number. Normal
     * transitions are to {@link #POST_DIAL_WAIT} when the post-dial string requires user
     * confirmation to proceed, {@link #ACTIVE} when the post-dial tones are completed, or
     * {@link #DISCONNECTED}.
     */
    POST_DIAL,

    /**
     * Indicates that the call was in the {@link #POST_DIAL} state but is now waiting for user
     * confirmation before the remaining digits can be sent. Normal transitions are to
     * {@link #POST_DIAL} when the user asks Telecomm to proceed with the post-dial sequence.
     */
    POST_DIAL_WAIT,

    /**
     * Indicates that a call is currently connected to another party and a communication channel is
     * open between them. The normal transition to this state is by the user answering a
     * {@link #DIALING} call or a {@link #RINGING} call being answered by the other party.
     */
    ACTIVE,

    /**
     * Indicates that the call is currently on hold. In this state, the call is not terminated
     * but no communication is allowed until the call is no longer on hold. The typical transition
     * to this state is by the user putting an {@link #ACTIVE} call on hold by explicitly performing
     * an action, such as clicking the hold button.
     */
    ON_HOLD,

    /**
     * Indicates that a call is currently disconnected. All states can transition to this state
     * by the call service giving notice that the connection has been severed. When the user
     * explicitly ends a call, it will not transition to this state until the call service confirms
     * the disconnection or communication was lost to the call service currently responsible for
     * this call (e.g., call service crashes).
     */
    DISCONNECTED,

    /**
     * Indicates that the call was attempted (mostly in the context of outgoing, at least at the
     * time of writing) but cancelled before it was successfully connected.
     * @hide
     */
    ABORTED;
}
