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

package android.hardware.hdmi;

import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import libcore.util.EmptyArray;

import java.util.Arrays;

/**
 * A class to encapsulate HDMI-CEC message used for the devices connected via
 * HDMI cable to communicate with one another. A message is defined by its
 * source and destination address, command (or opcode), and optional parameters.
 *
 * @hide
 */
@SystemApi
public final class HdmiCecMessage implements Parcelable {

    public static final byte[] EMPTY_PARAM = EmptyArray.BYTE;

    private static final int MAX_MESSAGE_LENGTH = 16;

    private final int mSource;
    private final int mDestination;

    private final int mOpcode;
    private final byte[] mParams;

    /**
     * Constructor.
     */
    public HdmiCecMessage(int source, int destination, int opcode, byte[] params) {
        mSource = source;
        mDestination = destination;
        mOpcode = opcode & 0xFF;
        mParams = Arrays.copyOf(params, params.length);
    }

    /**
     * Return the source address field of the message. It is the logical address
     * of the device which generated the message.
     *
     * @return source address
     */
    public int getSource() {
        return mSource;
    }

    /**
     * Return the destination address field of the message. It is the logical address
     * of the device to which the message is sent.
     *
     * @return destination address
     */
    public int getDestination() {
        return mDestination;
    }

    /**
     * Return the opcode field of the message. It is the type of the message that
     * tells the destination device what to do.
     *
     * @return opcode
     */
    public int getOpcode() {
        return mOpcode;
    }

    /**
     * Return the parameter field of the message. The contents of parameter varies
     * from opcode to opcode, and is used together with opcode to describe
     * the action for the destination device to take.
     *
     * @return parameter
     */
    public byte[] getParams() {
        return mParams;
    }

    /**
     * Describe the kinds of special objects contained in this Parcelable's
     * marshalled representation.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flatten this object in to a Parcel.
     *
     * @param dest The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     *        May be 0 or {@link Parcelable#PARCELABLE_WRITE_RETURN_VALUE}.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSource);
        dest.writeInt(mDestination);
        dest.writeInt(mOpcode);
        dest.writeInt(mParams.length);
        dest.writeByteArray(mParams);
    }

    public static final Parcelable.Creator<HdmiCecMessage> CREATOR
            = new Parcelable.Creator<HdmiCecMessage>() {
        /**
         * Rebuild a HdmiCecMessage previously stored with writeToParcel().
         * @param p HdmiCecMessage object to read the Rating from
         * @return a new HdmiCecMessage created from the data in the parcel
         */
        @Override
        public HdmiCecMessage createFromParcel(Parcel p) {
            int source = p.readInt();
            int destination = p.readInt();
            int opcode = p.readInt();
            byte[] params = new byte[p.readInt()];
            p.readByteArray(params);
            return new HdmiCecMessage(source, destination, opcode, params);
        }
        @Override
        public HdmiCecMessage[] newArray(int size) {
            return new HdmiCecMessage[size];
        }
    };

    @Override
    public String toString() {
        StringBuffer s = new StringBuffer();
        s.append(String.format("<%s> src: %d, dst: %d",
                opcodeToString(mOpcode), mSource, mDestination));
        if (mParams.length > 0) {
            s.append(", params:");
            for (byte data : mParams) {
                s.append(String.format(" %02X", data));
            }
        }
        return s.toString();
    }

    private static String opcodeToString(int opcode) {
        switch (opcode) {
            case HdmiCec.MESSAGE_FEATURE_ABORT:
                return "Feature Abort";
            case HdmiCec.MESSAGE_CEC_VERSION:
                return "CEC Version";
            case HdmiCec.MESSAGE_REQUEST_ARC_INITIATION:
                return "Request ARC Initiation";
            case HdmiCec.MESSAGE_REQUEST_ARC_TERMINATION:
                return "Request ARC Termination";
            case HdmiCec.MESSAGE_REPORT_ARC_INITIATED:
                return "Report ARC Initiated";
            case HdmiCec.MESSAGE_REPORT_ARC_TERMINATED:
                return "Report ARC Terminated";
            case HdmiCec.MESSAGE_TEXT_VIEW_ON:
                return "Text View On";
            case HdmiCec.MESSAGE_ACTIVE_SOURCE:
                return "Active Source";
            case HdmiCec.MESSAGE_GIVE_DEVICE_POWER_STATUS:
                return "Give Device Power Status";
            default:
                return String.format("Opcode: %02X", opcode);
        }
    }
}

