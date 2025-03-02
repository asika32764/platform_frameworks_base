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

package android.media;

/**
 * The AudioDevicePort is a specialized type of AudioPort
 * describing an input (e.g microphone) or output device (e.g speaker)
 * of the system.
 * An AudioDevicePort is an AudioPort controlled by the audio HAL, almost always a physical
 * device at the boundary of the audio system.
 * In addition to base audio port attributes, the device descriptor contains:
 * - the device type (e.g AudioManager.DEVICE_OUT_SPEAKER)
 * - the device address (e.g MAC adddress for AD2P sink).
 * @see AudioPort
 * @hide
 */

public class AudioDevicePort extends AudioPort {

    private final int mType;
    private final String mAddress;

    AudioDevicePort(AudioHandle handle, int[] samplingRates, int[] channelMasks,
            int[] formats, AudioGain[] gains, int type, String address) {
        super(handle,
             (AudioManager.isInputDevice(type) == true)  ?
                        AudioPort.ROLE_SOURCE : AudioPort.ROLE_SINK,
             samplingRates, channelMasks, formats, gains);
        mType = type;
        mAddress = address;
    }

    /**
     * Get the device type (e.g AudioManager.DEVICE_OUT_SPEAKER)
     */
    public int type() {
        return mType;
    }

    /**
     * Get the device address. Address format varies with the device type.
     * - USB devices ({@link AudioManager#DEVICE_OUT_USB_DEVICE},
     * {@link AudioManager#DEVICE_IN_USB_DEVICE}) use an address composed of the ALSA card number
     * and device number: "card=2;device=1"
     * - Bluetooth devices ({@link AudioManager#DEVICE_OUT_BLUETOOTH_SCO},
     * {@link AudioManager#DEVICE_OUT_BLUETOOTH_SCO}, {@link AudioManager#DEVICE_OUT_BLUETOOTH_A2DP})
     * use the MAC address of the bluetooth device in the form "00:11:22:AA:BB:CC" as reported by
     * {@link BluetoothDevice#getAddress()}.
     * - Deivces that do not have an address will indicate an empty string "".
     */
    public String address() {
        return mAddress;
    }

    /**
     * Build a specific configuration of this audio device port for use by methods
     * like AudioManager.connectAudioPatch().
     */
    public AudioDevicePortConfig buildConfig(int samplingRate, int channelMask, int format,
                                          AudioGainConfig gain) {
        return new AudioDevicePortConfig(this, samplingRate, channelMask, format, gain);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof AudioDevicePort)) {
            return false;
        }
        return super.equals(o);
    }
}
