/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.hardware.camera2;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The base class for camera controls and information.
 *
 * <p>
 * This class defines the basic key/value map used for querying for camera
 * characteristics or capture results, and for setting camera request
 * parameters.
 * </p>
 *
 * <p>
 * All instances of CameraMetadata are immutable. The list of keys with {@link #getKeys()}
 * never changes, nor do the values returned by any key with {@code #get} throughout
 * the lifetime of the object.
 * </p>
 *
 * @see CameraDevice
 * @see CameraManager
 * @see CameraCharacteristics
 **/
public abstract class CameraMetadata<TKey> {

    private static final String TAG = "CameraMetadataAb";
    private static final boolean VERBOSE = false;

    /**
     * Set a camera metadata field to a value. The field definitions can be
     * found in {@link CameraCharacteristics}, {@link CaptureResult}, and
     * {@link CaptureRequest}.
     *
     * @param key The metadata field to write.
     * @param value The value to set the field to, which must be of a matching
     * type to the key.
     *
     * @hide
     */
    protected CameraMetadata() {
    }

    /**
     * Get a camera metadata field value.
     *
     * <p>The field definitions can be
     * found in {@link CameraCharacteristics}, {@link CaptureResult}, and
     * {@link CaptureRequest}.</p>
     *
     * <p>Querying the value for the same key more than once will return a value
     * which is equal to the previous queried value.</p>
     *
     * @throws IllegalArgumentException if the key was not valid
     *
     * @param key The metadata field to read.
     * @return The value of that key, or {@code null} if the field is not set.
     *
     * @hide
     */
     protected abstract <T> T getProtected(TKey key);

     /**
      * @hide
      */
     protected abstract Class<TKey> getKeyClass();

    /**
     * Returns a list of the keys contained in this map.
     *
     * <p>The list returned is not modifiable, so any attempts to modify it will throw
     * a {@code UnsupportedOperationException}.</p>
     *
     * <p>All values retrieved by a key from this list with {@code #get} are guaranteed to be
     * non-{@code null}. Each key is only listed once in the list. The order of the keys
     * is undefined.</p>
     *
     * @return List of the keys contained in this map.
     */
    @SuppressWarnings("unchecked")
    public List<TKey> getKeys() {
        Class<CameraMetadata<TKey>> thisClass = (Class<CameraMetadata<TKey>>) getClass();
        return Collections.unmodifiableList(getKeysStatic(thisClass, getKeyClass(), this));
    }

    /**
     * Return a list of all the Key<?> that are declared as a field inside of the class
     * {@code type}.
     *
     * <p>
     * Optionally, if {@code instance} is not null, then filter out any keys with null values.
     * </p>
     */
     /*package*/ @SuppressWarnings("unchecked")
    static <TKey> ArrayList<TKey> getKeysStatic(
             Class<?> type, Class<TKey> keyClass,
             CameraMetadata<TKey> instance) {

        if (VERBOSE) Log.v(TAG, "getKeysStatic for " + type);

        ArrayList<TKey> keyList = new ArrayList<TKey>();

        Field[] fields = type.getDeclaredFields();
        for (Field field : fields) {
            // Filter for Keys that are public
            if (field.getType().isAssignableFrom(keyClass) &&
                    (field.getModifiers() & Modifier.PUBLIC) != 0) {

                TKey key;
                try {
                    key = (TKey) field.get(instance);
                } catch (IllegalAccessException e) {
                    throw new AssertionError("Can't get IllegalAccessException", e);
                } catch (IllegalArgumentException e) {
                    throw new AssertionError("Can't get IllegalArgumentException", e);
                }

                if (instance == null || instance.getProtected(key) != null) {
                    keyList.add(key);
                }
            }
        }

        return keyList;
    }

    /*@O~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~
     * The enum values below this point are generated from metadata
     * definitions in /system/media/camera/docs. Do not modify by hand or
     * modify the comment blocks at the start or end.
     *~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~*/

    //
    // Enumeration values for CameraCharacteristics#LENS_INFO_FOCUS_DISTANCE_CALIBRATION
    //

    /**
     * <p>The lens focus distance is not accurate, and the units used for
     * {@link CaptureRequest#LENS_FOCUS_DISTANCE android.lens.focusDistance} do not correspond to any physical units.</p>
     * <p>Setting the lens to the same focus distance on separate occasions may
     * result in a different real focus distance, depending on factors such
     * as the orientation of the device, the age of the focusing mechanism,
     * and the device temperature. The focus distance value will still be
     * in the range of <code>[0, {@link CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE android.lens.info.minimumFocusDistance}]</code>, where 0
     * represents the farthest focus.</p>
     *
     * @see CaptureRequest#LENS_FOCUS_DISTANCE
     * @see CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE
     * @see CameraCharacteristics#LENS_INFO_FOCUS_DISTANCE_CALIBRATION
     */
    public static final int LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED = 0;

    /**
     * <p>The lens focus distance is measured in diopters.</p>
     * <p>However, setting the lens to the same focus distance
     * on separate occasions may result in a different real
     * focus distance, depending on factors such as the
     * orientation of the device, the age of the focusing
     * mechanism, and the device temperature.</p>
     * @see CameraCharacteristics#LENS_INFO_FOCUS_DISTANCE_CALIBRATION
     */
    public static final int LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE = 1;

    /**
     * <p>The lens focus distance is measured in diopters, and
     * is calibrated.</p>
     * <p>The lens mechanism is calibrated so that setting the
     * same focus distance is repeatable on multiple
     * occasions with good accuracy, and the focus distance
     * corresponds to the real physical distance to the plane
     * of best focus.</p>
     * @see CameraCharacteristics#LENS_INFO_FOCUS_DISTANCE_CALIBRATION
     */
    public static final int LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED = 2;

    //
    // Enumeration values for CameraCharacteristics#LENS_FACING
    //

    /**
     * <p>The camera device faces the same direction as the device's screen.</p>
     * @see CameraCharacteristics#LENS_FACING
     */
    public static final int LENS_FACING_FRONT = 0;

    /**
     * <p>The camera device faces the opposite direction as the device's screen.</p>
     * @see CameraCharacteristics#LENS_FACING
     */
    public static final int LENS_FACING_BACK = 1;

    //
    // Enumeration values for CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES
    //

    /**
     * <p>The minimal set of capabilities that every camera
     * device (regardless of {@link CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL android.info.supportedHardwareLevel})
     * will support.</p>
     * <p>The full set of features supported by this capability makes
     * the camera2 api backwards compatible with the camera1
     * (android.hardware.Camera) API.</p>
     *
     * @see CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL
     * @see CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES
     * @hide
     */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE = 0;

    /**
     * <p>This is a catch-all capability to include all other
     * tags or functionality not encapsulated by one of the other
     * capabilities.</p>
     * <p>A typical example is all tags marked 'optional'.</p>
     * @see CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES
     * @hide
     */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_OPTIONAL = 1;

    /**
     * <p>The camera device can be manually controlled (3A algorithms such
     * as auto-exposure, and auto-focus can be bypassed).
     * The camera device supports basic manual control of the sensor image
     * acquisition related stages. This means the following controls are
     * guaranteed to be supported:</p>
     * <ul>
     * <li>Manual frame duration control<ul>
     * <li>{@link CaptureRequest#SENSOR_FRAME_DURATION android.sensor.frameDuration}</li>
     * <li>{@link CameraCharacteristics#SENSOR_INFO_MAX_FRAME_DURATION android.sensor.info.maxFrameDuration}</li>
     * <li>{@link CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP android.scaler.streamConfigurationMap}</li>
     * </ul>
     * </li>
     * <li>Manual exposure control<ul>
     * <li>{@link CaptureRequest#SENSOR_EXPOSURE_TIME android.sensor.exposureTime}</li>
     * <li>{@link CameraCharacteristics#SENSOR_INFO_EXPOSURE_TIME_RANGE android.sensor.info.exposureTimeRange}</li>
     * </ul>
     * </li>
     * <li>Manual sensitivity control<ul>
     * <li>{@link CaptureRequest#SENSOR_SENSITIVITY android.sensor.sensitivity}</li>
     * <li>{@link CameraCharacteristics#SENSOR_INFO_SENSITIVITY_RANGE android.sensor.info.sensitivityRange}</li>
     * </ul>
     * </li>
     * <li>Manual lens control (if the lens is adjustable)<ul>
     * <li>android.lens.*</li>
     * </ul>
     * </li>
     * <li>Manual flash control (if a flash unit is present)<ul>
     * <li>android.flash.*</li>
     * </ul>
     * </li>
     * <li>Manual black level locking<ul>
     * <li>{@link CaptureRequest#BLACK_LEVEL_LOCK android.blackLevel.lock}</li>
     * </ul>
     * </li>
     * </ul>
     * <p>If any of the above 3A algorithms are enabled, then the camera
     * device will accurately report the values applied by 3A in the
     * result.</p>
     * <p>A given camera device may also support additional manual sensor controls,
     * but this capability only covers the above list of controls.</p>
     *
     * @see CaptureRequest#BLACK_LEVEL_LOCK
     * @see CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP
     * @see CaptureRequest#SENSOR_EXPOSURE_TIME
     * @see CaptureRequest#SENSOR_FRAME_DURATION
     * @see CameraCharacteristics#SENSOR_INFO_EXPOSURE_TIME_RANGE
     * @see CameraCharacteristics#SENSOR_INFO_MAX_FRAME_DURATION
     * @see CameraCharacteristics#SENSOR_INFO_SENSITIVITY_RANGE
     * @see CaptureRequest#SENSOR_SENSITIVITY
     * @see CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES
     */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR = 2;

    /**
     * <p>The camera device post-processing stages can be manually controlled.
     * The camera device supports basic manual control of the image post-processing
     * stages. This means the following controls are guaranteed to be supported:</p>
     * <ul>
     * <li>Manual tonemap control<ul>
     * <li>{@link CaptureRequest#TONEMAP_CURVE android.tonemap.curve}</li>
     * <li>{@link CaptureRequest#TONEMAP_MODE android.tonemap.mode}</li>
     * <li>{@link CameraCharacteristics#TONEMAP_MAX_CURVE_POINTS android.tonemap.maxCurvePoints}</li>
     * </ul>
     * </li>
     * <li>Manual white balance control<ul>
     * <li>{@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}</li>
     * <li>{@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains}</li>
     * </ul>
     * </li>
     * <li>Lens shading map information<ul>
     * <li>android.statistics.lensShadingMap</li>
     * <li>android.lens.info.shadingMapSize</li>
     * </ul>
     * </li>
     * </ul>
     * <p>If auto white balance is enabled, then the camera device
     * will accurately report the values applied by AWB in the result.</p>
     * <p>A given camera device may also support additional post-processing
     * controls, but this capability only covers the above list of controls.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#TONEMAP_CURVE
     * @see CameraCharacteristics#TONEMAP_MAX_CURVE_POINTS
     * @see CaptureRequest#TONEMAP_MODE
     * @see CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES
     */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING = 3;

    /**
     * <p>The camera device supports the Zero Shutter Lag use case.</p>
     * <ul>
     * <li>At least one input stream can be used.</li>
     * <li>RAW_OPAQUE is supported as an output/input format</li>
     * <li>Using RAW_OPAQUE does not cause a frame rate drop
     * relative to the sensor's maximum capture rate (at that
     * resolution).</li>
     * <li>RAW_OPAQUE will be reprocessable into both YUV_420_888
     * and JPEG formats.</li>
     * <li>The maximum available resolution for RAW_OPAQUE streams
     * (both input/output) will match the maximum available
     * resolution of JPEG streams.</li>
     * </ul>
     * @see CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES
     * @hide
     */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_ZSL = 4;

    /**
     * <p>The camera device supports outputting RAW buffers that can be
     * saved offline into a DNG format. It can reprocess DNG
     * files (produced from the same camera device) back into YUV.</p>
     * <ul>
     * <li>At least one input stream can be used.</li>
     * <li>RAW16 is supported as output/input format.</li>
     * <li>RAW16 is reprocessable into both YUV_420_888 and JPEG
     * formats.</li>
     * <li>The maximum available resolution for RAW16 streams (both
     * input/output) will match either the value in
     * {@link CameraCharacteristics#SENSOR_INFO_PIXEL_ARRAY_SIZE android.sensor.info.pixelArraySize} or
     * {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.</li>
     * <li>All DNG-related optional metadata entries are provided
     * by the camera device.</li>
     * </ul>
     *
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     * @see CameraCharacteristics#SENSOR_INFO_PIXEL_ARRAY_SIZE
     * @see CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES
     */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_DNG = 5;

    //
    // Enumeration values for CameraCharacteristics#SCALER_CROPPING_TYPE
    //

    /**
     * <p>The camera device only supports centered crop regions.</p>
     * @see CameraCharacteristics#SCALER_CROPPING_TYPE
     */
    public static final int SCALER_CROPPING_TYPE_CENTER_ONLY = 0;

    /**
     * <p>The camera device supports arbitrarily chosen crop regions.</p>
     * @see CameraCharacteristics#SCALER_CROPPING_TYPE
     */
    public static final int SCALER_CROPPING_TYPE_FREEFORM = 1;

    //
    // Enumeration values for CameraCharacteristics#SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
    //

    /**
     * @see CameraCharacteristics#SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
     */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB = 0;

    /**
     * @see CameraCharacteristics#SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
     */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG = 1;

    /**
     * @see CameraCharacteristics#SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
     */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG = 2;

    /**
     * @see CameraCharacteristics#SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
     */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR = 3;

    /**
     * <p>Sensor is not Bayer; output has 3 16-bit
     * values for each pixel, instead of just 1 16-bit value
     * per pixel.</p>
     * @see CameraCharacteristics#SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
     */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB = 4;

    //
    // Enumeration values for CameraCharacteristics#SENSOR_INFO_TIMESTAMP_CALIBRATION
    //

    /**
     * <p>Timestamps from {@link CaptureResult#SENSOR_TIMESTAMP android.sensor.timestamp} are in nanoseconds and monotonic,
     * but can not be compared to timestamps from other subsystems
     * (e.g. accelerometer, gyro etc.), or other instances of the same or different
     * camera devices in the same system. Timestamps between streams and results for
     * a single camera instance are comparable, and the timestamps for all buffers
     * and the result metadata generated by a single capture are identical.</p>
     *
     * @see CaptureResult#SENSOR_TIMESTAMP
     * @see CameraCharacteristics#SENSOR_INFO_TIMESTAMP_CALIBRATION
     */
    public static final int SENSOR_INFO_TIMESTAMP_CALIBRATION_UNCALIBRATED = 0;

    /**
     * <p>Timestamps from {@link CaptureResult#SENSOR_TIMESTAMP android.sensor.timestamp} are in the same timebase as
     * android.os.SystemClock#elapsedRealtimeNanos(),
     * and they can be compared to other timestamps using that base.</p>
     *
     * @see CaptureResult#SENSOR_TIMESTAMP
     * @see CameraCharacteristics#SENSOR_INFO_TIMESTAMP_CALIBRATION
     */
    public static final int SENSOR_INFO_TIMESTAMP_CALIBRATION_CALIBRATED = 1;

    //
    // Enumeration values for CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
    //

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT = 1;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT = 2;

    /**
     * <p>Incandescent light</p>
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN = 3;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_FLASH = 4;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER = 9;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER = 10;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_SHADE = 11;

    /**
     * <p>D 5700 - 7100K</p>
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT = 12;

    /**
     * <p>N 4600 - 5400K</p>
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT = 13;

    /**
     * <p>W 3900 - 4500K</p>
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT = 14;

    /**
     * <p>WW 3200 - 3700K</p>
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT = 15;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A = 17;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B = 18;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C = 19;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_D55 = 20;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_D65 = 21;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_D75 = 22;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_D50 = 23;

    /**
     * @see CameraCharacteristics#SENSOR_REFERENCE_ILLUMINANT1
     */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN = 24;

    //
    // Enumeration values for CameraCharacteristics#LED_AVAILABLE_LEDS
    //

    /**
     * <p>android.led.transmit control is used.</p>
     * @see CameraCharacteristics#LED_AVAILABLE_LEDS
     * @hide
     */
    public static final int LED_AVAILABLE_LEDS_TRANSMIT = 0;

    //
    // Enumeration values for CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL
    //

    /**
     * <p>This camera device has only limited capabilities.</p>
     * @see CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL
     */
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED = 0;

    /**
     * <p>This camera device is capable of supporting advanced imaging
     * applications.</p>
     * @see CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL
     */
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_FULL = 1;

    //
    // Enumeration values for CameraCharacteristics#SYNC_MAX_LATENCY
    //

    /**
     * <p>Every frame has the requests immediately applied.</p>
     * <p>Furthermore for all results,
     * <code>android.sync.frameNumber == android.request.frameCount</code></p>
     * <p>Changing controls over multiple requests one after another will
     * produce results that have those controls applied atomically
     * each frame.</p>
     * <p>All FULL capability devices will have this as their maxLatency.</p>
     * @see CameraCharacteristics#SYNC_MAX_LATENCY
     */
    public static final int SYNC_MAX_LATENCY_PER_FRAME_CONTROL = 0;

    /**
     * <p>Each new frame has some subset (potentially the entire set)
     * of the past requests applied to the camera settings.</p>
     * <p>By submitting a series of identical requests, the camera device
     * will eventually have the camera settings applied, but it is
     * unknown when that exact point will be.</p>
     * @see CameraCharacteristics#SYNC_MAX_LATENCY
     */
    public static final int SYNC_MAX_LATENCY_UNKNOWN = -1;

    //
    // Enumeration values for CaptureRequest#COLOR_CORRECTION_MODE
    //

    /**
     * <p>Use the {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform} matrix
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} to do color conversion.</p>
     * <p>All advanced white balance adjustments (not specified
     * by our white balance pipeline) must be disabled.</p>
     * <p>If AWB is enabled with <code>{@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode} != OFF</code>, then
     * TRANSFORM_MATRIX is ignored. The camera device will override
     * this value to either FAST or HIGH_QUALITY.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     * @see CaptureRequest#COLOR_CORRECTION_MODE
     */
    public static final int COLOR_CORRECTION_MODE_TRANSFORM_MATRIX = 0;

    /**
     * <p>Color correction processing must not slow down
     * capture rate relative to sensor raw output.</p>
     * <p>Advanced white balance adjustments above and beyond
     * the specified white balance pipeline may be applied.</p>
     * <p>If AWB is enabled with <code>{@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode} != OFF</code>, then
     * the camera device uses the last frame's AWB values
     * (or defaults if AWB has never been run).</p>
     *
     * @see CaptureRequest#CONTROL_AWB_MODE
     * @see CaptureRequest#COLOR_CORRECTION_MODE
     */
    public static final int COLOR_CORRECTION_MODE_FAST = 1;

    /**
     * <p>Color correction processing operates at improved
     * quality but reduced capture rate (relative to sensor raw
     * output).</p>
     * <p>Advanced white balance adjustments above and beyond
     * the specified white balance pipeline may be applied.</p>
     * <p>If AWB is enabled with <code>{@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode} != OFF</code>, then
     * the camera device uses the last frame's AWB values
     * (or defaults if AWB has never been run).</p>
     *
     * @see CaptureRequest#CONTROL_AWB_MODE
     * @see CaptureRequest#COLOR_CORRECTION_MODE
     */
    public static final int COLOR_CORRECTION_MODE_HIGH_QUALITY = 2;

    //
    // Enumeration values for CaptureRequest#CONTROL_AE_ANTIBANDING_MODE
    //

    /**
     * <p>The camera device will not adjust exposure duration to
     * avoid banding problems.</p>
     * @see CaptureRequest#CONTROL_AE_ANTIBANDING_MODE
     */
    public static final int CONTROL_AE_ANTIBANDING_MODE_OFF = 0;

    /**
     * <p>The camera device will adjust exposure duration to
     * avoid banding problems with 50Hz illumination sources.</p>
     * @see CaptureRequest#CONTROL_AE_ANTIBANDING_MODE
     */
    public static final int CONTROL_AE_ANTIBANDING_MODE_50HZ = 1;

    /**
     * <p>The camera device will adjust exposure duration to
     * avoid banding problems with 60Hz illumination
     * sources.</p>
     * @see CaptureRequest#CONTROL_AE_ANTIBANDING_MODE
     */
    public static final int CONTROL_AE_ANTIBANDING_MODE_60HZ = 2;

    /**
     * <p>The camera device will automatically adapt its
     * antibanding routine to the current illumination
     * conditions. This is the default.</p>
     * @see CaptureRequest#CONTROL_AE_ANTIBANDING_MODE
     */
    public static final int CONTROL_AE_ANTIBANDING_MODE_AUTO = 3;

    //
    // Enumeration values for CaptureRequest#CONTROL_AE_MODE
    //

    /**
     * <p>The camera device's autoexposure routine is disabled.</p>
     * <p>The application-selected {@link CaptureRequest#SENSOR_EXPOSURE_TIME android.sensor.exposureTime},
     * {@link CaptureRequest#SENSOR_SENSITIVITY android.sensor.sensitivity} and
     * {@link CaptureRequest#SENSOR_FRAME_DURATION android.sensor.frameDuration} are used by the camera
     * device, along with android.flash.* fields, if there's
     * a flash unit for this camera device.</p>
     *
     * @see CaptureRequest#SENSOR_EXPOSURE_TIME
     * @see CaptureRequest#SENSOR_FRAME_DURATION
     * @see CaptureRequest#SENSOR_SENSITIVITY
     * @see CaptureRequest#CONTROL_AE_MODE
     */
    public static final int CONTROL_AE_MODE_OFF = 0;

    /**
     * <p>The camera device's autoexposure routine is active,
     * with no flash control.</p>
     * <p>The application's values for
     * {@link CaptureRequest#SENSOR_EXPOSURE_TIME android.sensor.exposureTime},
     * {@link CaptureRequest#SENSOR_SENSITIVITY android.sensor.sensitivity}, and
     * {@link CaptureRequest#SENSOR_FRAME_DURATION android.sensor.frameDuration} are ignored. The
     * application has control over the various
     * android.flash.* fields.</p>
     *
     * @see CaptureRequest#SENSOR_EXPOSURE_TIME
     * @see CaptureRequest#SENSOR_FRAME_DURATION
     * @see CaptureRequest#SENSOR_SENSITIVITY
     * @see CaptureRequest#CONTROL_AE_MODE
     */
    public static final int CONTROL_AE_MODE_ON = 1;

    /**
     * <p>Like ON, except that the camera device also controls
     * the camera's flash unit, firing it in low-light
     * conditions.</p>
     * <p>The flash may be fired during a precapture sequence
     * (triggered by {@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER android.control.aePrecaptureTrigger}) and
     * may be fired for captures for which the
     * {@link CaptureRequest#CONTROL_CAPTURE_INTENT android.control.captureIntent} field is set to
     * STILL_CAPTURE</p>
     *
     * @see CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     * @see CaptureRequest#CONTROL_AE_MODE
     */
    public static final int CONTROL_AE_MODE_ON_AUTO_FLASH = 2;

    /**
     * <p>Like ON, except that the camera device also controls
     * the camera's flash unit, always firing it for still
     * captures.</p>
     * <p>The flash may be fired during a precapture sequence
     * (triggered by {@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER android.control.aePrecaptureTrigger}) and
     * will always be fired for captures for which the
     * {@link CaptureRequest#CONTROL_CAPTURE_INTENT android.control.captureIntent} field is set to
     * STILL_CAPTURE</p>
     *
     * @see CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     * @see CaptureRequest#CONTROL_AE_MODE
     */
    public static final int CONTROL_AE_MODE_ON_ALWAYS_FLASH = 3;

    /**
     * <p>Like ON_AUTO_FLASH, but with automatic red eye
     * reduction.</p>
     * <p>If deemed necessary by the camera device, a red eye
     * reduction flash will fire during the precapture
     * sequence.</p>
     * @see CaptureRequest#CONTROL_AE_MODE
     */
    public static final int CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE = 4;

    //
    // Enumeration values for CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
    //

    /**
     * <p>The trigger is idle.</p>
     * @see CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
     */
    public static final int CONTROL_AE_PRECAPTURE_TRIGGER_IDLE = 0;

    /**
     * <p>The precapture metering sequence will be started
     * by the camera device.</p>
     * <p>The exact effect of the precapture trigger depends on
     * the current AE mode and state.</p>
     * @see CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
     */
    public static final int CONTROL_AE_PRECAPTURE_TRIGGER_START = 1;

    //
    // Enumeration values for CaptureRequest#CONTROL_AF_MODE
    //

    /**
     * <p>The auto-focus routine does not control the lens;
     * {@link CaptureRequest#LENS_FOCUS_DISTANCE android.lens.focusDistance} is controlled by the
     * application.</p>
     *
     * @see CaptureRequest#LENS_FOCUS_DISTANCE
     * @see CaptureRequest#CONTROL_AF_MODE
     */
    public static final int CONTROL_AF_MODE_OFF = 0;

    /**
     * <p>Basic automatic focus mode.</p>
     * <p>In this mode, the lens does not move unless
     * the autofocus trigger action is called. When that trigger
     * is activated, AF will transition to ACTIVE_SCAN, then to
     * the outcome of the scan (FOCUSED or NOT_FOCUSED).</p>
     * <p>Always supported if lens is not fixed focus.</p>
     * <p>Use {@link CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE android.lens.info.minimumFocusDistance} to determine if lens
     * is fixed-focus.</p>
     * <p>Triggering AF_CANCEL resets the lens position to default,
     * and sets the AF state to INACTIVE.</p>
     *
     * @see CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE
     * @see CaptureRequest#CONTROL_AF_MODE
     */
    public static final int CONTROL_AF_MODE_AUTO = 1;

    /**
     * <p>Close-up focusing mode.</p>
     * <p>In this mode, the lens does not move unless the
     * autofocus trigger action is called. When that trigger is
     * activated, AF will transition to ACTIVE_SCAN, then to
     * the outcome of the scan (FOCUSED or NOT_FOCUSED). This
     * mode is optimized for focusing on objects very close to
     * the camera.</p>
     * <p>When that trigger is activated, AF will transition to
     * ACTIVE_SCAN, then to the outcome of the scan (FOCUSED or
     * NOT_FOCUSED). Triggering cancel AF resets the lens
     * position to default, and sets the AF state to
     * INACTIVE.</p>
     * @see CaptureRequest#CONTROL_AF_MODE
     */
    public static final int CONTROL_AF_MODE_MACRO = 2;

    /**
     * <p>In this mode, the AF algorithm modifies the lens
     * position continually to attempt to provide a
     * constantly-in-focus image stream.</p>
     * <p>The focusing behavior should be suitable for good quality
     * video recording; typically this means slower focus
     * movement and no overshoots. When the AF trigger is not
     * involved, the AF algorithm should start in INACTIVE state,
     * and then transition into PASSIVE_SCAN and PASSIVE_FOCUSED
     * states as appropriate. When the AF trigger is activated,
     * the algorithm should immediately transition into
     * AF_FOCUSED or AF_NOT_FOCUSED as appropriate, and lock the
     * lens position until a cancel AF trigger is received.</p>
     * <p>Once cancel is received, the algorithm should transition
     * back to INACTIVE and resume passive scan. Note that this
     * behavior is not identical to CONTINUOUS_PICTURE, since an
     * ongoing PASSIVE_SCAN must immediately be
     * canceled.</p>
     * @see CaptureRequest#CONTROL_AF_MODE
     */
    public static final int CONTROL_AF_MODE_CONTINUOUS_VIDEO = 3;

    /**
     * <p>In this mode, the AF algorithm modifies the lens
     * position continually to attempt to provide a
     * constantly-in-focus image stream.</p>
     * <p>The focusing behavior should be suitable for still image
     * capture; typically this means focusing as fast as
     * possible. When the AF trigger is not involved, the AF
     * algorithm should start in INACTIVE state, and then
     * transition into PASSIVE_SCAN and PASSIVE_FOCUSED states as
     * appropriate as it attempts to maintain focus. When the AF
     * trigger is activated, the algorithm should finish its
     * PASSIVE_SCAN if active, and then transition into
     * AF_FOCUSED or AF_NOT_FOCUSED as appropriate, and lock the
     * lens position until a cancel AF trigger is received.</p>
     * <p>When the AF cancel trigger is activated, the algorithm
     * should transition back to INACTIVE and then act as if it
     * has just been started.</p>
     * @see CaptureRequest#CONTROL_AF_MODE
     */
    public static final int CONTROL_AF_MODE_CONTINUOUS_PICTURE = 4;

    /**
     * <p>Extended depth of field (digital focus) mode.</p>
     * <p>The camera device will produce images with an extended
     * depth of field automatically; no special focusing
     * operations need to be done before taking a picture.</p>
     * <p>AF triggers are ignored, and the AF state will always be
     * INACTIVE.</p>
     * @see CaptureRequest#CONTROL_AF_MODE
     */
    public static final int CONTROL_AF_MODE_EDOF = 5;

    //
    // Enumeration values for CaptureRequest#CONTROL_AF_TRIGGER
    //

    /**
     * <p>The trigger is idle.</p>
     * @see CaptureRequest#CONTROL_AF_TRIGGER
     */
    public static final int CONTROL_AF_TRIGGER_IDLE = 0;

    /**
     * <p>Autofocus will trigger now.</p>
     * @see CaptureRequest#CONTROL_AF_TRIGGER
     */
    public static final int CONTROL_AF_TRIGGER_START = 1;

    /**
     * <p>Autofocus will return to its initial
     * state, and cancel any currently active trigger.</p>
     * @see CaptureRequest#CONTROL_AF_TRIGGER
     */
    public static final int CONTROL_AF_TRIGGER_CANCEL = 2;

    //
    // Enumeration values for CaptureRequest#CONTROL_AWB_MODE
    //

    /**
     * <p>The camera device's auto-white balance routine is disabled.</p>
     * <p>The application-selected color transform matrix
     * ({@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}) and gains
     * ({@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains}) are used by the camera
     * device for manual white balance control.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_OFF = 0;

    /**
     * <p>The camera device's auto-white balance routine is active.</p>
     * <p>The application's values for {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} are ignored.
     * For devices that support the MANUAL_POST_PROCESSING capability, the
     * values used by the camera device for the transform and gains
     * will be available in the capture result for this request.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_AUTO = 1;

    /**
     * <p>The camera device's auto-white balance routine is disabled;
     * the camera device uses incandescent light as the assumed scene
     * illumination for white balance.</p>
     * <p>While the exact white balance transforms are up to the
     * camera device, they will approximately match the CIE
     * standard illuminant A.</p>
     * <p>The application's values for {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} are ignored.
     * For devices that support the MANUAL_POST_PROCESSING capability, the
     * values used by the camera device for the transform and gains
     * will be available in the capture result for this request.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_INCANDESCENT = 2;

    /**
     * <p>The camera device's auto-white balance routine is disabled;
     * the camera device uses fluorescent light as the assumed scene
     * illumination for white balance.</p>
     * <p>While the exact white balance transforms are up to the
     * camera device, they will approximately match the CIE
     * standard illuminant F2.</p>
     * <p>The application's values for {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} are ignored.
     * For devices that support the MANUAL_POST_PROCESSING capability, the
     * values used by the camera device for the transform and gains
     * will be available in the capture result for this request.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_FLUORESCENT = 3;

    /**
     * <p>The camera device's auto-white balance routine is disabled;
     * the camera device uses warm fluorescent light as the assumed scene
     * illumination for white balance.</p>
     * <p>While the exact white balance transforms are up to the
     * camera device, they will approximately match the CIE
     * standard illuminant F4.</p>
     * <p>The application's values for {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} are ignored.
     * For devices that support the MANUAL_POST_PROCESSING capability, the
     * values used by the camera device for the transform and gains
     * will be available in the capture result for this request.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_WARM_FLUORESCENT = 4;

    /**
     * <p>The camera device's auto-white balance routine is disabled;
     * the camera device uses daylight light as the assumed scene
     * illumination for white balance.</p>
     * <p>While the exact white balance transforms are up to the
     * camera device, they will approximately match the CIE
     * standard illuminant D65.</p>
     * <p>The application's values for {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} are ignored.
     * For devices that support the MANUAL_POST_PROCESSING capability, the
     * values used by the camera device for the transform and gains
     * will be available in the capture result for this request.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_DAYLIGHT = 5;

    /**
     * <p>The camera device's auto-white balance routine is disabled;
     * the camera device uses cloudy daylight light as the assumed scene
     * illumination for white balance.</p>
     * <p>The application's values for {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} are ignored.
     * For devices that support the MANUAL_POST_PROCESSING capability, the
     * values used by the camera device for the transform and gains
     * will be available in the capture result for this request.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_CLOUDY_DAYLIGHT = 6;

    /**
     * <p>The camera device's auto-white balance routine is disabled;
     * the camera device uses twilight light as the assumed scene
     * illumination for white balance.</p>
     * <p>The application's values for {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} are ignored.
     * For devices that support the MANUAL_POST_PROCESSING capability, the
     * values used by the camera device for the transform and gains
     * will be available in the capture result for this request.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_TWILIGHT = 7;

    /**
     * <p>The camera device's auto-white balance routine is disabled;
     * the camera device uses shade light as the assumed scene
     * illumination for white balance.</p>
     * <p>The application's values for {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}
     * and {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} are ignored.
     * For devices that support the MANUAL_POST_PROCESSING capability, the
     * values used by the camera device for the transform and gains
     * will be available in the capture result for this request.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final int CONTROL_AWB_MODE_SHADE = 8;

    //
    // Enumeration values for CaptureRequest#CONTROL_CAPTURE_INTENT
    //

    /**
     * <p>The goal of this request doesn't fall into the other
     * categories. The camera device will default to preview-like
     * behavior.</p>
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     */
    public static final int CONTROL_CAPTURE_INTENT_CUSTOM = 0;

    /**
     * <p>This request is for a preview-like use case.</p>
     * <p>The precapture trigger may be used to start off a metering
     * w/flash sequence.</p>
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     */
    public static final int CONTROL_CAPTURE_INTENT_PREVIEW = 1;

    /**
     * <p>This request is for a still capture-type
     * use case.</p>
     * <p>If the flash unit is under automatic control, it may fire as needed.</p>
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     */
    public static final int CONTROL_CAPTURE_INTENT_STILL_CAPTURE = 2;

    /**
     * <p>This request is for a video recording
     * use case.</p>
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     */
    public static final int CONTROL_CAPTURE_INTENT_VIDEO_RECORD = 3;

    /**
     * <p>This request is for a video snapshot (still
     * image while recording video) use case.</p>
     * <p>The camera device should take the highest-quality image
     * possible (given the other settings) without disrupting the
     * frame rate of video recording.<br />
     * </p>
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     */
    public static final int CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT = 4;

    /**
     * <p>This request is for a ZSL usecase; the
     * application will stream full-resolution images and
     * reprocess one or several later for a final
     * capture.</p>
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     */
    public static final int CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG = 5;

    /**
     * <p>This request is for manual capture use case where
     * the applications want to directly control the capture parameters.</p>
     * <p>For example, the application may wish to manually control
     * {@link CaptureRequest#SENSOR_EXPOSURE_TIME android.sensor.exposureTime}, {@link CaptureRequest#SENSOR_SENSITIVITY android.sensor.sensitivity}, etc.</p>
     *
     * @see CaptureRequest#SENSOR_EXPOSURE_TIME
     * @see CaptureRequest#SENSOR_SENSITIVITY
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     */
    public static final int CONTROL_CAPTURE_INTENT_MANUAL = 6;

    //
    // Enumeration values for CaptureRequest#CONTROL_EFFECT_MODE
    //

    /**
     * <p>No color effect will be applied.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_OFF = 0;

    /**
     * <p>A "monocolor" effect where the image is mapped into
     * a single color.</p>
     * <p>This will typically be grayscale.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_MONO = 1;

    /**
     * <p>A "photo-negative" effect where the image's colors
     * are inverted.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_NEGATIVE = 2;

    /**
     * <p>A "solarisation" effect (Sabattier effect) where the
     * image is wholly or partially reversed in
     * tone.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_SOLARIZE = 3;

    /**
     * <p>A "sepia" effect where the image is mapped into warm
     * gray, red, and brown tones.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_SEPIA = 4;

    /**
     * <p>A "posterization" effect where the image uses
     * discrete regions of tone rather than a continuous
     * gradient of tones.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_POSTERIZE = 5;

    /**
     * <p>A "whiteboard" effect where the image is typically displayed
     * as regions of white, with black or grey details.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_WHITEBOARD = 6;

    /**
     * <p>A "blackboard" effect where the image is typically displayed
     * as regions of black, with white or grey details.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_BLACKBOARD = 7;

    /**
     * <p>An "aqua" effect where a blue hue is added to the image.</p>
     * @see CaptureRequest#CONTROL_EFFECT_MODE
     */
    public static final int CONTROL_EFFECT_MODE_AQUA = 8;

    //
    // Enumeration values for CaptureRequest#CONTROL_MODE
    //

    /**
     * <p>Full application control of pipeline.</p>
     * <p>All control by the device's metering and focusing (3A)
     * routines is disabled, and no other settings in
     * android.control.* have any effect, except that
     * {@link CaptureRequest#CONTROL_CAPTURE_INTENT android.control.captureIntent} may be used by the camera
     * device to select post-processing values for processing
     * blocks that do not allow for manual control, or are not
     * exposed by the camera API.</p>
     * <p>However, the camera device's 3A routines may continue to
     * collect statistics and update their internal state so that
     * when control is switched to AUTO mode, good control values
     * can be immediately applied.</p>
     *
     * @see CaptureRequest#CONTROL_CAPTURE_INTENT
     * @see CaptureRequest#CONTROL_MODE
     */
    public static final int CONTROL_MODE_OFF = 0;

    /**
     * <p>Use settings for each individual 3A routine.</p>
     * <p>Manual control of capture parameters is disabled. All
     * controls in android.control.* besides sceneMode take
     * effect.</p>
     * @see CaptureRequest#CONTROL_MODE
     */
    public static final int CONTROL_MODE_AUTO = 1;

    /**
     * <p>Use a specific scene mode.</p>
     * <p>Enabling this disables control.aeMode, control.awbMode and
     * control.afMode controls; the camera device will ignore
     * those settings while USE_SCENE_MODE is active (except for
     * FACE_PRIORITY scene mode). Other control entries are still
     * active.  This setting can only be used if scene mode is
     * supported (i.e. {@link CameraCharacteristics#CONTROL_AVAILABLE_SCENE_MODES android.control.availableSceneModes}
     * contain some modes other than DISABLED).</p>
     *
     * @see CameraCharacteristics#CONTROL_AVAILABLE_SCENE_MODES
     * @see CaptureRequest#CONTROL_MODE
     */
    public static final int CONTROL_MODE_USE_SCENE_MODE = 2;

    /**
     * <p>Same as OFF mode, except that this capture will not be
     * used by camera device background auto-exposure, auto-white balance and
     * auto-focus algorithms (3A) to update their statistics.</p>
     * <p>Specifically, the 3A routines are locked to the last
     * values set from a request with AUTO, OFF, or
     * USE_SCENE_MODE, and any statistics or state updates
     * collected from manual captures with OFF_KEEP_STATE will be
     * discarded by the camera device.</p>
     * @see CaptureRequest#CONTROL_MODE
     */
    public static final int CONTROL_MODE_OFF_KEEP_STATE = 3;

    //
    // Enumeration values for CaptureRequest#CONTROL_SCENE_MODE
    //

    /**
     * <p>Indicates that no scene modes are set for a given capture request.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_DISABLED = 0;

    /**
     * <p>If face detection support exists, use face
     * detection data for auto-focus, auto-white balance, and
     * auto-exposure routines.</p>
     * <p>If face detection statistics are disabled
     * (i.e. {@link CaptureRequest#STATISTICS_FACE_DETECT_MODE android.statistics.faceDetectMode} is set to OFF),
     * this should still operate correctly (but will not return
     * face detection statistics to the framework).</p>
     * <p>Unlike the other scene modes, {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode},
     * {@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode}, and {@link CaptureRequest#CONTROL_AF_MODE android.control.afMode}
     * remain active when FACE_PRIORITY is set.</p>
     *
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureRequest#CONTROL_AF_MODE
     * @see CaptureRequest#CONTROL_AWB_MODE
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_FACE_PRIORITY = 1;

    /**
     * <p>Optimized for photos of quickly moving objects.</p>
     * <p>Similar to SPORTS.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_ACTION = 2;

    /**
     * <p>Optimized for still photos of people.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_PORTRAIT = 3;

    /**
     * <p>Optimized for photos of distant macroscopic objects.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_LANDSCAPE = 4;

    /**
     * <p>Optimized for low-light settings.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_NIGHT = 5;

    /**
     * <p>Optimized for still photos of people in low-light
     * settings.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_NIGHT_PORTRAIT = 6;

    /**
     * <p>Optimized for dim, indoor settings where flash must
     * remain off.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_THEATRE = 7;

    /**
     * <p>Optimized for bright, outdoor beach settings.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_BEACH = 8;

    /**
     * <p>Optimized for bright, outdoor settings containing snow.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_SNOW = 9;

    /**
     * <p>Optimized for scenes of the setting sun.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_SUNSET = 10;

    /**
     * <p>Optimized to avoid blurry photos due to small amounts of
     * device motion (for example: due to hand shake).</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_STEADYPHOTO = 11;

    /**
     * <p>Optimized for nighttime photos of fireworks.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_FIREWORKS = 12;

    /**
     * <p>Optimized for photos of quickly moving people.</p>
     * <p>Similar to ACTION.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_SPORTS = 13;

    /**
     * <p>Optimized for dim, indoor settings with multiple moving
     * people.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_PARTY = 14;

    /**
     * <p>Optimized for dim settings where the main light source
     * is a flame.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_CANDLELIGHT = 15;

    /**
     * <p>Optimized for accurately capturing a photo of barcode
     * for use by camera applications that wish to read the
     * barcode value.</p>
     * @see CaptureRequest#CONTROL_SCENE_MODE
     */
    public static final int CONTROL_SCENE_MODE_BARCODE = 16;

    //
    // Enumeration values for CaptureRequest#CONTROL_VIDEO_STABILIZATION_MODE
    //

    /**
     * <p>Video stabilization is disabled.</p>
     * @see CaptureRequest#CONTROL_VIDEO_STABILIZATION_MODE
     */
    public static final int CONTROL_VIDEO_STABILIZATION_MODE_OFF = 0;

    /**
     * <p>Video stabilization is enabled.</p>
     * @see CaptureRequest#CONTROL_VIDEO_STABILIZATION_MODE
     */
    public static final int CONTROL_VIDEO_STABILIZATION_MODE_ON = 1;

    //
    // Enumeration values for CaptureRequest#EDGE_MODE
    //

    /**
     * <p>No edge enhancement is applied.</p>
     * @see CaptureRequest#EDGE_MODE
     */
    public static final int EDGE_MODE_OFF = 0;

    /**
     * <p>Apply edge enhancement at a quality level that does not slow down frame rate relative to sensor
     * output</p>
     * @see CaptureRequest#EDGE_MODE
     */
    public static final int EDGE_MODE_FAST = 1;

    /**
     * <p>Apply high-quality edge enhancement, at a cost of reducing output frame rate.</p>
     * @see CaptureRequest#EDGE_MODE
     */
    public static final int EDGE_MODE_HIGH_QUALITY = 2;

    //
    // Enumeration values for CaptureRequest#FLASH_MODE
    //

    /**
     * <p>Do not fire the flash for this capture.</p>
     * @see CaptureRequest#FLASH_MODE
     */
    public static final int FLASH_MODE_OFF = 0;

    /**
     * <p>If the flash is available and charged, fire flash
     * for this capture.</p>
     * @see CaptureRequest#FLASH_MODE
     */
    public static final int FLASH_MODE_SINGLE = 1;

    /**
     * <p>Transition flash to continuously on.</p>
     * @see CaptureRequest#FLASH_MODE
     */
    public static final int FLASH_MODE_TORCH = 2;

    //
    // Enumeration values for CaptureRequest#HOT_PIXEL_MODE
    //

    /**
     * <p>No hot pixel correction is applied.</p>
     * <p>The frame rate must not be reduced relative to sensor raw output
     * for this option.</p>
     * <p>The hotpixel map may be returned in {@link CaptureResult#STATISTICS_HOT_PIXEL_MAP android.statistics.hotPixelMap}.</p>
     *
     * @see CaptureResult#STATISTICS_HOT_PIXEL_MAP
     * @see CaptureRequest#HOT_PIXEL_MODE
     */
    public static final int HOT_PIXEL_MODE_OFF = 0;

    /**
     * <p>Hot pixel correction is applied, without reducing frame
     * rate relative to sensor raw output.</p>
     * <p>The hotpixel map may be returned in {@link CaptureResult#STATISTICS_HOT_PIXEL_MAP android.statistics.hotPixelMap}.</p>
     *
     * @see CaptureResult#STATISTICS_HOT_PIXEL_MAP
     * @see CaptureRequest#HOT_PIXEL_MODE
     */
    public static final int HOT_PIXEL_MODE_FAST = 1;

    /**
     * <p>High-quality hot pixel correction is applied, at a cost
     * of reducing frame rate relative to sensor raw output.</p>
     * <p>The hotpixel map may be returned in {@link CaptureResult#STATISTICS_HOT_PIXEL_MAP android.statistics.hotPixelMap}.</p>
     *
     * @see CaptureResult#STATISTICS_HOT_PIXEL_MAP
     * @see CaptureRequest#HOT_PIXEL_MODE
     */
    public static final int HOT_PIXEL_MODE_HIGH_QUALITY = 2;

    //
    // Enumeration values for CaptureRequest#LENS_OPTICAL_STABILIZATION_MODE
    //

    /**
     * <p>Optical stabilization is unavailable.</p>
     * @see CaptureRequest#LENS_OPTICAL_STABILIZATION_MODE
     */
    public static final int LENS_OPTICAL_STABILIZATION_MODE_OFF = 0;

    /**
     * <p>Optical stabilization is enabled.</p>
     * @see CaptureRequest#LENS_OPTICAL_STABILIZATION_MODE
     */
    public static final int LENS_OPTICAL_STABILIZATION_MODE_ON = 1;

    //
    // Enumeration values for CaptureRequest#NOISE_REDUCTION_MODE
    //

    /**
     * <p>No noise reduction is applied.</p>
     * @see CaptureRequest#NOISE_REDUCTION_MODE
     */
    public static final int NOISE_REDUCTION_MODE_OFF = 0;

    /**
     * <p>Noise reduction is applied without reducing frame rate relative to sensor
     * output.</p>
     * @see CaptureRequest#NOISE_REDUCTION_MODE
     */
    public static final int NOISE_REDUCTION_MODE_FAST = 1;

    /**
     * <p>High-quality noise reduction is applied, at the cost of reducing frame rate
     * relative to sensor output.</p>
     * @see CaptureRequest#NOISE_REDUCTION_MODE
     */
    public static final int NOISE_REDUCTION_MODE_HIGH_QUALITY = 2;

    //
    // Enumeration values for CaptureRequest#SENSOR_TEST_PATTERN_MODE
    //

    /**
     * <p>No test pattern mode is used, and the camera
     * device returns captures from the image sensor.</p>
     * <p>This is the default if the key is not set.</p>
     * @see CaptureRequest#SENSOR_TEST_PATTERN_MODE
     */
    public static final int SENSOR_TEST_PATTERN_MODE_OFF = 0;

    /**
     * <p>Each pixel in <code>[R, G_even, G_odd, B]</code> is replaced by its
     * respective color channel provided in
     * {@link CaptureRequest#SENSOR_TEST_PATTERN_DATA android.sensor.testPatternData}.</p>
     * <p>For example:</p>
     * <pre><code>android.testPatternData = [0, 0xFFFFFFFF, 0xFFFFFFFF, 0]
     * </code></pre>
     * <p>All green pixels are 100% green. All red/blue pixels are black.</p>
     * <pre><code>android.testPatternData = [0xFFFFFFFF, 0, 0xFFFFFFFF, 0]
     * </code></pre>
     * <p>All red pixels are 100% red. Only the odd green pixels
     * are 100% green. All blue pixels are 100% black.</p>
     *
     * @see CaptureRequest#SENSOR_TEST_PATTERN_DATA
     * @see CaptureRequest#SENSOR_TEST_PATTERN_MODE
     */
    public static final int SENSOR_TEST_PATTERN_MODE_SOLID_COLOR = 1;

    /**
     * <p>All pixel data is replaced with an 8-bar color pattern.</p>
     * <p>The vertical bars (left-to-right) are as follows:</p>
     * <ul>
     * <li>100% white</li>
     * <li>yellow</li>
     * <li>cyan</li>
     * <li>green</li>
     * <li>magenta</li>
     * <li>red</li>
     * <li>blue</li>
     * <li>black</li>
     * </ul>
     * <p>In general the image would look like the following:</p>
     * <pre><code>W Y C G M R B K
     * W Y C G M R B K
     * W Y C G M R B K
     * W Y C G M R B K
     * W Y C G M R B K
     * . . . . . . . .
     * . . . . . . . .
     * . . . . . . . .
     *
     * (B = Blue, K = Black)
     * </code></pre>
     * <p>Each bar should take up 1/8 of the sensor pixel array width.
     * When this is not possible, the bar size should be rounded
     * down to the nearest integer and the pattern can repeat
     * on the right side.</p>
     * <p>Each bar's height must always take up the full sensor
     * pixel array height.</p>
     * <p>Each pixel in this test pattern must be set to either
     * 0% intensity or 100% intensity.</p>
     * @see CaptureRequest#SENSOR_TEST_PATTERN_MODE
     */
    public static final int SENSOR_TEST_PATTERN_MODE_COLOR_BARS = 2;

    /**
     * <p>The test pattern is similar to COLOR_BARS, except that
     * each bar should start at its specified color at the top,
     * and fade to gray at the bottom.</p>
     * <p>Furthermore each bar is further subdivided into a left and
     * right half. The left half should have a smooth gradient,
     * and the right half should have a quantized gradient.</p>
     * <p>In particular, the right half's should consist of blocks of the
     * same color for 1/16th active sensor pixel array width.</p>
     * <p>The least significant bits in the quantized gradient should
     * be copied from the most significant bits of the smooth gradient.</p>
     * <p>The height of each bar should always be a multiple of 128.
     * When this is not the case, the pattern should repeat at the bottom
     * of the image.</p>
     * @see CaptureRequest#SENSOR_TEST_PATTERN_MODE
     */
    public static final int SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY = 3;

    /**
     * <p>All pixel data is replaced by a pseudo-random sequence
     * generated from a PN9 512-bit sequence (typically implemented
     * in hardware with a linear feedback shift register).</p>
     * <p>The generator should be reset at the beginning of each frame,
     * and thus each subsequent raw frame with this test pattern should
     * be exactly the same as the last.</p>
     * @see CaptureRequest#SENSOR_TEST_PATTERN_MODE
     */
    public static final int SENSOR_TEST_PATTERN_MODE_PN9 = 4;

    /**
     * <p>The first custom test pattern. All custom patterns that are
     * available only on this camera device are at least this numeric
     * value.</p>
     * <p>All of the custom test patterns will be static
     * (that is the raw image must not vary from frame to frame).</p>
     * @see CaptureRequest#SENSOR_TEST_PATTERN_MODE
     */
    public static final int SENSOR_TEST_PATTERN_MODE_CUSTOM1 = 256;

    //
    // Enumeration values for CaptureRequest#SHADING_MODE
    //

    /**
     * <p>No lens shading correction is applied.</p>
     * @see CaptureRequest#SHADING_MODE
     */
    public static final int SHADING_MODE_OFF = 0;

    /**
     * <p>Apply lens shading corrections, without slowing
     * frame rate relative to sensor raw output</p>
     * @see CaptureRequest#SHADING_MODE
     */
    public static final int SHADING_MODE_FAST = 1;

    /**
     * <p>Apply high-quality lens shading correction, at the
     * cost of reduced frame rate.</p>
     * @see CaptureRequest#SHADING_MODE
     */
    public static final int SHADING_MODE_HIGH_QUALITY = 2;

    //
    // Enumeration values for CaptureRequest#STATISTICS_FACE_DETECT_MODE
    //

    /**
     * <p>Do not include face detection statistics in capture
     * results.</p>
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     */
    public static final int STATISTICS_FACE_DETECT_MODE_OFF = 0;

    /**
     * <p>Return face rectangle and confidence values only.</p>
     * <p>In this mode, only android.statistics.faceRectangles and
     * android.statistics.faceScores outputs are valid.</p>
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     */
    public static final int STATISTICS_FACE_DETECT_MODE_SIMPLE = 1;

    /**
     * <p>Return all face
     * metadata.</p>
     * <p>In this mode,
     * android.statistics.faceRectangles,
     * android.statistics.faceScores,
     * android.statistics.faceIds, and
     * android.statistics.faceLandmarks outputs are valid.</p>
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     */
    public static final int STATISTICS_FACE_DETECT_MODE_FULL = 2;

    //
    // Enumeration values for CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE
    //

    /**
     * <p>Do not include a lens shading map in the capture result.</p>
     * @see CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE
     */
    public static final int STATISTICS_LENS_SHADING_MAP_MODE_OFF = 0;

    /**
     * <p>Include a lens shading map in the capture result.</p>
     * @see CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE
     */
    public static final int STATISTICS_LENS_SHADING_MAP_MODE_ON = 1;

    //
    // Enumeration values for CaptureRequest#TONEMAP_MODE
    //

    /**
     * <p>Use the tone mapping curve specified in
     * the {@link CaptureRequest#TONEMAP_CURVE android.tonemap.curve}* entries.</p>
     * <p>All color enhancement and tonemapping must be disabled, except
     * for applying the tonemapping curve specified by
     * {@link CaptureRequest#TONEMAP_CURVE android.tonemap.curve}.</p>
     * <p>Must not slow down frame rate relative to raw
     * sensor output.</p>
     *
     * @see CaptureRequest#TONEMAP_CURVE
     * @see CaptureRequest#TONEMAP_MODE
     */
    public static final int TONEMAP_MODE_CONTRAST_CURVE = 0;

    /**
     * <p>Advanced gamma mapping and color enhancement may be applied, without
     * reducing frame rate compared to raw sensor output.</p>
     * @see CaptureRequest#TONEMAP_MODE
     */
    public static final int TONEMAP_MODE_FAST = 1;

    /**
     * <p>High-quality gamma mapping and color enhancement will be applied, at
     * the cost of reduced frame rate compared to raw sensor output.</p>
     * @see CaptureRequest#TONEMAP_MODE
     */
    public static final int TONEMAP_MODE_HIGH_QUALITY = 2;

    //
    // Enumeration values for CaptureResult#CONTROL_AE_STATE
    //

    /**
     * <p>AE is off or recently reset.</p>
     * <p>When a camera device is opened, it starts in
     * this state. This is a transient state, the camera device may skip reporting
     * this state in capture result.</p>
     * @see CaptureResult#CONTROL_AE_STATE
     */
    public static final int CONTROL_AE_STATE_INACTIVE = 0;

    /**
     * <p>AE doesn't yet have a good set of control values
     * for the current scene.</p>
     * <p>This is a transient state, the camera device may skip
     * reporting this state in capture result.</p>
     * @see CaptureResult#CONTROL_AE_STATE
     */
    public static final int CONTROL_AE_STATE_SEARCHING = 1;

    /**
     * <p>AE has a good set of control values for the
     * current scene.</p>
     * @see CaptureResult#CONTROL_AE_STATE
     */
    public static final int CONTROL_AE_STATE_CONVERGED = 2;

    /**
     * <p>AE has been locked.</p>
     * @see CaptureResult#CONTROL_AE_STATE
     */
    public static final int CONTROL_AE_STATE_LOCKED = 3;

    /**
     * <p>AE has a good set of control values, but flash
     * needs to be fired for good quality still
     * capture.</p>
     * @see CaptureResult#CONTROL_AE_STATE
     */
    public static final int CONTROL_AE_STATE_FLASH_REQUIRED = 4;

    /**
     * <p>AE has been asked to do a precapture sequence
     * and is currently executing it.</p>
     * <p>Precapture can be triggered through setting
     * {@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER android.control.aePrecaptureTrigger} to START.</p>
     * <p>Once PRECAPTURE completes, AE will transition to CONVERGED
     * or FLASH_REQUIRED as appropriate. This is a transient
     * state, the camera device may skip reporting this state in
     * capture result.</p>
     *
     * @see CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
     * @see CaptureResult#CONTROL_AE_STATE
     */
    public static final int CONTROL_AE_STATE_PRECAPTURE = 5;

    //
    // Enumeration values for CaptureResult#CONTROL_AF_STATE
    //

    /**
     * <p>AF is off or has not yet tried to scan/been asked
     * to scan.</p>
     * <p>When a camera device is opened, it starts in this
     * state. This is a transient state, the camera device may
     * skip reporting this state in capture
     * result.</p>
     * @see CaptureResult#CONTROL_AF_STATE
     */
    public static final int CONTROL_AF_STATE_INACTIVE = 0;

    /**
     * <p>AF is currently performing an AF scan initiated the
     * camera device in a continuous autofocus mode.</p>
     * <p>Only used by CONTINUOUS_* AF modes. This is a transient
     * state, the camera device may skip reporting this state in
     * capture result.</p>
     * @see CaptureResult#CONTROL_AF_STATE
     */
    public static final int CONTROL_AF_STATE_PASSIVE_SCAN = 1;

    /**
     * <p>AF currently believes it is in focus, but may
     * restart scanning at any time.</p>
     * <p>Only used by CONTINUOUS_* AF modes. This is a transient
     * state, the camera device may skip reporting this state in
     * capture result.</p>
     * @see CaptureResult#CONTROL_AF_STATE
     */
    public static final int CONTROL_AF_STATE_PASSIVE_FOCUSED = 2;

    /**
     * <p>AF is performing an AF scan because it was
     * triggered by AF trigger.</p>
     * <p>Only used by AUTO or MACRO AF modes. This is a transient
     * state, the camera device may skip reporting this state in
     * capture result.</p>
     * @see CaptureResult#CONTROL_AF_STATE
     */
    public static final int CONTROL_AF_STATE_ACTIVE_SCAN = 3;

    /**
     * <p>AF believes it is focused correctly and has locked
     * focus.</p>
     * <p>This state is reached only after an explicit START AF trigger has been
     * sent ({@link CaptureRequest#CONTROL_AF_TRIGGER android.control.afTrigger}), when good focus has been obtained.</p>
     * <p>The lens will remain stationary until the AF mode ({@link CaptureRequest#CONTROL_AF_MODE android.control.afMode}) is changed or
     * a new AF trigger is sent to the camera device ({@link CaptureRequest#CONTROL_AF_TRIGGER android.control.afTrigger}).</p>
     *
     * @see CaptureRequest#CONTROL_AF_MODE
     * @see CaptureRequest#CONTROL_AF_TRIGGER
     * @see CaptureResult#CONTROL_AF_STATE
     */
    public static final int CONTROL_AF_STATE_FOCUSED_LOCKED = 4;

    /**
     * <p>AF has failed to focus successfully and has locked
     * focus.</p>
     * <p>This state is reached only after an explicit START AF trigger has been
     * sent ({@link CaptureRequest#CONTROL_AF_TRIGGER android.control.afTrigger}), when good focus cannot be obtained.</p>
     * <p>The lens will remain stationary until the AF mode ({@link CaptureRequest#CONTROL_AF_MODE android.control.afMode}) is changed or
     * a new AF trigger is sent to the camera device ({@link CaptureRequest#CONTROL_AF_TRIGGER android.control.afTrigger}).</p>
     *
     * @see CaptureRequest#CONTROL_AF_MODE
     * @see CaptureRequest#CONTROL_AF_TRIGGER
     * @see CaptureResult#CONTROL_AF_STATE
     */
    public static final int CONTROL_AF_STATE_NOT_FOCUSED_LOCKED = 5;

    /**
     * <p>AF finished a passive scan without finding focus,
     * and may restart scanning at any time.</p>
     * <p>Only used by CONTINUOUS_* AF modes. This is a transient state, the camera
     * device may skip reporting this state in capture result.</p>
     * @see CaptureResult#CONTROL_AF_STATE
     */
    public static final int CONTROL_AF_STATE_PASSIVE_UNFOCUSED = 6;

    //
    // Enumeration values for CaptureResult#CONTROL_AWB_STATE
    //

    /**
     * <p>AWB is not in auto mode, or has not yet started metering.</p>
     * <p>When a camera device is opened, it starts in this
     * state. This is a transient state, the camera device may
     * skip reporting this state in capture
     * result.</p>
     * @see CaptureResult#CONTROL_AWB_STATE
     */
    public static final int CONTROL_AWB_STATE_INACTIVE = 0;

    /**
     * <p>AWB doesn't yet have a good set of control
     * values for the current scene.</p>
     * <p>This is a transient state, the camera device
     * may skip reporting this state in capture result.</p>
     * @see CaptureResult#CONTROL_AWB_STATE
     */
    public static final int CONTROL_AWB_STATE_SEARCHING = 1;

    /**
     * <p>AWB has a good set of control values for the
     * current scene.</p>
     * @see CaptureResult#CONTROL_AWB_STATE
     */
    public static final int CONTROL_AWB_STATE_CONVERGED = 2;

    /**
     * <p>AWB has been locked.</p>
     * @see CaptureResult#CONTROL_AWB_STATE
     */
    public static final int CONTROL_AWB_STATE_LOCKED = 3;

    //
    // Enumeration values for CaptureResult#FLASH_STATE
    //

    /**
     * <p>No flash on camera.</p>
     * @see CaptureResult#FLASH_STATE
     */
    public static final int FLASH_STATE_UNAVAILABLE = 0;

    /**
     * <p>Flash is charging and cannot be fired.</p>
     * @see CaptureResult#FLASH_STATE
     */
    public static final int FLASH_STATE_CHARGING = 1;

    /**
     * <p>Flash is ready to fire.</p>
     * @see CaptureResult#FLASH_STATE
     */
    public static final int FLASH_STATE_READY = 2;

    /**
     * <p>Flash fired for this capture.</p>
     * @see CaptureResult#FLASH_STATE
     */
    public static final int FLASH_STATE_FIRED = 3;

    /**
     * <p>Flash partially illuminated this frame.</p>
     * <p>This is usually due to the next or previous frame having
     * the flash fire, and the flash spilling into this capture
     * due to hardware limitations.</p>
     * @see CaptureResult#FLASH_STATE
     */
    public static final int FLASH_STATE_PARTIAL = 4;

    //
    // Enumeration values for CaptureResult#LENS_STATE
    //

    /**
     * <p>The lens parameters ({@link CaptureRequest#LENS_FOCAL_LENGTH android.lens.focalLength}, {@link CaptureRequest#LENS_FOCUS_DISTANCE android.lens.focusDistance},
     * {@link CaptureRequest#LENS_FILTER_DENSITY android.lens.filterDensity} and {@link CaptureRequest#LENS_APERTURE android.lens.aperture}) are not changing.</p>
     *
     * @see CaptureRequest#LENS_APERTURE
     * @see CaptureRequest#LENS_FILTER_DENSITY
     * @see CaptureRequest#LENS_FOCAL_LENGTH
     * @see CaptureRequest#LENS_FOCUS_DISTANCE
     * @see CaptureResult#LENS_STATE
     */
    public static final int LENS_STATE_STATIONARY = 0;

    /**
     * <p>One or several of the lens parameters
     * ({@link CaptureRequest#LENS_FOCAL_LENGTH android.lens.focalLength}, {@link CaptureRequest#LENS_FOCUS_DISTANCE android.lens.focusDistance},
     * {@link CaptureRequest#LENS_FILTER_DENSITY android.lens.filterDensity} or {@link CaptureRequest#LENS_APERTURE android.lens.aperture}) is
     * currently changing.</p>
     *
     * @see CaptureRequest#LENS_APERTURE
     * @see CaptureRequest#LENS_FILTER_DENSITY
     * @see CaptureRequest#LENS_FOCAL_LENGTH
     * @see CaptureRequest#LENS_FOCUS_DISTANCE
     * @see CaptureResult#LENS_STATE
     */
    public static final int LENS_STATE_MOVING = 1;

    //
    // Enumeration values for CaptureResult#STATISTICS_SCENE_FLICKER
    //

    /**
     * <p>The camera device does not detect any flickering illumination
     * in the current scene.</p>
     * @see CaptureResult#STATISTICS_SCENE_FLICKER
     */
    public static final int STATISTICS_SCENE_FLICKER_NONE = 0;

    /**
     * <p>The camera device detects illumination flickering at 50Hz
     * in the current scene.</p>
     * @see CaptureResult#STATISTICS_SCENE_FLICKER
     */
    public static final int STATISTICS_SCENE_FLICKER_50HZ = 1;

    /**
     * <p>The camera device detects illumination flickering at 60Hz
     * in the current scene.</p>
     * @see CaptureResult#STATISTICS_SCENE_FLICKER
     */
    public static final int STATISTICS_SCENE_FLICKER_60HZ = 2;

    //
    // Enumeration values for CaptureResult#SYNC_FRAME_NUMBER
    //

    /**
     * <p>The current result is not yet fully synchronized to any request.</p>
     * <p>Synchronization is in progress, and reading metadata from this
     * result may include a mix of data that have taken effect since the
     * last synchronization time.</p>
     * <p>In some future result, within {@link CameraCharacteristics#SYNC_MAX_LATENCY android.sync.maxLatency} frames,
     * this value will update to the actual frame number frame number
     * the result is guaranteed to be synchronized to (as long as the
     * request settings remain constant).</p>
     *
     * @see CameraCharacteristics#SYNC_MAX_LATENCY
     * @see CaptureResult#SYNC_FRAME_NUMBER
     * @hide
     */
    public static final int SYNC_FRAME_NUMBER_CONVERGING = -1;

    /**
     * <p>The current result's synchronization status is unknown.</p>
     * <p>The result may have already converged, or it may be in
     * progress.  Reading from this result may include some mix
     * of settings from past requests.</p>
     * <p>After a settings change, the new settings will eventually all
     * take effect for the output buffers and results. However, this
     * value will not change when that happens. Altering settings
     * rapidly may provide outcomes using mixes of settings from recent
     * requests.</p>
     * <p>This value is intended primarily for backwards compatibility with
     * the older camera implementations (for android.hardware.Camera).</p>
     * @see CaptureResult#SYNC_FRAME_NUMBER
     * @hide
     */
    public static final int SYNC_FRAME_NUMBER_UNKNOWN = -2;

    /*~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~
     * End generated code
     *~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~O@*/

}
