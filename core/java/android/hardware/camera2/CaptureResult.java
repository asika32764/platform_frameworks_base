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

package android.hardware.camera2;

import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.utils.TypeReference;
import android.util.Log;
import android.util.Rational;

import java.util.List;

/**
 * <p>The subset of the results of a single image capture from the image sensor.</p>
 *
 * <p>Contains a subset of the final configuration for the capture hardware (sensor, lens,
 * flash), the processing pipeline, the control algorithms, and the output
 * buffers.</p>
 *
 * <p>CaptureResults are produced by a {@link CameraDevice} after processing a
 * {@link CaptureRequest}. All properties listed for capture requests can also
 * be queried on the capture result, to determine the final values used for
 * capture. The result also includes additional metadata about the state of the
 * camera device during the capture.</p>
 *
 * <p>Not all properties returned by {@link CameraCharacteristics#getAvailableCaptureResultKeys()}
 * are necessarily available. Some results are {@link CaptureResult partial} and will
 * not have every key set. Only {@link TotalCaptureResult total} results are guaranteed to have
 * every key available that was enabled by the request.</p>
 *
 * <p>{@link CaptureResult} objects are immutable.</p>
 *
 */
public class CaptureResult extends CameraMetadata<CaptureResult.Key<?>> {

    private static final String TAG = "CaptureResult";
    private static final boolean VERBOSE = false;

    /**
     * A {@code Key} is used to do capture result field lookups with
     * {@link CaptureResult#get}.
     *
     * <p>For example, to get the timestamp corresponding to the exposure of the first row:
     * <code><pre>
     * long timestamp = captureResult.get(CaptureResult.SENSOR_TIMESTAMP);
     * </pre></code>
     * </p>
     *
     * <p>To enumerate over all possible keys for {@link CaptureResult}, see
     * {@link CameraCharacteristics#getAvailableCaptureResultKeys}.</p>
     *
     * @see CaptureResult#get
     * @see CameraCharacteristics#getAvailableCaptureResultKeys
     */
    public final static class Key<T> {
        private final CameraMetadataNative.Key<T> mKey;

        /**
         * Visible for testing and vendor extensions only.
         *
         * @hide
         */
        public Key(String name, Class<T> type) {
            mKey = new CameraMetadataNative.Key<T>(name, type);
        }

        /**
         * Visible for testing and vendor extensions only.
         *
         * @hide
         */
        public Key(String name, TypeReference<T> typeReference) {
            mKey = new CameraMetadataNative.Key<T>(name, typeReference);
        }

        /**
         * Return a camelCase, period separated name formatted like:
         * {@code "root.section[.subsections].name"}.
         *
         * <p>Built-in keys exposed by the Android SDK are always prefixed with {@code "android."};
         * keys that are device/platform-specific are prefixed with {@code "com."}.</p>
         *
         * <p>For example, {@code CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP} would
         * have a name of {@code "android.scaler.streamConfigurationMap"}; whereas a device
         * specific key might look like {@code "com.google.nexus.data.private"}.</p>
         *
         * @return String representation of the key name
         */
        public String getName() {
            return mKey.getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final int hashCode() {
            return mKey.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public final boolean equals(Object o) {
            return o instanceof Key && ((Key<T>)o).mKey.equals(mKey);
        }

        /**
         * Visible for CameraMetadataNative implementation only; do not use.
         *
         * TODO: Make this private or remove it altogether.
         *
         * @hide
         */
        public CameraMetadataNative.Key<T> getNativeKey() {
            return mKey;
        }

        @SuppressWarnings({ "unchecked" })
        /*package*/ Key(CameraMetadataNative.Key<?> nativeKey) {
            mKey = (CameraMetadataNative.Key<T>) nativeKey;
        }
    }

    private final CameraMetadataNative mResults;
    private final CaptureRequest mRequest;
    private final int mSequenceId;

    /**
     * Takes ownership of the passed-in properties object
     * @hide
     */
    public CaptureResult(CameraMetadataNative results, CaptureRequest parent, int sequenceId) {
        if (results == null) {
            throw new IllegalArgumentException("results was null");
        }

        if (parent == null) {
            throw new IllegalArgumentException("parent was null");
        }

        mResults = CameraMetadataNative.move(results);
        if (mResults.isEmpty()) {
            throw new AssertionError("Results must not be empty");
        }
        mRequest = parent;
        mSequenceId = sequenceId;
    }

    /**
     * Returns a copy of the underlying {@link CameraMetadataNative}.
     * @hide
     */
    public CameraMetadataNative getNativeCopy() {
        return new CameraMetadataNative(mResults);
    }

    /**
     * Creates a request-less result.
     *
     * <p><strong>For testing only.</strong></p>
     * @hide
     */
    public CaptureResult(CameraMetadataNative results, int sequenceId) {
        if (results == null) {
            throw new IllegalArgumentException("results was null");
        }

        mResults = CameraMetadataNative.move(results);
        if (mResults.isEmpty()) {
            throw new AssertionError("Results must not be empty");
        }

        mRequest = null;
        mSequenceId = sequenceId;
    }

    /**
     * Get a capture result field value.
     *
     * <p>The field definitions can be found in {@link CaptureResult}.</p>
     *
     * <p>Querying the value for the same key more than once will return a value
     * which is equal to the previous queried value.</p>
     *
     * @throws IllegalArgumentException if the key was not valid
     *
     * @param key The result field to read.
     * @return The value of that key, or {@code null} if the field is not set.
     */
    public <T> T get(Key<T> key) {
        T value = mResults.get(key);
        if (VERBOSE) Log.v(TAG, "#get for Key = " + key.getName() + ", returned value = " + value);
        return value;
    }

    /**
     * {@inheritDoc}
     * @hide
     */
    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getProtected(Key<?> key) {
        return (T) mResults.get(key);
    }

    /**
     * {@inheritDoc}
     * @hide
     */
    @SuppressWarnings("unchecked")
    @Override
    protected Class<Key<?>> getKeyClass() {
        Object thisClass = Key.class;
        return (Class<Key<?>>)thisClass;
    }

    /**
     * Dumps the native metadata contents to logcat.
     *
     * <p>Visibility for testing/debugging only. The results will not
     * include any synthesized keys, as they are invisible to the native layer.</p>
     *
     * @hide
     */
    public void dumpToLog() {
        mResults.dumpToLog();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Key<?>> getKeys() {
        // Force the javadoc for this function to show up on the CaptureResult page
        return super.getKeys();
    }

    /**
     * Get the request associated with this result.
     *
     * <p>Whenever a request has been fully or partially captured, with
     * {@link CameraDevice.CaptureListener#onCaptureCompleted} or
     * {@link CameraDevice.CaptureListener#onCaptureProgressed}, the {@code result}'s
     * {@code getRequest()} will return that {@code request}.
     * </p>
     *
     * <p>For example,
     * <code><pre>cameraDevice.capture(someRequest, new CaptureListener() {
     *     {@literal @}Override
     *     void onCaptureCompleted(CaptureRequest myRequest, CaptureResult myResult) {
     *         assert(myResult.getRequest.equals(myRequest) == true);
     *     }
     * }, null);
     * </code></pre>
     * </p>
     *
     * @return The request associated with this result. Never {@code null}.
     */
    public CaptureRequest getRequest() {
        return mRequest;
    }

    /**
     * Get the frame number associated with this result.
     *
     * <p>Whenever a request has been processed, regardless of failure or success,
     * it gets a unique frame number assigned to its future result/failure.</p>
     *
     * <p>This value monotonically increments, starting with 0,
     * for every new result or failure; and the scope is the lifetime of the
     * {@link CameraDevice}.</p>
     *
     * @return int frame number
     */
    public int getFrameNumber() {
        // TODO: @hide REQUEST_FRAME_COUNT
        return get(REQUEST_FRAME_COUNT);
    }

    /**
     * The sequence ID for this failure that was returned by the
     * {@link CameraDevice#capture} family of functions.
     *
     * <p>The sequence ID is a unique monotonically increasing value starting from 0,
     * incremented every time a new group of requests is submitted to the CameraDevice.</p>
     *
     * @return int The ID for the sequence of requests that this capture result is a part of
     *
     * @see CameraDevice.CaptureListener#onCaptureSequenceCompleted
     * @see CameraDevice.CaptureListener#onCaptureSequenceAborted
     */
    public int getSequenceId() {
        return mSequenceId;
    }

    /*@O~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~
     * The key entries below this point are generated from metadata
     * definitions in /system/media/camera/docs. Do not modify by hand or
     * modify the comment blocks at the start or end.
     *~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~*/


    /**
     * <p>The mode control selects how the image data is converted from the
     * sensor's native color into linear sRGB color.</p>
     * <p>When auto-white balance (AWB) is enabled with {@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode}, this
     * control is overridden by the AWB routine. When AWB is disabled, the
     * application controls how the color mapping is performed.</p>
     * <p>We define the expected processing pipeline below. For consistency
     * across devices, this is always the case with TRANSFORM_MATRIX.</p>
     * <p>When either FULL or HIGH_QUALITY is used, the camera device may
     * do additional processing but {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} and
     * {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform} will still be provided by the
     * camera device (in the results) and be roughly correct.</p>
     * <p>Switching to TRANSFORM_MATRIX and using the data provided from
     * FAST or HIGH_QUALITY will yield a picture with the same white point
     * as what was produced by the camera device in the earlier frame.</p>
     * <p>The expected processing pipeline is as follows:</p>
     * <p><img alt="White balance processing pipeline" src="../../../../images/camera2/metadata/android.colorCorrection.mode/processing_pipeline.png" /></p>
     * <p>The white balance is encoded by two values, a 4-channel white-balance
     * gain vector (applied in the Bayer domain), and a 3x3 color transform
     * matrix (applied after demosaic).</p>
     * <p>The 4-channel white-balance gains are defined as:</p>
     * <pre><code>{@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} = [ R G_even G_odd B ]
     * </code></pre>
     * <p>where <code>G_even</code> is the gain for green pixels on even rows of the
     * output, and <code>G_odd</code> is the gain for green pixels on the odd rows.
     * These may be identical for a given camera device implementation; if
     * the camera device does not support a separate gain for even/odd green
     * channels, it will use the <code>G_even</code> value, and write <code>G_odd</code> equal to
     * <code>G_even</code> in the output result metadata.</p>
     * <p>The matrices for color transforms are defined as a 9-entry vector:</p>
     * <pre><code>{@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform} = [ I0 I1 I2 I3 I4 I5 I6 I7 I8 ]
     * </code></pre>
     * <p>which define a transform from input sensor colors, <code>P_in = [ r g b ]</code>,
     * to output linear sRGB, <code>P_out = [ r' g' b' ]</code>,</p>
     * <p>with colors as follows:</p>
     * <pre><code>r' = I0r + I1g + I2b
     * g' = I3r + I4g + I5b
     * b' = I6r + I7g + I8b
     * </code></pre>
     * <p>Both the input and output value ranges must match. Overflow/underflow
     * values are clipped to fit within the range.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_AWB_MODE
     * @see #COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
     * @see #COLOR_CORRECTION_MODE_FAST
     * @see #COLOR_CORRECTION_MODE_HIGH_QUALITY
     */
    public static final Key<Integer> COLOR_CORRECTION_MODE =
            new Key<Integer>("android.colorCorrection.mode", int.class);

    /**
     * <p>A color transform matrix to use to transform
     * from sensor RGB color space to output linear sRGB color space.</p>
     * <p>This matrix is either set by the camera device when the request
     * {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode} is not TRANSFORM_MATRIX, or
     * directly by the application in the request when the
     * {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode} is TRANSFORM_MATRIX.</p>
     * <p>In the latter case, the camera device may round the matrix to account
     * for precision issues; the final rounded matrix should be reported back
     * in this matrix result metadata. The transform should keep the magnitude
     * of the output color values within <code>[0, 1.0]</code> (assuming input color
     * values is within the normalized range <code>[0, 1.0]</code>), or clipping may occur.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_MODE
     */
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> COLOR_CORRECTION_TRANSFORM =
            new Key<android.hardware.camera2.params.ColorSpaceTransform>("android.colorCorrection.transform", android.hardware.camera2.params.ColorSpaceTransform.class);

    /**
     * <p>Gains applying to Bayer raw color channels for
     * white-balance.</p>
     * <p>These per-channel gains are either set by the camera device
     * when the request {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode} is not
     * TRANSFORM_MATRIX, or directly by the application in the
     * request when the {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode} is
     * TRANSFORM_MATRIX.</p>
     * <p>The gains in the result metadata are the gains actually
     * applied by the camera device to the current frame.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_MODE
     */
    public static final Key<android.hardware.camera2.params.RggbChannelVector> COLOR_CORRECTION_GAINS =
            new Key<android.hardware.camera2.params.RggbChannelVector>("android.colorCorrection.gains", android.hardware.camera2.params.RggbChannelVector.class);

    /**
     * <p>The desired setting for the camera device's auto-exposure
     * algorithm's antibanding compensation.</p>
     * <p>Some kinds of lighting fixtures, such as some fluorescent
     * lights, flicker at the rate of the power supply frequency
     * (60Hz or 50Hz, depending on country). While this is
     * typically not noticeable to a person, it can be visible to
     * a camera device. If a camera sets its exposure time to the
     * wrong value, the flicker may become visible in the
     * viewfinder as flicker or in a final captured image, as a
     * set of variable-brightness bands across the image.</p>
     * <p>Therefore, the auto-exposure routines of camera devices
     * include antibanding routines that ensure that the chosen
     * exposure value will not cause such banding. The choice of
     * exposure time depends on the rate of flicker, which the
     * camera device can detect automatically, or the expected
     * rate can be selected by the application using this
     * control.</p>
     * <p>A given camera device may not support all of the possible
     * options for the antibanding mode. The
     * {@link CameraCharacteristics#CONTROL_AE_AVAILABLE_ANTIBANDING_MODES android.control.aeAvailableAntibandingModes} key contains
     * the available modes for a given camera device.</p>
     * <p>The default mode is AUTO, which must be supported by all
     * camera devices.</p>
     * <p>If manual exposure control is enabled (by setting
     * {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} or {@link CaptureRequest#CONTROL_MODE android.control.mode} to OFF),
     * then this setting has no effect, and the application must
     * ensure it selects exposure times that do not cause banding
     * issues. The {@link CaptureResult#STATISTICS_SCENE_FLICKER android.statistics.sceneFlicker} key can assist
     * the application in this.</p>
     *
     * @see CameraCharacteristics#CONTROL_AE_AVAILABLE_ANTIBANDING_MODES
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureRequest#CONTROL_MODE
     * @see CaptureResult#STATISTICS_SCENE_FLICKER
     * @see #CONTROL_AE_ANTIBANDING_MODE_OFF
     * @see #CONTROL_AE_ANTIBANDING_MODE_50HZ
     * @see #CONTROL_AE_ANTIBANDING_MODE_60HZ
     * @see #CONTROL_AE_ANTIBANDING_MODE_AUTO
     */
    public static final Key<Integer> CONTROL_AE_ANTIBANDING_MODE =
            new Key<Integer>("android.control.aeAntibandingMode", int.class);

    /**
     * <p>Adjustment to auto-exposure (AE) target image
     * brightness.</p>
     * <p>The adjustment is measured as a count of steps, with the
     * step size defined by {@link CameraCharacteristics#CONTROL_AE_COMPENSATION_STEP android.control.aeCompensationStep} and the
     * allowed range by {@link CameraCharacteristics#CONTROL_AE_COMPENSATION_RANGE android.control.aeCompensationRange}.</p>
     * <p>For example, if the exposure value (EV) step is 0.333, '6'
     * will mean an exposure compensation of +2 EV; -3 will mean an
     * exposure compensation of -1 EV. One EV represents a doubling
     * of image brightness. Note that this control will only be
     * effective if {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} <code>!=</code> OFF. This control
     * will take effect even when {@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} <code>== true</code>.</p>
     * <p>In the event of exposure compensation value being changed, camera device
     * may take several frames to reach the newly requested exposure target.
     * During that time, {@link CaptureResult#CONTROL_AE_STATE android.control.aeState} field will be in the SEARCHING
     * state. Once the new exposure target is reached, {@link CaptureResult#CONTROL_AE_STATE android.control.aeState} will
     * change from SEARCHING to either CONVERGED, LOCKED (if AE lock is enabled), or
     * FLASH_REQUIRED (if the scene is too dark for still capture).</p>
     *
     * @see CameraCharacteristics#CONTROL_AE_COMPENSATION_RANGE
     * @see CameraCharacteristics#CONTROL_AE_COMPENSATION_STEP
     * @see CaptureRequest#CONTROL_AE_LOCK
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureResult#CONTROL_AE_STATE
     */
    public static final Key<Integer> CONTROL_AE_EXPOSURE_COMPENSATION =
            new Key<Integer>("android.control.aeExposureCompensation", int.class);

    /**
     * <p>Whether auto-exposure (AE) is currently locked to its latest
     * calculated values.</p>
     * <p>Note that even when AE is locked, the flash may be
     * fired if the {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} is ON_AUTO_FLASH / ON_ALWAYS_FLASH /
     * ON_AUTO_FLASH_REDEYE.</p>
     * <p>When {@link CaptureRequest#CONTROL_AE_EXPOSURE_COMPENSATION android.control.aeExposureCompensation} is changed, even if the AE lock
     * is ON, the camera device will still adjust its exposure value.</p>
     * <p>If AE precapture is triggered (see {@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER android.control.aePrecaptureTrigger})
     * when AE is already locked, the camera device will not change the exposure time
     * ({@link CaptureRequest#SENSOR_EXPOSURE_TIME android.sensor.exposureTime}) and sensitivity ({@link CaptureRequest#SENSOR_SENSITIVITY android.sensor.sensitivity})
     * parameters. The flash may be fired if the {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode}
     * is ON_AUTO_FLASH/ON_AUTO_FLASH_REDEYE and the scene is too dark. If the
     * {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} is ON_ALWAYS_FLASH, the scene may become overexposed.</p>
     * <p>See {@link CaptureResult#CONTROL_AE_STATE android.control.aeState} for AE lock related state transition details.</p>
     *
     * @see CaptureRequest#CONTROL_AE_EXPOSURE_COMPENSATION
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
     * @see CaptureResult#CONTROL_AE_STATE
     * @see CaptureRequest#SENSOR_EXPOSURE_TIME
     * @see CaptureRequest#SENSOR_SENSITIVITY
     */
    public static final Key<Boolean> CONTROL_AE_LOCK =
            new Key<Boolean>("android.control.aeLock", boolean.class);

    /**
     * <p>The desired mode for the camera device's
     * auto-exposure routine.</p>
     * <p>This control is only effective if {@link CaptureRequest#CONTROL_MODE android.control.mode} is
     * AUTO.</p>
     * <p>When set to any of the ON modes, the camera device's
     * auto-exposure routine is enabled, overriding the
     * application's selected exposure time, sensor sensitivity,
     * and frame duration ({@link CaptureRequest#SENSOR_EXPOSURE_TIME android.sensor.exposureTime},
     * {@link CaptureRequest#SENSOR_SENSITIVITY android.sensor.sensitivity}, and
     * {@link CaptureRequest#SENSOR_FRAME_DURATION android.sensor.frameDuration}). If one of the FLASH modes
     * is selected, the camera device's flash unit controls are
     * also overridden.</p>
     * <p>The FLASH modes are only available if the camera device
     * has a flash unit ({@link CameraCharacteristics#FLASH_INFO_AVAILABLE android.flash.info.available} is <code>true</code>).</p>
     * <p>If flash TORCH mode is desired, this field must be set to
     * ON or OFF, and {@link CaptureRequest#FLASH_MODE android.flash.mode} set to TORCH.</p>
     * <p>When set to any of the ON modes, the values chosen by the
     * camera device auto-exposure routine for the overridden
     * fields for a given capture will be available in its
     * CaptureResult.</p>
     *
     * @see CaptureRequest#CONTROL_MODE
     * @see CameraCharacteristics#FLASH_INFO_AVAILABLE
     * @see CaptureRequest#FLASH_MODE
     * @see CaptureRequest#SENSOR_EXPOSURE_TIME
     * @see CaptureRequest#SENSOR_FRAME_DURATION
     * @see CaptureRequest#SENSOR_SENSITIVITY
     * @see #CONTROL_AE_MODE_OFF
     * @see #CONTROL_AE_MODE_ON
     * @see #CONTROL_AE_MODE_ON_AUTO_FLASH
     * @see #CONTROL_AE_MODE_ON_ALWAYS_FLASH
     * @see #CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE
     */
    public static final Key<Integer> CONTROL_AE_MODE =
            new Key<Integer>("android.control.aeMode", int.class);

    /**
     * <p>List of areas to use for
     * metering.</p>
     * <p>The coordinate system is based on the active pixel array,
     * with (0,0) being the top-left pixel in the active pixel array, and
     * ({@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.width - 1,
     * {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.height - 1) being the
     * bottom-right pixel in the active pixel array.</p>
     * <p>The weight must range from 0 to 1000, and represents a weight
     * for every pixel in the area. This means that a large metering area
     * with the same weight as a smaller area will have more effect in
     * the metering result. Metering areas can partially overlap and the
     * camera device will add the weights in the overlap region.</p>
     * <p>If all regions have 0 weight, then no specific metering area
     * needs to be used by the camera device. If the metering region is
     * outside the used {@link CaptureRequest#SCALER_CROP_REGION android.scaler.cropRegion} returned in capture result metadata,
     * the camera device will ignore the sections outside the region and output the
     * used sections in the result metadata.</p>
     *
     * @see CaptureRequest#SCALER_CROP_REGION
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     */
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AE_REGIONS =
            new Key<android.hardware.camera2.params.MeteringRectangle[]>("android.control.aeRegions", android.hardware.camera2.params.MeteringRectangle[].class);

    /**
     * <p>Range over which fps can be adjusted to
     * maintain exposure.</p>
     * <p>Only constrains auto-exposure (AE) algorithm, not
     * manual control of {@link CaptureRequest#SENSOR_EXPOSURE_TIME android.sensor.exposureTime}</p>
     *
     * @see CaptureRequest#SENSOR_EXPOSURE_TIME
     */
    public static final Key<android.util.Range<Integer>> CONTROL_AE_TARGET_FPS_RANGE =
            new Key<android.util.Range<Integer>>("android.control.aeTargetFpsRange", new TypeReference<android.util.Range<Integer>>() {{ }});

    /**
     * <p>Whether the camera device will trigger a precapture
     * metering sequence when it processes this request.</p>
     * <p>This entry is normally set to IDLE, or is not
     * included at all in the request settings. When included and
     * set to START, the camera device will trigger the autoexposure
     * precapture metering sequence.</p>
     * <p>The precapture sequence should triggered before starting a
     * high-quality still capture for final metering decisions to
     * be made, and for firing pre-capture flash pulses to estimate
     * scene brightness and required final capture flash power, when
     * the flash is enabled.</p>
     * <p>Normally, this entry should be set to START for only a
     * single request, and the application should wait until the
     * sequence completes before starting a new one.</p>
     * <p>The exact effect of auto-exposure (AE) precapture trigger
     * depends on the current AE mode and state; see
     * {@link CaptureResult#CONTROL_AE_STATE android.control.aeState} for AE precapture state transition
     * details.</p>
     *
     * @see CaptureResult#CONTROL_AE_STATE
     * @see #CONTROL_AE_PRECAPTURE_TRIGGER_IDLE
     * @see #CONTROL_AE_PRECAPTURE_TRIGGER_START
     */
    public static final Key<Integer> CONTROL_AE_PRECAPTURE_TRIGGER =
            new Key<Integer>("android.control.aePrecaptureTrigger", int.class);

    /**
     * <p>Current state of the auto-exposure (AE) algorithm.</p>
     * <p>Switching between or enabling AE modes ({@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode}) always
     * resets the AE state to INACTIVE. Similarly, switching between {@link CaptureRequest#CONTROL_MODE android.control.mode},
     * or {@link CaptureRequest#CONTROL_SCENE_MODE android.control.sceneMode} if <code>{@link CaptureRequest#CONTROL_MODE android.control.mode} == USE_SCENE_MODE</code> resets all
     * the algorithm states to INACTIVE.</p>
     * <p>The camera device can do several state transitions between two results, if it is
     * allowed by the state transition table. For example: INACTIVE may never actually be
     * seen in a result.</p>
     * <p>The state in the result is the state for this image (in sync with this image): if
     * AE state becomes CONVERGED, then the image data associated with this result should
     * be good to use.</p>
     * <p>Below are state transition tables for different AE modes.</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center"></td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Camera device auto exposure algorithm is disabled</td>
     * </tr>
     * </tbody>
     * </table>
     * <p>When {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} is AE_MODE_ON_*:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">Camera device initiates AE scan</td>
     * <td align="center">SEARCHING</td>
     * <td align="center">Values changing</td>
     * </tr>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is ON</td>
     * <td align="center">LOCKED</td>
     * <td align="center">Values locked</td>
     * </tr>
     * <tr>
     * <td align="center">SEARCHING</td>
     * <td align="center">Camera device finishes AE scan</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Good values, not changing</td>
     * </tr>
     * <tr>
     * <td align="center">SEARCHING</td>
     * <td align="center">Camera device finishes AE scan</td>
     * <td align="center">FLASH_REQUIRED</td>
     * <td align="center">Converged but too dark w/o flash</td>
     * </tr>
     * <tr>
     * <td align="center">SEARCHING</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is ON</td>
     * <td align="center">LOCKED</td>
     * <td align="center">Values locked</td>
     * </tr>
     * <tr>
     * <td align="center">CONVERGED</td>
     * <td align="center">Camera device initiates AE scan</td>
     * <td align="center">SEARCHING</td>
     * <td align="center">Values changing</td>
     * </tr>
     * <tr>
     * <td align="center">CONVERGED</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is ON</td>
     * <td align="center">LOCKED</td>
     * <td align="center">Values locked</td>
     * </tr>
     * <tr>
     * <td align="center">FLASH_REQUIRED</td>
     * <td align="center">Camera device initiates AE scan</td>
     * <td align="center">SEARCHING</td>
     * <td align="center">Values changing</td>
     * </tr>
     * <tr>
     * <td align="center">FLASH_REQUIRED</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is ON</td>
     * <td align="center">LOCKED</td>
     * <td align="center">Values locked</td>
     * </tr>
     * <tr>
     * <td align="center">LOCKED</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is OFF</td>
     * <td align="center">SEARCHING</td>
     * <td align="center">Values not good after unlock</td>
     * </tr>
     * <tr>
     * <td align="center">LOCKED</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is OFF</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Values good after unlock</td>
     * </tr>
     * <tr>
     * <td align="center">LOCKED</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is OFF</td>
     * <td align="center">FLASH_REQUIRED</td>
     * <td align="center">Exposure good, but too dark</td>
     * </tr>
     * <tr>
     * <td align="center">PRECAPTURE</td>
     * <td align="center">Sequence done. {@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is OFF</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Ready for high-quality capture</td>
     * </tr>
     * <tr>
     * <td align="center">PRECAPTURE</td>
     * <td align="center">Sequence done. {@link CaptureRequest#CONTROL_AE_LOCK android.control.aeLock} is ON</td>
     * <td align="center">LOCKED</td>
     * <td align="center">Ready for high-quality capture</td>
     * </tr>
     * <tr>
     * <td align="center">Any state</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER android.control.aePrecaptureTrigger} is START</td>
     * <td align="center">PRECAPTURE</td>
     * <td align="center">Start AE precapture metering sequence</td>
     * </tr>
     * </tbody>
     * </table>
     * <p>For the above table, the camera device may skip reporting any state changes that happen
     * without application intervention (i.e. mode switch, trigger, locking). Any state that
     * can be skipped in that manner is called a transient state.</p>
     * <p>For example, for above AE modes (AE_MODE_ON_*), in addition to the state transitions
     * listed in above table, it is also legal for the camera device to skip one or more
     * transient states between two results. See below table for examples:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">Camera device finished AE scan</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Values are already good, transient states are skipped by camera device.</td>
     * </tr>
     * <tr>
     * <td align="center">Any state</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER android.control.aePrecaptureTrigger} is START, sequence done</td>
     * <td align="center">FLASH_REQUIRED</td>
     * <td align="center">Converged but too dark w/o flash after a precapture sequence, transient states are skipped by camera device.</td>
     * </tr>
     * <tr>
     * <td align="center">Any state</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER android.control.aePrecaptureTrigger} is START, sequence done</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Converged after a precapture sequence, transient states are skipped by camera device.</td>
     * </tr>
     * <tr>
     * <td align="center">CONVERGED</td>
     * <td align="center">Camera device finished AE scan</td>
     * <td align="center">FLASH_REQUIRED</td>
     * <td align="center">Converged but too dark w/o flash after a new scan, transient states are skipped by camera device.</td>
     * </tr>
     * <tr>
     * <td align="center">FLASH_REQUIRED</td>
     * <td align="center">Camera device finished AE scan</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Converged after a new scan, transient states are skipped by camera device.</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @see CaptureRequest#CONTROL_AE_LOCK
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
     * @see CaptureRequest#CONTROL_MODE
     * @see CaptureRequest#CONTROL_SCENE_MODE
     * @see #CONTROL_AE_STATE_INACTIVE
     * @see #CONTROL_AE_STATE_SEARCHING
     * @see #CONTROL_AE_STATE_CONVERGED
     * @see #CONTROL_AE_STATE_LOCKED
     * @see #CONTROL_AE_STATE_FLASH_REQUIRED
     * @see #CONTROL_AE_STATE_PRECAPTURE
     */
    public static final Key<Integer> CONTROL_AE_STATE =
            new Key<Integer>("android.control.aeState", int.class);

    /**
     * <p>Whether auto-focus (AF) is currently enabled, and what
     * mode it is set to.</p>
     * <p>Only effective if {@link CaptureRequest#CONTROL_MODE android.control.mode} = AUTO and the lens is not fixed focus
     * (i.e. <code>{@link CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE android.lens.info.minimumFocusDistance} &gt; 0</code>).</p>
     * <p>If the lens is controlled by the camera device auto-focus algorithm,
     * the camera device will report the current AF status in {@link CaptureResult#CONTROL_AF_STATE android.control.afState}
     * in result metadata.</p>
     *
     * @see CaptureResult#CONTROL_AF_STATE
     * @see CaptureRequest#CONTROL_MODE
     * @see CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE
     * @see #CONTROL_AF_MODE_OFF
     * @see #CONTROL_AF_MODE_AUTO
     * @see #CONTROL_AF_MODE_MACRO
     * @see #CONTROL_AF_MODE_CONTINUOUS_VIDEO
     * @see #CONTROL_AF_MODE_CONTINUOUS_PICTURE
     * @see #CONTROL_AF_MODE_EDOF
     */
    public static final Key<Integer> CONTROL_AF_MODE =
            new Key<Integer>("android.control.afMode", int.class);

    /**
     * <p>List of areas to use for focus
     * estimation.</p>
     * <p>The coordinate system is based on the active pixel array,
     * with (0,0) being the top-left pixel in the active pixel array, and
     * ({@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.width - 1,
     * {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.height - 1) being the
     * bottom-right pixel in the active pixel array.</p>
     * <p>The weight must range from 0 to 1000, and represents a weight
     * for every pixel in the area. This means that a large metering area
     * with the same weight as a smaller area will have more effect in
     * the metering result. Metering areas can partially overlap and the
     * camera device will add the weights in the overlap region.</p>
     * <p>If all regions have 0 weight, then no specific metering area
     * needs to be used by the camera device. If the metering region is
     * outside the used {@link CaptureRequest#SCALER_CROP_REGION android.scaler.cropRegion} returned in capture result metadata,
     * the camera device will ignore the sections outside the region and output the
     * used sections in the result metadata.</p>
     *
     * @see CaptureRequest#SCALER_CROP_REGION
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     */
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AF_REGIONS =
            new Key<android.hardware.camera2.params.MeteringRectangle[]>("android.control.afRegions", android.hardware.camera2.params.MeteringRectangle[].class);

    /**
     * <p>Whether the camera device will trigger autofocus for this request.</p>
     * <p>This entry is normally set to IDLE, or is not
     * included at all in the request settings.</p>
     * <p>When included and set to START, the camera device will trigger the
     * autofocus algorithm. If autofocus is disabled, this trigger has no effect.</p>
     * <p>When set to CANCEL, the camera device will cancel any active trigger,
     * and return to its initial AF state.</p>
     * <p>Generally, applications should set this entry to START or CANCEL for only a
     * single capture, and then return it to IDLE (or not set at all). Specifying
     * START for multiple captures in a row means restarting the AF operation over
     * and over again.</p>
     * <p>See {@link CaptureResult#CONTROL_AF_STATE android.control.afState} for what the trigger means for each AF mode.</p>
     *
     * @see CaptureResult#CONTROL_AF_STATE
     * @see #CONTROL_AF_TRIGGER_IDLE
     * @see #CONTROL_AF_TRIGGER_START
     * @see #CONTROL_AF_TRIGGER_CANCEL
     */
    public static final Key<Integer> CONTROL_AF_TRIGGER =
            new Key<Integer>("android.control.afTrigger", int.class);

    /**
     * <p>Current state of auto-focus (AF) algorithm.</p>
     * <p>Switching between or enabling AF modes ({@link CaptureRequest#CONTROL_AF_MODE android.control.afMode}) always
     * resets the AF state to INACTIVE. Similarly, switching between {@link CaptureRequest#CONTROL_MODE android.control.mode},
     * or {@link CaptureRequest#CONTROL_SCENE_MODE android.control.sceneMode} if <code>{@link CaptureRequest#CONTROL_MODE android.control.mode} == USE_SCENE_MODE</code> resets all
     * the algorithm states to INACTIVE.</p>
     * <p>The camera device can do several state transitions between two results, if it is
     * allowed by the state transition table. For example: INACTIVE may never actually be
     * seen in a result.</p>
     * <p>The state in the result is the state for this image (in sync with this image): if
     * AF state becomes FOCUSED, then the image data associated with this result should
     * be sharp.</p>
     * <p>Below are state transition tables for different AF modes.</p>
     * <p>When {@link CaptureRequest#CONTROL_AF_MODE android.control.afMode} is AF_MODE_OFF or AF_MODE_EDOF:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center"></td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Never changes</td>
     * </tr>
     * </tbody>
     * </table>
     * <p>When {@link CaptureRequest#CONTROL_AF_MODE android.control.afMode} is AF_MODE_AUTO or AF_MODE_MACRO:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">ACTIVE_SCAN</td>
     * <td align="center">Start AF sweep, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">ACTIVE_SCAN</td>
     * <td align="center">AF sweep done</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">Focused, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">ACTIVE_SCAN</td>
     * <td align="center">AF sweep done</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">Not focused, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">ACTIVE_SCAN</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Cancel/reset AF, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Cancel/reset AF</td>
     * </tr>
     * <tr>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">ACTIVE_SCAN</td>
     * <td align="center">Start new sweep, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Cancel/reset AF</td>
     * </tr>
     * <tr>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">ACTIVE_SCAN</td>
     * <td align="center">Start new sweep, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">Any state</td>
     * <td align="center">Mode change</td>
     * <td align="center">INACTIVE</td>
     * <td align="center"></td>
     * </tr>
     * </tbody>
     * </table>
     * <p>For the above table, the camera device may skip reporting any state changes that happen
     * without application intervention (i.e. mode switch, trigger, locking). Any state that
     * can be skipped in that manner is called a transient state.</p>
     * <p>For example, for these AF modes (AF_MODE_AUTO and AF_MODE_MACRO), in addition to the
     * state transitions listed in above table, it is also legal for the camera device to skip
     * one or more transient states between two results. See below table for examples:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">Focus is already good or good after a scan, lens is now locked.</td>
     * </tr>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">Focus failed after a scan, lens is now locked.</td>
     * </tr>
     * <tr>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">Focus is already good or good after a scan, lens is now locked.</td>
     * </tr>
     * <tr>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">Focus is good after a scan, lens is not locked.</td>
     * </tr>
     * </tbody>
     * </table>
     * <p>When {@link CaptureRequest#CONTROL_AF_MODE android.control.afMode} is AF_MODE_CONTINUOUS_VIDEO:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">Camera device initiates new scan</td>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Start AF scan, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF state query, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Camera device completes current scan</td>
     * <td align="center">PASSIVE_FOCUSED</td>
     * <td align="center">End AF scan, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Camera device fails current scan</td>
     * <td align="center">PASSIVE_UNFOCUSED</td>
     * <td align="center">End AF scan, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">Immediate transition, if focus is good. Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">Immediate transition, if focus is bad. Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Reset lens position, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_FOCUSED</td>
     * <td align="center">Camera device initiates new scan</td>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Start AF scan, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_UNFOCUSED</td>
     * <td align="center">Camera device initiates new scan</td>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Start AF scan, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_FOCUSED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">Immediate transition, lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_UNFOCUSED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">Immediate transition, lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">No effect</td>
     * </tr>
     * <tr>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Restart AF scan</td>
     * </tr>
     * <tr>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">No effect</td>
     * </tr>
     * <tr>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Restart AF scan</td>
     * </tr>
     * </tbody>
     * </table>
     * <p>When {@link CaptureRequest#CONTROL_AF_MODE android.control.afMode} is AF_MODE_CONTINUOUS_PICTURE:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">Camera device initiates new scan</td>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Start AF scan, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF state query, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Camera device completes current scan</td>
     * <td align="center">PASSIVE_FOCUSED</td>
     * <td align="center">End AF scan, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Camera device fails current scan</td>
     * <td align="center">PASSIVE_UNFOCUSED</td>
     * <td align="center">End AF scan, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">Eventual transition once the focus is good. Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">Eventual transition if cannot find focus. Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Reset lens position, Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_FOCUSED</td>
     * <td align="center">Camera device initiates new scan</td>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Start AF scan, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_UNFOCUSED</td>
     * <td align="center">Camera device initiates new scan</td>
     * <td align="center">PASSIVE_SCAN</td>
     * <td align="center">Start AF scan, Lens now moving</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_FOCUSED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">Immediate trans. Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">PASSIVE_UNFOCUSED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">Immediate trans. Lens now locked</td>
     * </tr>
     * <tr>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">No effect</td>
     * </tr>
     * <tr>
     * <td align="center">FOCUSED_LOCKED</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Restart AF scan</td>
     * </tr>
     * <tr>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF_TRIGGER</td>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">No effect</td>
     * </tr>
     * <tr>
     * <td align="center">NOT_FOCUSED_LOCKED</td>
     * <td align="center">AF_CANCEL</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Restart AF scan</td>
     * </tr>
     * </tbody>
     * </table>
     * <p>When switch between AF_MODE_CONTINUOUS_* (CAF modes) and AF_MODE_AUTO/AF_MODE_MACRO
     * (AUTO modes), the initial INACTIVE or PASSIVE_SCAN states may be skipped by the
     * camera device. When a trigger is included in a mode switch request, the trigger
     * will be evaluated in the context of the new mode in the request.
     * See below table for examples:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">any state</td>
     * <td align="center">CAF--&gt;AUTO mode switch</td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Mode switch without trigger, initial state must be INACTIVE</td>
     * </tr>
     * <tr>
     * <td align="center">any state</td>
     * <td align="center">CAF--&gt;AUTO mode switch with AF_TRIGGER</td>
     * <td align="center">trigger-reachable states from INACTIVE</td>
     * <td align="center">Mode switch with trigger, INACTIVE is skipped</td>
     * </tr>
     * <tr>
     * <td align="center">any state</td>
     * <td align="center">AUTO--&gt;CAF mode switch</td>
     * <td align="center">passively reachable states from INACTIVE</td>
     * <td align="center">Mode switch without trigger, passive transient state is skipped</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @see CaptureRequest#CONTROL_AF_MODE
     * @see CaptureRequest#CONTROL_MODE
     * @see CaptureRequest#CONTROL_SCENE_MODE
     * @see #CONTROL_AF_STATE_INACTIVE
     * @see #CONTROL_AF_STATE_PASSIVE_SCAN
     * @see #CONTROL_AF_STATE_PASSIVE_FOCUSED
     * @see #CONTROL_AF_STATE_ACTIVE_SCAN
     * @see #CONTROL_AF_STATE_FOCUSED_LOCKED
     * @see #CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
     * @see #CONTROL_AF_STATE_PASSIVE_UNFOCUSED
     */
    public static final Key<Integer> CONTROL_AF_STATE =
            new Key<Integer>("android.control.afState", int.class);

    /**
     * <p>Whether auto-white balance (AWB) is currently locked to its
     * latest calculated values.</p>
     * <p>Note that AWB lock is only meaningful when
     * {@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode} is in the AUTO mode; in other modes,
     * AWB is already fixed to a specific setting.</p>
     *
     * @see CaptureRequest#CONTROL_AWB_MODE
     */
    public static final Key<Boolean> CONTROL_AWB_LOCK =
            new Key<Boolean>("android.control.awbLock", boolean.class);

    /**
     * <p>Whether auto-white balance (AWB) is currently setting the color
     * transform fields, and what its illumination target
     * is.</p>
     * <p>This control is only effective if {@link CaptureRequest#CONTROL_MODE android.control.mode} is AUTO.</p>
     * <p>When set to the ON mode, the camera device's auto-white balance
     * routine is enabled, overriding the application's selected
     * {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}, {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} and
     * {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode}.</p>
     * <p>When set to the OFF mode, the camera device's auto-white balance
     * routine is disabled. The application manually controls the white
     * balance by {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform}, {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains}
     * and {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode}.</p>
     * <p>When set to any other modes, the camera device's auto-white
     * balance routine is disabled. The camera device uses each
     * particular illumination target for white balance
     * adjustment. The application's values for
     * {@link CaptureRequest#COLOR_CORRECTION_TRANSFORM android.colorCorrection.transform},
     * {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} and
     * {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode} are ignored.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @see CaptureRequest#COLOR_CORRECTION_MODE
     * @see CaptureRequest#COLOR_CORRECTION_TRANSFORM
     * @see CaptureRequest#CONTROL_MODE
     * @see #CONTROL_AWB_MODE_OFF
     * @see #CONTROL_AWB_MODE_AUTO
     * @see #CONTROL_AWB_MODE_INCANDESCENT
     * @see #CONTROL_AWB_MODE_FLUORESCENT
     * @see #CONTROL_AWB_MODE_WARM_FLUORESCENT
     * @see #CONTROL_AWB_MODE_DAYLIGHT
     * @see #CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
     * @see #CONTROL_AWB_MODE_TWILIGHT
     * @see #CONTROL_AWB_MODE_SHADE
     */
    public static final Key<Integer> CONTROL_AWB_MODE =
            new Key<Integer>("android.control.awbMode", int.class);

    /**
     * <p>List of areas to use for illuminant
     * estimation.</p>
     * <p>The coordinate system is based on the active pixel array,
     * with (0,0) being the top-left pixel in the active pixel array, and
     * ({@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.width - 1,
     * {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.height - 1) being the
     * bottom-right pixel in the active pixel array.</p>
     * <p>The weight must range from 0 to 1000, and represents a weight
     * for every pixel in the area. This means that a large metering area
     * with the same weight as a smaller area will have more effect in
     * the metering result. Metering areas can partially overlap and the
     * camera device will add the weights in the overlap region.</p>
     * <p>If all regions have 0 weight, then no specific metering area
     * needs to be used by the camera device. If the metering region is
     * outside the used {@link CaptureRequest#SCALER_CROP_REGION android.scaler.cropRegion} returned in capture result metadata,
     * the camera device will ignore the sections outside the region and output the
     * used sections in the result metadata.</p>
     *
     * @see CaptureRequest#SCALER_CROP_REGION
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     */
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AWB_REGIONS =
            new Key<android.hardware.camera2.params.MeteringRectangle[]>("android.control.awbRegions", android.hardware.camera2.params.MeteringRectangle[].class);

    /**
     * <p>Information to the camera device 3A (auto-exposure,
     * auto-focus, auto-white balance) routines about the purpose
     * of this capture, to help the camera device to decide optimal 3A
     * strategy.</p>
     * <p>This control (except for MANUAL) is only effective if
     * <code>{@link CaptureRequest#CONTROL_MODE android.control.mode} != OFF</code> and any 3A routine is active.</p>
     * <p>ZERO_SHUTTER_LAG will be supported if {@link CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES android.request.availableCapabilities}
     * contains ZSL. MANUAL will be supported if {@link CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES android.request.availableCapabilities}
     * contains MANUAL_SENSOR.</p>
     *
     * @see CaptureRequest#CONTROL_MODE
     * @see CameraCharacteristics#REQUEST_AVAILABLE_CAPABILITIES
     * @see #CONTROL_CAPTURE_INTENT_CUSTOM
     * @see #CONTROL_CAPTURE_INTENT_PREVIEW
     * @see #CONTROL_CAPTURE_INTENT_STILL_CAPTURE
     * @see #CONTROL_CAPTURE_INTENT_VIDEO_RECORD
     * @see #CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT
     * @see #CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG
     * @see #CONTROL_CAPTURE_INTENT_MANUAL
     */
    public static final Key<Integer> CONTROL_CAPTURE_INTENT =
            new Key<Integer>("android.control.captureIntent", int.class);

    /**
     * <p>Current state of auto-white balance (AWB) algorithm.</p>
     * <p>Switching between or enabling AWB modes ({@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode}) always
     * resets the AWB state to INACTIVE. Similarly, switching between {@link CaptureRequest#CONTROL_MODE android.control.mode},
     * or {@link CaptureRequest#CONTROL_SCENE_MODE android.control.sceneMode} if <code>{@link CaptureRequest#CONTROL_MODE android.control.mode} == USE_SCENE_MODE</code> resets all
     * the algorithm states to INACTIVE.</p>
     * <p>The camera device can do several state transitions between two results, if it is
     * allowed by the state transition table. So INACTIVE may never actually be seen in
     * a result.</p>
     * <p>The state in the result is the state for this image (in sync with this image): if
     * AWB state becomes CONVERGED, then the image data associated with this result should
     * be good to use.</p>
     * <p>Below are state transition tables for different AWB modes.</p>
     * <p>When <code>{@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode} != AWB_MODE_AUTO</code>:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center"></td>
     * <td align="center">INACTIVE</td>
     * <td align="center">Camera device auto white balance algorithm is disabled</td>
     * </tr>
     * </tbody>
     * </table>
     * <p>When {@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode} is AWB_MODE_AUTO:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">Camera device initiates AWB scan</td>
     * <td align="center">SEARCHING</td>
     * <td align="center">Values changing</td>
     * </tr>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AWB_LOCK android.control.awbLock} is ON</td>
     * <td align="center">LOCKED</td>
     * <td align="center">Values locked</td>
     * </tr>
     * <tr>
     * <td align="center">SEARCHING</td>
     * <td align="center">Camera device finishes AWB scan</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Good values, not changing</td>
     * </tr>
     * <tr>
     * <td align="center">SEARCHING</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AWB_LOCK android.control.awbLock} is ON</td>
     * <td align="center">LOCKED</td>
     * <td align="center">Values locked</td>
     * </tr>
     * <tr>
     * <td align="center">CONVERGED</td>
     * <td align="center">Camera device initiates AWB scan</td>
     * <td align="center">SEARCHING</td>
     * <td align="center">Values changing</td>
     * </tr>
     * <tr>
     * <td align="center">CONVERGED</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AWB_LOCK android.control.awbLock} is ON</td>
     * <td align="center">LOCKED</td>
     * <td align="center">Values locked</td>
     * </tr>
     * <tr>
     * <td align="center">LOCKED</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AWB_LOCK android.control.awbLock} is OFF</td>
     * <td align="center">SEARCHING</td>
     * <td align="center">Values not good after unlock</td>
     * </tr>
     * </tbody>
     * </table>
     * <p>For the above table, the camera device may skip reporting any state changes that happen
     * without application intervention (i.e. mode switch, trigger, locking). Any state that
     * can be skipped in that manner is called a transient state.</p>
     * <p>For example, for this AWB mode (AWB_MODE_AUTO), in addition to the state transitions
     * listed in above table, it is also legal for the camera device to skip one or more
     * transient states between two results. See below table for examples:</p>
     * <table>
     * <thead>
     * <tr>
     * <th align="center">State</th>
     * <th align="center">Transition Cause</th>
     * <th align="center">New State</th>
     * <th align="center">Notes</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td align="center">INACTIVE</td>
     * <td align="center">Camera device finished AWB scan</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Values are already good, transient states are skipped by camera device.</td>
     * </tr>
     * <tr>
     * <td align="center">LOCKED</td>
     * <td align="center">{@link CaptureRequest#CONTROL_AWB_LOCK android.control.awbLock} is OFF</td>
     * <td align="center">CONVERGED</td>
     * <td align="center">Values good after unlock, transient states are skipped by camera device.</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @see CaptureRequest#CONTROL_AWB_LOCK
     * @see CaptureRequest#CONTROL_AWB_MODE
     * @see CaptureRequest#CONTROL_MODE
     * @see CaptureRequest#CONTROL_SCENE_MODE
     * @see #CONTROL_AWB_STATE_INACTIVE
     * @see #CONTROL_AWB_STATE_SEARCHING
     * @see #CONTROL_AWB_STATE_CONVERGED
     * @see #CONTROL_AWB_STATE_LOCKED
     */
    public static final Key<Integer> CONTROL_AWB_STATE =
            new Key<Integer>("android.control.awbState", int.class);

    /**
     * <p>A special color effect to apply.</p>
     * <p>When this mode is set, a color effect will be applied
     * to images produced by the camera device. The interpretation
     * and implementation of these color effects is left to the
     * implementor of the camera device, and should not be
     * depended on to be consistent (or present) across all
     * devices.</p>
     * <p>A color effect will only be applied if
     * {@link CaptureRequest#CONTROL_MODE android.control.mode} != OFF.</p>
     *
     * @see CaptureRequest#CONTROL_MODE
     * @see #CONTROL_EFFECT_MODE_OFF
     * @see #CONTROL_EFFECT_MODE_MONO
     * @see #CONTROL_EFFECT_MODE_NEGATIVE
     * @see #CONTROL_EFFECT_MODE_SOLARIZE
     * @see #CONTROL_EFFECT_MODE_SEPIA
     * @see #CONTROL_EFFECT_MODE_POSTERIZE
     * @see #CONTROL_EFFECT_MODE_WHITEBOARD
     * @see #CONTROL_EFFECT_MODE_BLACKBOARD
     * @see #CONTROL_EFFECT_MODE_AQUA
     */
    public static final Key<Integer> CONTROL_EFFECT_MODE =
            new Key<Integer>("android.control.effectMode", int.class);

    /**
     * <p>Overall mode of 3A control
     * routines.</p>
     * <p>High-level 3A control. When set to OFF, all 3A control
     * by the camera device is disabled. The application must set the fields for
     * capture parameters itself.</p>
     * <p>When set to AUTO, the individual algorithm controls in
     * android.control.* are in effect, such as {@link CaptureRequest#CONTROL_AF_MODE android.control.afMode}.</p>
     * <p>When set to USE_SCENE_MODE, the individual controls in
     * android.control.* are mostly disabled, and the camera device implements
     * one of the scene mode settings (such as ACTION, SUNSET, or PARTY)
     * as it wishes. The camera device scene mode 3A settings are provided by
     * android.control.sceneModeOverrides.</p>
     * <p>When set to OFF_KEEP_STATE, it is similar to OFF mode, the only difference
     * is that this frame will not be used by camera device background 3A statistics
     * update, as if this frame is never captured. This mode can be used in the scenario
     * where the application doesn't want a 3A manual control capture to affect
     * the subsequent auto 3A capture results.</p>
     *
     * @see CaptureRequest#CONTROL_AF_MODE
     * @see #CONTROL_MODE_OFF
     * @see #CONTROL_MODE_AUTO
     * @see #CONTROL_MODE_USE_SCENE_MODE
     * @see #CONTROL_MODE_OFF_KEEP_STATE
     */
    public static final Key<Integer> CONTROL_MODE =
            new Key<Integer>("android.control.mode", int.class);

    /**
     * <p>A camera mode optimized for conditions typical in a particular
     * capture setting.</p>
     * <p>This is the mode that that is active when
     * <code>{@link CaptureRequest#CONTROL_MODE android.control.mode} == USE_SCENE_MODE</code>. Aside from FACE_PRIORITY,
     * these modes will disable {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode},
     * {@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode}, and {@link CaptureRequest#CONTROL_AF_MODE android.control.afMode} while in use.
     * The scene modes available for a given camera device are listed in
     * {@link CameraCharacteristics#CONTROL_AVAILABLE_SCENE_MODES android.control.availableSceneModes}.</p>
     * <p>The interpretation and implementation of these scene modes is left
     * to the implementor of the camera device. Their behavior will not be
     * consistent across all devices, and any given device may only implement
     * a subset of these modes.</p>
     *
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureRequest#CONTROL_AF_MODE
     * @see CameraCharacteristics#CONTROL_AVAILABLE_SCENE_MODES
     * @see CaptureRequest#CONTROL_AWB_MODE
     * @see CaptureRequest#CONTROL_MODE
     * @see #CONTROL_SCENE_MODE_DISABLED
     * @see #CONTROL_SCENE_MODE_FACE_PRIORITY
     * @see #CONTROL_SCENE_MODE_ACTION
     * @see #CONTROL_SCENE_MODE_PORTRAIT
     * @see #CONTROL_SCENE_MODE_LANDSCAPE
     * @see #CONTROL_SCENE_MODE_NIGHT
     * @see #CONTROL_SCENE_MODE_NIGHT_PORTRAIT
     * @see #CONTROL_SCENE_MODE_THEATRE
     * @see #CONTROL_SCENE_MODE_BEACH
     * @see #CONTROL_SCENE_MODE_SNOW
     * @see #CONTROL_SCENE_MODE_SUNSET
     * @see #CONTROL_SCENE_MODE_STEADYPHOTO
     * @see #CONTROL_SCENE_MODE_FIREWORKS
     * @see #CONTROL_SCENE_MODE_SPORTS
     * @see #CONTROL_SCENE_MODE_PARTY
     * @see #CONTROL_SCENE_MODE_CANDLELIGHT
     * @see #CONTROL_SCENE_MODE_BARCODE
     */
    public static final Key<Integer> CONTROL_SCENE_MODE =
            new Key<Integer>("android.control.sceneMode", int.class);

    /**
     * <p>Whether video stabilization is
     * active.</p>
     * <p>Video stabilization automatically translates and scales images from the camera
     * in order to stabilize motion between consecutive frames.</p>
     * <p>If enabled, video stabilization can modify the
     * {@link CaptureRequest#SCALER_CROP_REGION android.scaler.cropRegion} to keep the video stream stabilized.</p>
     * <p>Switching between different video stabilization modes may take several frames
     * to initialize, the camera device will report the current mode in capture result
     * metadata. For example, When "ON" mode is requested, the video stabilization modes
     * in the first several capture results may still be "OFF", and it will become "ON"
     * when the initialization is done.</p>
     * <p>If a camera device supports both this mode and OIS ({@link CaptureRequest#LENS_OPTICAL_STABILIZATION_MODE android.lens.opticalStabilizationMode}),
     * turning both modes on may produce undesirable interaction, so it is recommended not to
     * enable both at the same time.</p>
     *
     * @see CaptureRequest#LENS_OPTICAL_STABILIZATION_MODE
     * @see CaptureRequest#SCALER_CROP_REGION
     * @see #CONTROL_VIDEO_STABILIZATION_MODE_OFF
     * @see #CONTROL_VIDEO_STABILIZATION_MODE_ON
     */
    public static final Key<Integer> CONTROL_VIDEO_STABILIZATION_MODE =
            new Key<Integer>("android.control.videoStabilizationMode", int.class);

    /**
     * <p>Operation mode for edge
     * enhancement.</p>
     * <p>Edge/sharpness/detail enhancement. OFF means no
     * enhancement will be applied by the camera device.</p>
     * <p>This must be set to one of the modes listed in {@link CameraCharacteristics#EDGE_AVAILABLE_EDGE_MODES android.edge.availableEdgeModes}.</p>
     * <p>FAST/HIGH_QUALITY both mean camera device determined enhancement
     * will be applied. HIGH_QUALITY mode indicates that the
     * camera device will use the highest-quality enhancement algorithms,
     * even if it slows down capture rate. FAST means the camera device will
     * not slow down capture rate when applying edge enhancement.</p>
     *
     * @see CameraCharacteristics#EDGE_AVAILABLE_EDGE_MODES
     * @see #EDGE_MODE_OFF
     * @see #EDGE_MODE_FAST
     * @see #EDGE_MODE_HIGH_QUALITY
     */
    public static final Key<Integer> EDGE_MODE =
            new Key<Integer>("android.edge.mode", int.class);

    /**
     * <p>The desired mode for for the camera device's flash control.</p>
     * <p>This control is only effective when flash unit is available
     * (<code>{@link CameraCharacteristics#FLASH_INFO_AVAILABLE android.flash.info.available} == true</code>).</p>
     * <p>When this control is used, the {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} must be set to ON or OFF.
     * Otherwise, the camera device auto-exposure related flash control (ON_AUTO_FLASH,
     * ON_ALWAYS_FLASH, or ON_AUTO_FLASH_REDEYE) will override this control.</p>
     * <p>When set to OFF, the camera device will not fire flash for this capture.</p>
     * <p>When set to SINGLE, the camera device will fire flash regardless of the camera
     * device's auto-exposure routine's result. When used in still capture case, this
     * control should be used along with auto-exposure (AE) precapture metering sequence
     * ({@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER android.control.aePrecaptureTrigger}), otherwise, the image may be incorrectly exposed.</p>
     * <p>When set to TORCH, the flash will be on continuously. This mode can be used
     * for use cases such as preview, auto-focus assist, still capture, or video recording.</p>
     * <p>The flash status will be reported by {@link CaptureResult#FLASH_STATE android.flash.state} in the capture result metadata.</p>
     *
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER
     * @see CameraCharacteristics#FLASH_INFO_AVAILABLE
     * @see CaptureResult#FLASH_STATE
     * @see #FLASH_MODE_OFF
     * @see #FLASH_MODE_SINGLE
     * @see #FLASH_MODE_TORCH
     */
    public static final Key<Integer> FLASH_MODE =
            new Key<Integer>("android.flash.mode", int.class);

    /**
     * <p>Current state of the flash
     * unit.</p>
     * <p>When the camera device doesn't have flash unit
     * (i.e. <code>{@link CameraCharacteristics#FLASH_INFO_AVAILABLE android.flash.info.available} == false</code>), this state will always be UNAVAILABLE.
     * Other states indicate the current flash status.</p>
     *
     * @see CameraCharacteristics#FLASH_INFO_AVAILABLE
     * @see #FLASH_STATE_UNAVAILABLE
     * @see #FLASH_STATE_CHARGING
     * @see #FLASH_STATE_READY
     * @see #FLASH_STATE_FIRED
     * @see #FLASH_STATE_PARTIAL
     */
    public static final Key<Integer> FLASH_STATE =
            new Key<Integer>("android.flash.state", int.class);

    /**
     * <p>Set operational mode for hot pixel correction.</p>
     * <p>Valid modes for this camera device are listed in
     * {@link CameraCharacteristics#HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES android.hotPixel.availableHotPixelModes}.</p>
     * <p>Hotpixel correction interpolates out, or otherwise removes, pixels
     * that do not accurately encode the incoming light (i.e. pixels that
     * are stuck at an arbitrary value).</p>
     *
     * @see CameraCharacteristics#HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES
     * @see #HOT_PIXEL_MODE_OFF
     * @see #HOT_PIXEL_MODE_FAST
     * @see #HOT_PIXEL_MODE_HIGH_QUALITY
     */
    public static final Key<Integer> HOT_PIXEL_MODE =
            new Key<Integer>("android.hotPixel.mode", int.class);

    /**
     * <p>A location object to use when generating image GPS metadata.</p>
     */
    public static final Key<android.location.Location> JPEG_GPS_LOCATION =
            new Key<android.location.Location>("android.jpeg.gpsLocation", android.location.Location.class);

    /**
     * <p>GPS coordinates to include in output JPEG
     * EXIF</p>
     * @hide
     */
    public static final Key<double[]> JPEG_GPS_COORDINATES =
            new Key<double[]>("android.jpeg.gpsCoordinates", double[].class);

    /**
     * <p>32 characters describing GPS algorithm to
     * include in EXIF</p>
     * @hide
     */
    public static final Key<String> JPEG_GPS_PROCESSING_METHOD =
            new Key<String>("android.jpeg.gpsProcessingMethod", String.class);

    /**
     * <p>Time GPS fix was made to include in
     * EXIF</p>
     * @hide
     */
    public static final Key<Long> JPEG_GPS_TIMESTAMP =
            new Key<Long>("android.jpeg.gpsTimestamp", long.class);

    /**
     * <p>Orientation of JPEG image to
     * write</p>
     */
    public static final Key<Integer> JPEG_ORIENTATION =
            new Key<Integer>("android.jpeg.orientation", int.class);

    /**
     * <p>Compression quality of the final JPEG
     * image.</p>
     * <p>85-95 is typical usage range.</p>
     */
    public static final Key<Byte> JPEG_QUALITY =
            new Key<Byte>("android.jpeg.quality", byte.class);

    /**
     * <p>Compression quality of JPEG
     * thumbnail.</p>
     */
    public static final Key<Byte> JPEG_THUMBNAIL_QUALITY =
            new Key<Byte>("android.jpeg.thumbnailQuality", byte.class);

    /**
     * <p>Resolution of embedded JPEG thumbnail.</p>
     * <p>When set to (0, 0) value, the JPEG EXIF will not contain thumbnail,
     * but the captured JPEG will still be a valid image.</p>
     * <p>When a jpeg image capture is issued, the thumbnail size selected should have
     * the same aspect ratio as the jpeg image.</p>
     * <p>If the thumbnail image aspect ratio differs from the JPEG primary image aspect
     * ratio, the camera device creates the thumbnail by cropping it from the primary image.
     * For example, if the primary image has 4:3 aspect ratio, the thumbnail image has
     * 16:9 aspect ratio, the primary image will be cropped vertically (letterbox) to
     * generate the thumbnail image. The thumbnail image will always have a smaller Field
     * Of View (FOV) than the primary image when aspect ratios differ.</p>
     */
    public static final Key<android.util.Size> JPEG_THUMBNAIL_SIZE =
            new Key<android.util.Size>("android.jpeg.thumbnailSize", android.util.Size.class);

    /**
     * <p>The ratio of lens focal length to the effective
     * aperture diameter.</p>
     * <p>This will only be supported on the camera devices that
     * have variable aperture lens. The aperture value can only be
     * one of the values listed in {@link CameraCharacteristics#LENS_INFO_AVAILABLE_APERTURES android.lens.info.availableApertures}.</p>
     * <p>When this is supported and {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} is OFF,
     * this can be set along with {@link CaptureRequest#SENSOR_EXPOSURE_TIME android.sensor.exposureTime},
     * {@link CaptureRequest#SENSOR_SENSITIVITY android.sensor.sensitivity}, and {@link CaptureRequest#SENSOR_FRAME_DURATION android.sensor.frameDuration}
     * to achieve manual exposure control.</p>
     * <p>The requested aperture value may take several frames to reach the
     * requested value; the camera device will report the current (intermediate)
     * aperture size in capture result metadata while the aperture is changing.
     * While the aperture is still changing, {@link CaptureResult#LENS_STATE android.lens.state} will be set to MOVING.</p>
     * <p>When this is supported and {@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} is one of
     * the ON modes, this will be overridden by the camera device
     * auto-exposure algorithm, the overridden values are then provided
     * back to the user in the corresponding result.</p>
     *
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CameraCharacteristics#LENS_INFO_AVAILABLE_APERTURES
     * @see CaptureResult#LENS_STATE
     * @see CaptureRequest#SENSOR_EXPOSURE_TIME
     * @see CaptureRequest#SENSOR_FRAME_DURATION
     * @see CaptureRequest#SENSOR_SENSITIVITY
     */
    public static final Key<Float> LENS_APERTURE =
            new Key<Float>("android.lens.aperture", float.class);

    /**
     * <p>State of lens neutral density filter(s).</p>
     * <p>This will not be supported on most camera devices. On devices
     * where this is supported, this may only be set to one of the
     * values included in {@link CameraCharacteristics#LENS_INFO_AVAILABLE_FILTER_DENSITIES android.lens.info.availableFilterDensities}.</p>
     * <p>Lens filters are typically used to lower the amount of light the
     * sensor is exposed to (measured in steps of EV). As used here, an EV
     * step is the standard logarithmic representation, which are
     * non-negative, and inversely proportional to the amount of light
     * hitting the sensor.  For example, setting this to 0 would result
     * in no reduction of the incoming light, and setting this to 2 would
     * mean that the filter is set to reduce incoming light by two stops
     * (allowing 1/4 of the prior amount of light to the sensor).</p>
     * <p>It may take several frames before the lens filter density changes
     * to the requested value. While the filter density is still changing,
     * {@link CaptureResult#LENS_STATE android.lens.state} will be set to MOVING.</p>
     *
     * @see CameraCharacteristics#LENS_INFO_AVAILABLE_FILTER_DENSITIES
     * @see CaptureResult#LENS_STATE
     */
    public static final Key<Float> LENS_FILTER_DENSITY =
            new Key<Float>("android.lens.filterDensity", float.class);

    /**
     * <p>The current lens focal length; used for optical zoom.</p>
     * <p>This setting controls the physical focal length of the camera
     * device's lens. Changing the focal length changes the field of
     * view of the camera device, and is usually used for optical zoom.</p>
     * <p>Like {@link CaptureRequest#LENS_FOCUS_DISTANCE android.lens.focusDistance} and {@link CaptureRequest#LENS_APERTURE android.lens.aperture}, this
     * setting won't be applied instantaneously, and it may take several
     * frames before the lens can change to the requested focal length.
     * While the focal length is still changing, {@link CaptureResult#LENS_STATE android.lens.state} will
     * be set to MOVING.</p>
     * <p>This is expected not to be supported on most devices.</p>
     *
     * @see CaptureRequest#LENS_APERTURE
     * @see CaptureRequest#LENS_FOCUS_DISTANCE
     * @see CaptureResult#LENS_STATE
     */
    public static final Key<Float> LENS_FOCAL_LENGTH =
            new Key<Float>("android.lens.focalLength", float.class);

    /**
     * <p>Distance to plane of sharpest focus,
     * measured from frontmost surface of the lens.</p>
     * <p>Should be zero for fixed-focus cameras</p>
     */
    public static final Key<Float> LENS_FOCUS_DISTANCE =
            new Key<Float>("android.lens.focusDistance", float.class);

    /**
     * <p>The range of scene distances that are in
     * sharp focus (depth of field).</p>
     * <p>If variable focus not supported, can still report
     * fixed depth of field range</p>
     */
    public static final Key<android.util.Pair<Float,Float>> LENS_FOCUS_RANGE =
            new Key<android.util.Pair<Float,Float>>("android.lens.focusRange", new TypeReference<android.util.Pair<Float,Float>>() {{ }});

    /**
     * <p>Sets whether the camera device uses optical image stabilization (OIS)
     * when capturing images.</p>
     * <p>OIS is used to compensate for motion blur due to small
     * movements of the camera during capture. Unlike digital image
     * stabilization ({@link CaptureRequest#CONTROL_VIDEO_STABILIZATION_MODE android.control.videoStabilizationMode}), OIS
     * makes use of mechanical elements to stabilize the camera
     * sensor, and thus allows for longer exposure times before
     * camera shake becomes apparent.</p>
     * <p>Switching between different optical stabilization modes may take several
     * frames to initialize, the camera device will report the current mode in
     * capture result metadata. For example, When "ON" mode is requested, the
     * optical stabilization modes in the first several capture results may still
     * be "OFF", and it will become "ON" when the initialization is done.</p>
     * <p>If a camera device supports both OIS and EIS ({@link CaptureRequest#CONTROL_VIDEO_STABILIZATION_MODE android.control.videoStabilizationMode}),
     * turning both modes on may produce undesirable interaction, so it is recommended not
     * to enable both at the same time.</p>
     * <p>Not all devices will support OIS; see
     * {@link CameraCharacteristics#LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION android.lens.info.availableOpticalStabilization} for
     * available controls.</p>
     *
     * @see CaptureRequest#CONTROL_VIDEO_STABILIZATION_MODE
     * @see CameraCharacteristics#LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
     * @see #LENS_OPTICAL_STABILIZATION_MODE_OFF
     * @see #LENS_OPTICAL_STABILIZATION_MODE_ON
     */
    public static final Key<Integer> LENS_OPTICAL_STABILIZATION_MODE =
            new Key<Integer>("android.lens.opticalStabilizationMode", int.class);

    /**
     * <p>Current lens status.</p>
     * <p>For lens parameters {@link CaptureRequest#LENS_FOCAL_LENGTH android.lens.focalLength}, {@link CaptureRequest#LENS_FOCUS_DISTANCE android.lens.focusDistance},
     * {@link CaptureRequest#LENS_FILTER_DENSITY android.lens.filterDensity} and {@link CaptureRequest#LENS_APERTURE android.lens.aperture}, when changes are requested,
     * they may take several frames to reach the requested values. This state indicates
     * the current status of the lens parameters.</p>
     * <p>When the state is STATIONARY, the lens parameters are not changing. This could be
     * either because the parameters are all fixed, or because the lens has had enough
     * time to reach the most recently-requested values.
     * If all these lens parameters are not changable for a camera device, as listed below:</p>
     * <ul>
     * <li>Fixed focus (<code>{@link CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE android.lens.info.minimumFocusDistance} == 0</code>), which means
     * {@link CaptureRequest#LENS_FOCUS_DISTANCE android.lens.focusDistance} parameter will always be 0.</li>
     * <li>Fixed focal length ({@link CameraCharacteristics#LENS_INFO_AVAILABLE_FOCAL_LENGTHS android.lens.info.availableFocalLengths} contains single value),
     * which means the optical zoom is not supported.</li>
     * <li>No ND filter ({@link CameraCharacteristics#LENS_INFO_AVAILABLE_FILTER_DENSITIES android.lens.info.availableFilterDensities} contains only 0).</li>
     * <li>Fixed aperture ({@link CameraCharacteristics#LENS_INFO_AVAILABLE_APERTURES android.lens.info.availableApertures} contains single value).</li>
     * </ul>
     * <p>Then this state will always be STATIONARY.</p>
     * <p>When the state is MOVING, it indicates that at least one of the lens parameters
     * is changing.</p>
     *
     * @see CaptureRequest#LENS_APERTURE
     * @see CaptureRequest#LENS_FILTER_DENSITY
     * @see CaptureRequest#LENS_FOCAL_LENGTH
     * @see CaptureRequest#LENS_FOCUS_DISTANCE
     * @see CameraCharacteristics#LENS_INFO_AVAILABLE_APERTURES
     * @see CameraCharacteristics#LENS_INFO_AVAILABLE_FILTER_DENSITIES
     * @see CameraCharacteristics#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
     * @see CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE
     * @see #LENS_STATE_STATIONARY
     * @see #LENS_STATE_MOVING
     */
    public static final Key<Integer> LENS_STATE =
            new Key<Integer>("android.lens.state", int.class);

    /**
     * <p>Mode of operation for the noise reduction algorithm.</p>
     * <p>Noise filtering control. OFF means no noise reduction
     * will be applied by the camera device.</p>
     * <p>This must be set to a valid mode from
     * {@link CameraCharacteristics#NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES android.noiseReduction.availableNoiseReductionModes}.</p>
     * <p>FAST/HIGH_QUALITY both mean camera device determined noise filtering
     * will be applied. HIGH_QUALITY mode indicates that the camera device
     * will use the highest-quality noise filtering algorithms,
     * even if it slows down capture rate. FAST means the camera device will not
     * slow down capture rate when applying noise filtering.</p>
     *
     * @see CameraCharacteristics#NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES
     * @see #NOISE_REDUCTION_MODE_OFF
     * @see #NOISE_REDUCTION_MODE_FAST
     * @see #NOISE_REDUCTION_MODE_HIGH_QUALITY
     */
    public static final Key<Integer> NOISE_REDUCTION_MODE =
            new Key<Integer>("android.noiseReduction.mode", int.class);

    /**
     * <p>Whether a result given to the framework is the
     * final one for the capture, or only a partial that contains a
     * subset of the full set of dynamic metadata
     * values.</p>
     * <p>The entries in the result metadata buffers for a
     * single capture may not overlap, except for this entry. The
     * FINAL buffers must retain FIFO ordering relative to the
     * requests that generate them, so the FINAL buffer for frame 3 must
     * always be sent to the framework after the FINAL buffer for frame 2, and
     * before the FINAL buffer for frame 4. PARTIAL buffers may be returned
     * in any order relative to other frames, but all PARTIAL buffers for a given
     * capture must arrive before the FINAL buffer for that capture. This entry may
     * only be used by the camera device if quirks.usePartialResult is set to 1.</p>
     * <p><b>Optional</b> - This value may be {@code null} on some devices.</p>
     * @deprecated
     * @hide
     */
    @Deprecated
    public static final Key<Boolean> QUIRKS_PARTIAL_RESULT =
            new Key<Boolean>("android.quirks.partialResult", boolean.class);

    /**
     * <p>A frame counter set by the framework. This value monotonically
     * increases with every new result (that is, each new result has a unique
     * frameCount value).</p>
     * <p>Reset on release()</p>
     */
    public static final Key<Integer> REQUEST_FRAME_COUNT =
            new Key<Integer>("android.request.frameCount", int.class);

    /**
     * <p>An application-specified ID for the current
     * request. Must be maintained unchanged in output
     * frame</p>
     * @hide
     */
    public static final Key<Integer> REQUEST_ID =
            new Key<Integer>("android.request.id", int.class);

    /**
     * <p>Specifies the number of pipeline stages the frame went
     * through from when it was exposed to when the final completed result
     * was available to the framework.</p>
     * <p>Depending on what settings are used in the request, and
     * what streams are configured, the data may undergo less processing,
     * and some pipeline stages skipped.</p>
     * <p>See {@link CameraCharacteristics#REQUEST_PIPELINE_MAX_DEPTH android.request.pipelineMaxDepth} for more details.</p>
     *
     * @see CameraCharacteristics#REQUEST_PIPELINE_MAX_DEPTH
     */
    public static final Key<Byte> REQUEST_PIPELINE_DEPTH =
            new Key<Byte>("android.request.pipelineDepth", byte.class);

    /**
     * <p>The region of the sensor to read out for this capture.</p>
     * <p>The crop region coordinate system is based off
     * {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}, with <code>(0, 0)</code> being the
     * top-left corner of the sensor active array.</p>
     * <p>Output streams use this rectangle to produce their output,
     * cropping to a smaller region if necessary to maintain the
     * stream's aspect ratio, then scaling the sensor input to
     * match the output's configured resolution.</p>
     * <p>The crop region is applied after the RAW to other color
     * space (e.g. YUV) conversion. Since raw streams
     * (e.g. RAW16) don't have the conversion stage, they are not
     * croppable. The crop region will be ignored by raw streams.</p>
     * <p>For non-raw streams, any additional per-stream cropping will
     * be done to maximize the final pixel area of the stream.</p>
     * <p>For example, if the crop region is set to a 4:3 aspect
     * ratio, then 4:3 streams will use the exact crop
     * region. 16:9 streams will further crop vertically
     * (letterbox).</p>
     * <p>Conversely, if the crop region is set to a 16:9, then 4:3
     * outputs will crop horizontally (pillarbox), and 16:9
     * streams will match exactly. These additional crops will
     * be centered within the crop region.</p>
     * <p>The width and height of the crop region cannot
     * be set to be smaller than
     * <code>floor( activeArraySize.width / {@link CameraCharacteristics#SCALER_AVAILABLE_MAX_DIGITAL_ZOOM android.scaler.availableMaxDigitalZoom} )</code> and
     * <code>floor( activeArraySize.height / {@link CameraCharacteristics#SCALER_AVAILABLE_MAX_DIGITAL_ZOOM android.scaler.availableMaxDigitalZoom} )</code>, respectively.</p>
     * <p>The camera device may adjust the crop region to account
     * for rounding and other hardware requirements; the final
     * crop region used will be included in the output capture
     * result.</p>
     *
     * @see CameraCharacteristics#SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     */
    public static final Key<android.graphics.Rect> SCALER_CROP_REGION =
            new Key<android.graphics.Rect>("android.scaler.cropRegion", android.graphics.Rect.class);

    /**
     * <p>Duration each pixel is exposed to
     * light.</p>
     * <p>If the sensor can't expose this exact duration, it should shorten the
     * duration exposed to the nearest possible value (rather than expose longer).</p>
     */
    public static final Key<Long> SENSOR_EXPOSURE_TIME =
            new Key<Long>("android.sensor.exposureTime", long.class);

    /**
     * <p>Duration from start of frame exposure to
     * start of next frame exposure.</p>
     * <p>The maximum frame rate that can be supported by a camera subsystem is
     * a function of many factors:</p>
     * <ul>
     * <li>Requested resolutions of output image streams</li>
     * <li>Availability of binning / skipping modes on the imager</li>
     * <li>The bandwidth of the imager interface</li>
     * <li>The bandwidth of the various ISP processing blocks</li>
     * </ul>
     * <p>Since these factors can vary greatly between different ISPs and
     * sensors, the camera abstraction tries to represent the bandwidth
     * restrictions with as simple a model as possible.</p>
     * <p>The model presented has the following characteristics:</p>
     * <ul>
     * <li>The image sensor is always configured to output the smallest
     * resolution possible given the application's requested output stream
     * sizes.  The smallest resolution is defined as being at least as large
     * as the largest requested output stream size; the camera pipeline must
     * never digitally upsample sensor data when the crop region covers the
     * whole sensor. In general, this means that if only small output stream
     * resolutions are configured, the sensor can provide a higher frame
     * rate.</li>
     * <li>Since any request may use any or all the currently configured
     * output streams, the sensor and ISP must be configured to support
     * scaling a single capture to all the streams at the same time.  This
     * means the camera pipeline must be ready to produce the largest
     * requested output size without any delay.  Therefore, the overall
     * frame rate of a given configured stream set is governed only by the
     * largest requested stream resolution.</li>
     * <li>Using more than one output stream in a request does not affect the
     * frame duration.</li>
     * <li>Certain format-streams may need to do additional background processing
     * before data is consumed/produced by that stream. These processors
     * can run concurrently to the rest of the camera pipeline, but
     * cannot process more than 1 capture at a time.</li>
     * </ul>
     * <p>The necessary information for the application, given the model above,
     * is provided via the {@link CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP android.scaler.streamConfigurationMap} field
     * using StreamConfigurationMap#getOutputMinFrameDuration(int, Size).
     * These are used to determine the maximum frame rate / minimum frame
     * duration that is possible for a given stream configuration.</p>
     * <p>Specifically, the application can use the following rules to
     * determine the minimum frame duration it can request from the camera
     * device:</p>
     * <ol>
     * <li>Let the set of currently configured input/output streams
     * be called <code>S</code>.</li>
     * <li>Find the minimum frame durations for each stream in <code>S</code>, by
     * looking it up in {@link CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP android.scaler.streamConfigurationMap} using
     * StreamConfigurationMap#getOutputMinFrameDuration(int, Size) (with
     * its respective size/format). Let this set of frame durations be called
     * <code>F</code>.</li>
     * <li>For any given request <code>R</code>, the minimum frame duration allowed
     * for <code>R</code> is the maximum out of all values in <code>F</code>. Let the streams
     * used in <code>R</code> be called <code>S_r</code>.</li>
     * </ol>
     * <p>If none of the streams in <code>S_r</code> have a stall time (listed in
     * StreamConfigurationMap#getOutputStallDuration(int,Size) using its
     * respective size/format), then the frame duration in
     * <code>F</code> determines the steady state frame rate that the application will
     * get if it uses <code>R</code> as a repeating request. Let this special kind
     * of request be called <code>Rsimple</code>.</p>
     * <p>A repeating request <code>Rsimple</code> can be <em>occasionally</em> interleaved
     * by a single capture of a new request <code>Rstall</code> (which has at least
     * one in-use stream with a non-0 stall time) and if <code>Rstall</code> has the
     * same minimum frame duration this will not cause a frame rate loss
     * if all buffers from the previous <code>Rstall</code> have already been
     * delivered.</p>
     * <p>For more details about stalling, see
     * StreamConfigurationMap#getOutputStallDuration(int,Size).</p>
     *
     * @see CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP
     */
    public static final Key<Long> SENSOR_FRAME_DURATION =
            new Key<Long>("android.sensor.frameDuration", long.class);

    /**
     * <p>The amount of gain applied to sensor data
     * before processing.</p>
     * <p>The sensitivity is the standard ISO sensitivity value,
     * as defined in ISO 12232:2006.</p>
     * <p>The sensitivity must be within {@link CameraCharacteristics#SENSOR_INFO_SENSITIVITY_RANGE android.sensor.info.sensitivityRange}, and
     * if if it less than {@link CameraCharacteristics#SENSOR_MAX_ANALOG_SENSITIVITY android.sensor.maxAnalogSensitivity}, the camera device
     * is guaranteed to use only analog amplification for applying the gain.</p>
     * <p>If the camera device cannot apply the exact sensitivity
     * requested, it will reduce the gain to the nearest supported
     * value. The final sensitivity used will be available in the
     * output capture result.</p>
     *
     * @see CameraCharacteristics#SENSOR_INFO_SENSITIVITY_RANGE
     * @see CameraCharacteristics#SENSOR_MAX_ANALOG_SENSITIVITY
     */
    public static final Key<Integer> SENSOR_SENSITIVITY =
            new Key<Integer>("android.sensor.sensitivity", int.class);

    /**
     * <p>Time at start of exposure of first
     * row of the image sensor active array, in nanoseconds.</p>
     * <p>The timestamps are also included in all image
     * buffers produced for the same capture, and will be identical
     * on all the outputs.</p>
     * <p>When {@link CameraCharacteristics#SENSOR_INFO_TIMESTAMP_CALIBRATION android.sensor.info.timestampCalibration} <code>==</code> UNCALIBRATED,
     * the timestamps measure time since an unspecified starting point,
     * and are monotonically increasing. They can be compared with the
     * timestamps for other captures from the same camera device, but are
     * not guaranteed to be comparable to any other time source.</p>
     * <p>When {@link CameraCharacteristics#SENSOR_INFO_TIMESTAMP_CALIBRATION android.sensor.info.timestampCalibration} <code>==</code> CALIBRATED,
     * the timestamps measure time in the same timebase as
     * android.os.SystemClock#elapsedRealtimeNanos(), and they can be
     * compared to other timestamps from other subsystems that are using
     * that base.</p>
     *
     * @see CameraCharacteristics#SENSOR_INFO_TIMESTAMP_CALIBRATION
     */
    public static final Key<Long> SENSOR_TIMESTAMP =
            new Key<Long>("android.sensor.timestamp", long.class);

    /**
     * <p>The estimated camera neutral color in the native sensor colorspace at
     * the time of capture.</p>
     * <p>This value gives the neutral color point encoded as an RGB value in the
     * native sensor color space.  The neutral color point indicates the
     * currently estimated white point of the scene illumination.  It can be
     * used to interpolate between the provided color transforms when
     * processing raw sensor data.</p>
     * <p>The order of the values is R, G, B; where R is in the lowest index.</p>
     * <p><b>Optional</b> - This value may be {@code null} on some devices.</p>
     */
    public static final Key<Rational[]> SENSOR_NEUTRAL_COLOR_POINT =
            new Key<Rational[]>("android.sensor.neutralColorPoint", Rational[].class);

    /**
     * <p>The worst-case divergence between Bayer green channels.</p>
     * <p>This value is an estimate of the worst case split between the
     * Bayer green channels in the red and blue rows in the sensor color
     * filter array.</p>
     * <p>The green split is calculated as follows:</p>
     * <ol>
     * <li>A 5x5 pixel (or larger) window W within the active sensor array is
     * chosen. The term 'pixel' here is taken to mean a group of 4 Bayer
     * mosaic channels (R, Gr, Gb, B).  The location and size of the window
     * chosen is implementation defined, and should be chosen to provide a
     * green split estimate that is both representative of the entire image
     * for this camera sensor, and can be calculated quickly.</li>
     * <li>The arithmetic mean of the green channels from the red
     * rows (mean_Gr) within W is computed.</li>
     * <li>The arithmetic mean of the green channels from the blue
     * rows (mean_Gb) within W is computed.</li>
     * <li>The maximum ratio R of the two means is computed as follows:
     * <code>R = max((mean_Gr + 1)/(mean_Gb + 1), (mean_Gb + 1)/(mean_Gr + 1))</code></li>
     * </ol>
     * <p>The ratio R is the green split divergence reported for this property,
     * which represents how much the green channels differ in the mosaic
     * pattern.  This value is typically used to determine the treatment of
     * the green mosaic channels when demosaicing.</p>
     * <p>The green split value can be roughly interpreted as follows:</p>
     * <ul>
     * <li>R &lt; 1.03 is a negligible split (&lt;3% divergence).</li>
     * <li>1.20 &lt;= R &gt;= 1.03 will require some software
     * correction to avoid demosaic errors (3-20% divergence).</li>
     * <li>R &gt; 1.20 will require strong software correction to produce
     * a usuable image (&gt;20% divergence).</li>
     * </ul>
     * <p><b>Optional</b> - This value may be {@code null} on some devices.</p>
     */
    public static final Key<Float> SENSOR_GREEN_SPLIT =
            new Key<Float>("android.sensor.greenSplit", float.class);

    /**
     * <p>A pixel <code>[R, G_even, G_odd, B]</code> that supplies the test pattern
     * when {@link CaptureRequest#SENSOR_TEST_PATTERN_MODE android.sensor.testPatternMode} is SOLID_COLOR.</p>
     * <p>Each color channel is treated as an unsigned 32-bit integer.
     * The camera device then uses the most significant X bits
     * that correspond to how many bits are in its Bayer raw sensor
     * output.</p>
     * <p>For example, a sensor with RAW10 Bayer output would use the
     * 10 most significant bits from each color channel.</p>
     * <p><b>Optional</b> - This value may be {@code null} on some devices.</p>
     *
     * @see CaptureRequest#SENSOR_TEST_PATTERN_MODE
     */
    public static final Key<int[]> SENSOR_TEST_PATTERN_DATA =
            new Key<int[]>("android.sensor.testPatternData", int[].class);

    /**
     * <p>When enabled, the sensor sends a test pattern instead of
     * doing a real exposure from the camera.</p>
     * <p>When a test pattern is enabled, all manual sensor controls specified
     * by android.sensor.* will be ignored. All other controls should
     * work as normal.</p>
     * <p>For example, if manual flash is enabled, flash firing should still
     * occur (and that the test pattern remain unmodified, since the flash
     * would not actually affect it).</p>
     * <p><b>Optional</b> - This value may be {@code null} on some devices.</p>
     * @see #SENSOR_TEST_PATTERN_MODE_OFF
     * @see #SENSOR_TEST_PATTERN_MODE_SOLID_COLOR
     * @see #SENSOR_TEST_PATTERN_MODE_COLOR_BARS
     * @see #SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY
     * @see #SENSOR_TEST_PATTERN_MODE_PN9
     * @see #SENSOR_TEST_PATTERN_MODE_CUSTOM1
     */
    public static final Key<Integer> SENSOR_TEST_PATTERN_MODE =
            new Key<Integer>("android.sensor.testPatternMode", int.class);

    /**
     * <p>Duration between the start of first row exposure
     * and the start of last row exposure.</p>
     * <p>This is the exposure time skew (in the unit of nanosecond) between the first and
     * last row exposure start times. The first row and the last row are the first
     * and last rows inside of the {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.</p>
     * <p>For typical camera sensors that use rolling shutters, this is also equivalent
     * to the frame readout time.</p>
     *
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     */
    public static final Key<Long> SENSOR_ROLLING_SHUTTER_SKEW =
            new Key<Long>("android.sensor.rollingShutterSkew", long.class);

    /**
     * <p>Quality of lens shading correction applied
     * to the image data.</p>
     * <p>When set to OFF mode, no lens shading correction will be applied by the
     * camera device, and an identity lens shading map data will be provided
     * if <code>{@link CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE android.statistics.lensShadingMapMode} == ON</code>. For example, for lens
     * shading map with size specified as <code>android.lens.info.shadingMapSize = [ 4, 3 ]</code>,
     * the output android.statistics.lensShadingMap for this case will be an identity map
     * shown below:</p>
     * <pre><code>[ 1.0, 1.0, 1.0, 1.0,  1.0, 1.0, 1.0, 1.0,
     * 1.0, 1.0, 1.0, 1.0,  1.0, 1.0, 1.0, 1.0,
     * 1.0, 1.0, 1.0, 1.0,  1.0, 1.0, 1.0, 1.0,
     * 1.0, 1.0, 1.0, 1.0,  1.0, 1.0, 1.0, 1.0,
     * 1.0, 1.0, 1.0, 1.0,   1.0, 1.0, 1.0, 1.0,
     * 1.0, 1.0, 1.0, 1.0,  1.0, 1.0, 1.0, 1.0 ]
     * </code></pre>
     * <p>When set to other modes, lens shading correction will be applied by the
     * camera device. Applications can request lens shading map data by setting
     * {@link CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE android.statistics.lensShadingMapMode} to ON, and then the camera device will provide
     * lens shading map data in android.statistics.lensShadingMap, with size specified
     * by android.lens.info.shadingMapSize; the returned shading map data will be the one
     * applied by the camera device for this capture request.</p>
     * <p>The shading map data may depend on the auto-exposure (AE) and AWB statistics, therefore the reliability
     * of the map data may be affected by the AE and AWB algorithms. When AE and AWB are in
     * AUTO modes({@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} <code>!=</code> OFF and {@link CaptureRequest#CONTROL_AWB_MODE android.control.awbMode} <code>!=</code> OFF),
     * to get best results, it is recommended that the applications wait for the AE and AWB to
     * be converged before using the returned shading map data.</p>
     *
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureRequest#CONTROL_AWB_MODE
     * @see CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE
     * @see #SHADING_MODE_OFF
     * @see #SHADING_MODE_FAST
     * @see #SHADING_MODE_HIGH_QUALITY
     */
    public static final Key<Integer> SHADING_MODE =
            new Key<Integer>("android.shading.mode", int.class);

    /**
     * <p>Control for the face detector
     * unit.</p>
     * <p>Whether face detection is enabled, and whether it
     * should output just the basic fields or the full set of
     * fields. Value must be one of the
     * {@link CameraCharacteristics#STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES android.statistics.info.availableFaceDetectModes}.</p>
     *
     * @see CameraCharacteristics#STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES
     * @see #STATISTICS_FACE_DETECT_MODE_OFF
     * @see #STATISTICS_FACE_DETECT_MODE_SIMPLE
     * @see #STATISTICS_FACE_DETECT_MODE_FULL
     */
    public static final Key<Integer> STATISTICS_FACE_DETECT_MODE =
            new Key<Integer>("android.statistics.faceDetectMode", int.class);

    /**
     * <p>List of unique IDs for detected faces.</p>
     * <p>Each detected face is given a unique ID that is valid for as long as the face is visible
     * to the camera device.  A face that leaves the field of view and later returns may be
     * assigned a new ID.</p>
     * <p>Only available if {@link CaptureRequest#STATISTICS_FACE_DETECT_MODE android.statistics.faceDetectMode} == FULL</p>
     *
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     * @hide
     */
    public static final Key<int[]> STATISTICS_FACE_IDS =
            new Key<int[]>("android.statistics.faceIds", int[].class);

    /**
     * <p>List of landmarks for detected
     * faces.</p>
     * <p>The coordinate system is that of {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}, with
     * <code>(0, 0)</code> being the top-left pixel of the active array.</p>
     * <p>Only available if {@link CaptureRequest#STATISTICS_FACE_DETECT_MODE android.statistics.faceDetectMode} == FULL</p>
     *
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     * @hide
     */
    public static final Key<int[]> STATISTICS_FACE_LANDMARKS =
            new Key<int[]>("android.statistics.faceLandmarks", int[].class);

    /**
     * <p>List of the bounding rectangles for detected
     * faces.</p>
     * <p>The coordinate system is that of {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}, with
     * <code>(0, 0)</code> being the top-left pixel of the active array.</p>
     * <p>Only available if {@link CaptureRequest#STATISTICS_FACE_DETECT_MODE android.statistics.faceDetectMode} != OFF</p>
     *
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     * @hide
     */
    public static final Key<android.graphics.Rect[]> STATISTICS_FACE_RECTANGLES =
            new Key<android.graphics.Rect[]>("android.statistics.faceRectangles", android.graphics.Rect[].class);

    /**
     * <p>List of the face confidence scores for
     * detected faces</p>
     * <p>Only available if {@link CaptureRequest#STATISTICS_FACE_DETECT_MODE android.statistics.faceDetectMode} != OFF.</p>
     *
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     * @hide
     */
    public static final Key<byte[]> STATISTICS_FACE_SCORES =
            new Key<byte[]>("android.statistics.faceScores", byte[].class);

    /**
     * <p>List of the faces detected through camera face detection
     * in this result.</p>
     * <p>Only available if {@link CaptureRequest#STATISTICS_FACE_DETECT_MODE android.statistics.faceDetectMode} <code>!=</code> OFF.</p>
     *
     * @see CaptureRequest#STATISTICS_FACE_DETECT_MODE
     */
    public static final Key<android.hardware.camera2.params.Face[]> STATISTICS_FACES =
            new Key<android.hardware.camera2.params.Face[]>("android.statistics.faces", android.hardware.camera2.params.Face[].class);

    /**
     * <p>The shading map is a low-resolution floating-point map
     * that lists the coefficients used to correct for vignetting, for each
     * Bayer color channel.</p>
     * <p>The least shaded section of the image should have a gain factor
     * of 1; all other sections should have gains above 1.</p>
     * <p>When {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode} = TRANSFORM_MATRIX, the map
     * must take into account the colorCorrection settings.</p>
     * <p>The shading map is for the entire active pixel array, and is not
     * affected by the crop region specified in the request. Each shading map
     * entry is the value of the shading compensation map over a specific
     * pixel on the sensor.  Specifically, with a (N x M) resolution shading
     * map, and an active pixel array size (W x H), shading map entry
     * (x,y) ϵ (0 ... N-1, 0 ... M-1) is the value of the shading map at
     * pixel ( ((W-1)/(N-1)) * x, ((H-1)/(M-1)) * y) for the four color channels.
     * The map is assumed to be bilinearly interpolated between the sample points.</p>
     * <p>The channel order is [R, Geven, Godd, B], where Geven is the green
     * channel for the even rows of a Bayer pattern, and Godd is the odd rows.
     * The shading map is stored in a fully interleaved format.</p>
     * <p>The shading map should have on the order of 30-40 rows and columns,
     * and must be smaller than 64x64.</p>
     * <p>As an example, given a very small map defined as:</p>
     * <pre><code>width,height = [ 4, 3 ]
     * values =
     * [ 1.3, 1.2, 1.15, 1.2,  1.2, 1.2, 1.15, 1.2,
     * 1.1, 1.2, 1.2, 1.2,  1.3, 1.2, 1.3, 1.3,
     * 1.2, 1.2, 1.25, 1.1,  1.1, 1.1, 1.1, 1.0,
     * 1.0, 1.0, 1.0, 1.0,  1.2, 1.3, 1.25, 1.2,
     * 1.3, 1.2, 1.2, 1.3,   1.2, 1.15, 1.1, 1.2,
     * 1.2, 1.1, 1.0, 1.2,  1.3, 1.15, 1.2, 1.3 ]
     * </code></pre>
     * <p>The low-resolution scaling map images for each channel are
     * (displayed using nearest-neighbor interpolation):</p>
     * <p><img alt="Red lens shading map" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/red_shading.png" />
     * <img alt="Green (even rows) lens shading map" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/green_e_shading.png" />
     * <img alt="Green (odd rows) lens shading map" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/green_o_shading.png" />
     * <img alt="Blue lens shading map" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/blue_shading.png" /></p>
     * <p>As a visualization only, inverting the full-color map to recover an
     * image of a gray wall (using bicubic interpolation for visual quality) as captured by the sensor gives:</p>
     * <p><img alt="Image of a uniform white wall (inverse shading map)" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/inv_shading.png" /></p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_MODE
     */
    public static final Key<android.hardware.camera2.params.LensShadingMap> STATISTICS_LENS_SHADING_CORRECTION_MAP =
            new Key<android.hardware.camera2.params.LensShadingMap>("android.statistics.lensShadingCorrectionMap", android.hardware.camera2.params.LensShadingMap.class);

    /**
     * <p>The shading map is a low-resolution floating-point map
     * that lists the coefficients used to correct for vignetting, for each
     * Bayer color channel.</p>
     * <p>The least shaded section of the image should have a gain factor
     * of 1; all other sections should have gains above 1.</p>
     * <p>When {@link CaptureRequest#COLOR_CORRECTION_MODE android.colorCorrection.mode} = TRANSFORM_MATRIX, the map
     * must take into account the colorCorrection settings.</p>
     * <p>The shading map is for the entire active pixel array, and is not
     * affected by the crop region specified in the request. Each shading map
     * entry is the value of the shading compensation map over a specific
     * pixel on the sensor.  Specifically, with a (N x M) resolution shading
     * map, and an active pixel array size (W x H), shading map entry
     * (x,y) ϵ (0 ... N-1, 0 ... M-1) is the value of the shading map at
     * pixel ( ((W-1)/(N-1)) * x, ((H-1)/(M-1)) * y) for the four color channels.
     * The map is assumed to be bilinearly interpolated between the sample points.</p>
     * <p>The channel order is [R, Geven, Godd, B], where Geven is the green
     * channel for the even rows of a Bayer pattern, and Godd is the odd rows.
     * The shading map is stored in a fully interleaved format, and its size
     * is provided in the camera static metadata by android.lens.info.shadingMapSize.</p>
     * <p>The shading map should have on the order of 30-40 rows and columns,
     * and must be smaller than 64x64.</p>
     * <p>As an example, given a very small map defined as:</p>
     * <pre><code>android.lens.info.shadingMapSize = [ 4, 3 ]
     * android.statistics.lensShadingMap =
     * [ 1.3, 1.2, 1.15, 1.2,  1.2, 1.2, 1.15, 1.2,
     * 1.1, 1.2, 1.2, 1.2,  1.3, 1.2, 1.3, 1.3,
     * 1.2, 1.2, 1.25, 1.1,  1.1, 1.1, 1.1, 1.0,
     * 1.0, 1.0, 1.0, 1.0,  1.2, 1.3, 1.25, 1.2,
     * 1.3, 1.2, 1.2, 1.3,   1.2, 1.15, 1.1, 1.2,
     * 1.2, 1.1, 1.0, 1.2,  1.3, 1.15, 1.2, 1.3 ]
     * </code></pre>
     * <p>The low-resolution scaling map images for each channel are
     * (displayed using nearest-neighbor interpolation):</p>
     * <p><img alt="Red lens shading map" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/red_shading.png" />
     * <img alt="Green (even rows) lens shading map" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/green_e_shading.png" />
     * <img alt="Green (odd rows) lens shading map" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/green_o_shading.png" />
     * <img alt="Blue lens shading map" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/blue_shading.png" /></p>
     * <p>As a visualization only, inverting the full-color map to recover an
     * image of a gray wall (using bicubic interpolation for visual quality) as captured by the sensor gives:</p>
     * <p><img alt="Image of a uniform white wall (inverse shading map)" src="../../../../images/camera2/metadata/android.statistics.lensShadingMap/inv_shading.png" /></p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_MODE
     * @hide
     */
    public static final Key<float[]> STATISTICS_LENS_SHADING_MAP =
            new Key<float[]>("android.statistics.lensShadingMap", float[].class);

    /**
     * <p>The best-fit color channel gains calculated
     * by the camera device's statistics units for the current output frame.</p>
     * <p>This may be different than the gains used for this frame,
     * since statistics processing on data from a new frame
     * typically completes after the transform has already been
     * applied to that frame.</p>
     * <p>The 4 channel gains are defined in Bayer domain,
     * see {@link CaptureRequest#COLOR_CORRECTION_GAINS android.colorCorrection.gains} for details.</p>
     * <p>This value should always be calculated by the auto-white balance (AWB) block,
     * regardless of the android.control.* current values.</p>
     * <p><b>Optional</b> - This value may be {@code null} on some devices.</p>
     *
     * @see CaptureRequest#COLOR_CORRECTION_GAINS
     * @deprecated
     * @hide
     */
    @Deprecated
    public static final Key<float[]> STATISTICS_PREDICTED_COLOR_GAINS =
            new Key<float[]>("android.statistics.predictedColorGains", float[].class);

    /**
     * <p>The best-fit color transform matrix estimate
     * calculated by the camera device's statistics units for the current
     * output frame.</p>
     * <p>The camera device will provide the estimate from its
     * statistics unit on the white balance transforms to use
     * for the next frame. These are the values the camera device believes
     * are the best fit for the current output frame. This may
     * be different than the transform used for this frame, since
     * statistics processing on data from a new frame typically
     * completes after the transform has already been applied to
     * that frame.</p>
     * <p>These estimates must be provided for all frames, even if
     * capture settings and color transforms are set by the application.</p>
     * <p>This value should always be calculated by the auto-white balance (AWB) block,
     * regardless of the android.control.* current values.</p>
     * <p><b>Optional</b> - This value may be {@code null} on some devices.</p>
     * @deprecated
     * @hide
     */
    @Deprecated
    public static final Key<Rational[]> STATISTICS_PREDICTED_COLOR_TRANSFORM =
            new Key<Rational[]>("android.statistics.predictedColorTransform", Rational[].class);

    /**
     * <p>The camera device estimated scene illumination lighting
     * frequency.</p>
     * <p>Many light sources, such as most fluorescent lights, flicker at a rate
     * that depends on the local utility power standards. This flicker must be
     * accounted for by auto-exposure routines to avoid artifacts in captured images.
     * The camera device uses this entry to tell the application what the scene
     * illuminant frequency is.</p>
     * <p>When manual exposure control is enabled
     * (<code>{@link CaptureRequest#CONTROL_AE_MODE android.control.aeMode} == OFF</code> or <code>{@link CaptureRequest#CONTROL_MODE android.control.mode} ==
     * OFF</code>), the {@link CaptureRequest#CONTROL_AE_ANTIBANDING_MODE android.control.aeAntibandingMode} doesn't perform
     * antibanding, and the application can ensure it selects
     * exposure times that do not cause banding issues by looking
     * into this metadata field. See
     * {@link CaptureRequest#CONTROL_AE_ANTIBANDING_MODE android.control.aeAntibandingMode} for more details.</p>
     * <p>Reports NONE if there doesn't appear to be flickering illumination.</p>
     *
     * @see CaptureRequest#CONTROL_AE_ANTIBANDING_MODE
     * @see CaptureRequest#CONTROL_AE_MODE
     * @see CaptureRequest#CONTROL_MODE
     * @see #STATISTICS_SCENE_FLICKER_NONE
     * @see #STATISTICS_SCENE_FLICKER_50HZ
     * @see #STATISTICS_SCENE_FLICKER_60HZ
     */
    public static final Key<Integer> STATISTICS_SCENE_FLICKER =
            new Key<Integer>("android.statistics.sceneFlicker", int.class);

    /**
     * <p>Operating mode for hotpixel map generation.</p>
     * <p>If set to ON, a hotpixel map is returned in {@link CaptureResult#STATISTICS_HOT_PIXEL_MAP android.statistics.hotPixelMap}.
     * If set to OFF, no hotpixel map will be returned.</p>
     * <p>This must be set to a valid mode from {@link CameraCharacteristics#STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES android.statistics.info.availableHotPixelMapModes}.</p>
     *
     * @see CaptureResult#STATISTICS_HOT_PIXEL_MAP
     * @see CameraCharacteristics#STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES
     */
    public static final Key<Boolean> STATISTICS_HOT_PIXEL_MAP_MODE =
            new Key<Boolean>("android.statistics.hotPixelMapMode", boolean.class);

    /**
     * <p>List of <code>(x, y)</code> coordinates of hot/defective pixels on the sensor.</p>
     * <p>A coordinate <code>(x, y)</code> must lie between <code>(0, 0)</code>, and
     * <code>(width - 1, height - 1)</code> (inclusive), which are the top-left and
     * bottom-right of the pixel array, respectively. The width and
     * height dimensions are given in {@link CameraCharacteristics#SENSOR_INFO_PIXEL_ARRAY_SIZE android.sensor.info.pixelArraySize}.
     * This may include hot pixels that lie outside of the active array
     * bounds given by {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE android.sensor.info.activeArraySize}.</p>
     *
     * @see CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE
     * @see CameraCharacteristics#SENSOR_INFO_PIXEL_ARRAY_SIZE
     */
    public static final Key<android.graphics.Point[]> STATISTICS_HOT_PIXEL_MAP =
            new Key<android.graphics.Point[]>("android.statistics.hotPixelMap", android.graphics.Point[].class);

    /**
     * <p>Whether the camera device will output the lens
     * shading map in output result metadata.</p>
     * <p>When set to ON,
     * android.statistics.lensShadingMap will be provided in
     * the output result metadata.</p>
     * @see #STATISTICS_LENS_SHADING_MAP_MODE_OFF
     * @see #STATISTICS_LENS_SHADING_MAP_MODE_ON
     */
    public static final Key<Integer> STATISTICS_LENS_SHADING_MAP_MODE =
            new Key<Integer>("android.statistics.lensShadingMapMode", int.class);

    /**
     * <p>Tonemapping / contrast / gamma curve for the blue
     * channel, to use when {@link CaptureRequest#TONEMAP_MODE android.tonemap.mode} is
     * CONTRAST_CURVE.</p>
     * <p>See android.tonemap.curveRed for more details.</p>
     *
     * @see CaptureRequest#TONEMAP_MODE
     * @hide
     */
    public static final Key<float[]> TONEMAP_CURVE_BLUE =
            new Key<float[]>("android.tonemap.curveBlue", float[].class);

    /**
     * <p>Tonemapping / contrast / gamma curve for the green
     * channel, to use when {@link CaptureRequest#TONEMAP_MODE android.tonemap.mode} is
     * CONTRAST_CURVE.</p>
     * <p>See android.tonemap.curveRed for more details.</p>
     *
     * @see CaptureRequest#TONEMAP_MODE
     * @hide
     */
    public static final Key<float[]> TONEMAP_CURVE_GREEN =
            new Key<float[]>("android.tonemap.curveGreen", float[].class);

    /**
     * <p>Tonemapping / contrast / gamma curve for the red
     * channel, to use when {@link CaptureRequest#TONEMAP_MODE android.tonemap.mode} is
     * CONTRAST_CURVE.</p>
     * <p>Each channel's curve is defined by an array of control points:</p>
     * <pre><code>android.tonemap.curveRed =
     * [ P0in, P0out, P1in, P1out, P2in, P2out, P3in, P3out, ..., PNin, PNout ]
     * 2 &lt;= N &lt;= {@link CameraCharacteristics#TONEMAP_MAX_CURVE_POINTS android.tonemap.maxCurvePoints}</code></pre>
     * <p>These are sorted in order of increasing <code>Pin</code>; it is always
     * guaranteed that input values 0.0 and 1.0 are included in the list to
     * define a complete mapping. For input values between control points,
     * the camera device must linearly interpolate between the control
     * points.</p>
     * <p>Each curve can have an independent number of points, and the number
     * of points can be less than max (that is, the request doesn't have to
     * always provide a curve with number of points equivalent to
     * {@link CameraCharacteristics#TONEMAP_MAX_CURVE_POINTS android.tonemap.maxCurvePoints}).</p>
     * <p>A few examples, and their corresponding graphical mappings; these
     * only specify the red channel and the precision is limited to 4
     * digits, for conciseness.</p>
     * <p>Linear mapping:</p>
     * <pre><code>android.tonemap.curveRed = [ 0, 0, 1.0, 1.0 ]
     * </code></pre>
     * <p><img alt="Linear mapping curve" src="../../../../images/camera2/metadata/android.tonemap.curveRed/linear_tonemap.png" /></p>
     * <p>Invert mapping:</p>
     * <pre><code>android.tonemap.curveRed = [ 0, 1.0, 1.0, 0 ]
     * </code></pre>
     * <p><img alt="Inverting mapping curve" src="../../../../images/camera2/metadata/android.tonemap.curveRed/inverse_tonemap.png" /></p>
     * <p>Gamma 1/2.2 mapping, with 16 control points:</p>
     * <pre><code>android.tonemap.curveRed = [
     * 0.0000, 0.0000, 0.0667, 0.2920, 0.1333, 0.4002, 0.2000, 0.4812,
     * 0.2667, 0.5484, 0.3333, 0.6069, 0.4000, 0.6594, 0.4667, 0.7072,
     * 0.5333, 0.7515, 0.6000, 0.7928, 0.6667, 0.8317, 0.7333, 0.8685,
     * 0.8000, 0.9035, 0.8667, 0.9370, 0.9333, 0.9691, 1.0000, 1.0000 ]
     * </code></pre>
     * <p><img alt="Gamma = 1/2.2 tonemapping curve" src="../../../../images/camera2/metadata/android.tonemap.curveRed/gamma_tonemap.png" /></p>
     * <p>Standard sRGB gamma mapping, per IEC 61966-2-1:1999, with 16 control points:</p>
     * <pre><code>android.tonemap.curveRed = [
     * 0.0000, 0.0000, 0.0667, 0.2864, 0.1333, 0.4007, 0.2000, 0.4845,
     * 0.2667, 0.5532, 0.3333, 0.6125, 0.4000, 0.6652, 0.4667, 0.7130,
     * 0.5333, 0.7569, 0.6000, 0.7977, 0.6667, 0.8360, 0.7333, 0.8721,
     * 0.8000, 0.9063, 0.8667, 0.9389, 0.9333, 0.9701, 1.0000, 1.0000 ]
     * </code></pre>
     * <p><img alt="sRGB tonemapping curve" src="../../../../images/camera2/metadata/android.tonemap.curveRed/srgb_tonemap.png" /></p>
     *
     * @see CameraCharacteristics#TONEMAP_MAX_CURVE_POINTS
     * @see CaptureRequest#TONEMAP_MODE
     * @hide
     */
    public static final Key<float[]> TONEMAP_CURVE_RED =
            new Key<float[]>("android.tonemap.curveRed", float[].class);

    /**
     * <p>Tonemapping / contrast / gamma curve to use when {@link CaptureRequest#TONEMAP_MODE android.tonemap.mode}
     * is CONTRAST_CURVE.</p>
     * <p>The tonemapCurve consist of three curves for each of red, green, and blue
     * channels respectively. The following example uses the red channel as an
     * example. The same logic applies to green and blue channel.
     * Each channel's curve is defined by an array of control points:</p>
     * <pre><code>curveRed =
     * [ P0(in, out), P1(in, out), P2(in, out), P3(in, out), ..., PN(in, out) ]
     * 2 &lt;= N &lt;= {@link CameraCharacteristics#TONEMAP_MAX_CURVE_POINTS android.tonemap.maxCurvePoints}</code></pre>
     * <p>These are sorted in order of increasing <code>Pin</code>; it is always
     * guaranteed that input values 0.0 and 1.0 are included in the list to
     * define a complete mapping. For input values between control points,
     * the camera device must linearly interpolate between the control
     * points.</p>
     * <p>Each curve can have an independent number of points, and the number
     * of points can be less than max (that is, the request doesn't have to
     * always provide a curve with number of points equivalent to
     * {@link CameraCharacteristics#TONEMAP_MAX_CURVE_POINTS android.tonemap.maxCurvePoints}).</p>
     * <p>A few examples, and their corresponding graphical mappings; these
     * only specify the red channel and the precision is limited to 4
     * digits, for conciseness.</p>
     * <p>Linear mapping:</p>
     * <pre><code>curveRed = [ (0, 0), (1.0, 1.0) ]
     * </code></pre>
     * <p><img alt="Linear mapping curve" src="../../../../images/camera2/metadata/android.tonemap.curveRed/linear_tonemap.png" /></p>
     * <p>Invert mapping:</p>
     * <pre><code>curveRed = [ (0, 1.0), (1.0, 0) ]
     * </code></pre>
     * <p><img alt="Inverting mapping curve" src="../../../../images/camera2/metadata/android.tonemap.curveRed/inverse_tonemap.png" /></p>
     * <p>Gamma 1/2.2 mapping, with 16 control points:</p>
     * <pre><code>curveRed = [
     * (0.0000, 0.0000), (0.0667, 0.2920), (0.1333, 0.4002), (0.2000, 0.4812),
     * (0.2667, 0.5484), (0.3333, 0.6069), (0.4000, 0.6594), (0.4667, 0.7072),
     * (0.5333, 0.7515), (0.6000, 0.7928), (0.6667, 0.8317), (0.7333, 0.8685),
     * (0.8000, 0.9035), (0.8667, 0.9370), (0.9333, 0.9691), (1.0000, 1.0000) ]
     * </code></pre>
     * <p><img alt="Gamma = 1/2.2 tonemapping curve" src="../../../../images/camera2/metadata/android.tonemap.curveRed/gamma_tonemap.png" /></p>
     * <p>Standard sRGB gamma mapping, per IEC 61966-2-1:1999, with 16 control points:</p>
     * <pre><code>curveRed = [
     * (0.0000, 0.0000), (0.0667, 0.2864), (0.1333, 0.4007), (0.2000, 0.4845),
     * (0.2667, 0.5532), (0.3333, 0.6125), (0.4000, 0.6652), (0.4667, 0.7130),
     * (0.5333, 0.7569), (0.6000, 0.7977), (0.6667, 0.8360), (0.7333, 0.8721),
     * (0.8000, 0.9063), (0.8667, 0.9389), (0.9333, 0.9701), (1.0000, 1.0000) ]
     * </code></pre>
     * <p><img alt="sRGB tonemapping curve" src="../../../../images/camera2/metadata/android.tonemap.curveRed/srgb_tonemap.png" /></p>
     *
     * @see CameraCharacteristics#TONEMAP_MAX_CURVE_POINTS
     * @see CaptureRequest#TONEMAP_MODE
     */
    public static final Key<android.hardware.camera2.params.TonemapCurve> TONEMAP_CURVE =
            new Key<android.hardware.camera2.params.TonemapCurve>("android.tonemap.curve", android.hardware.camera2.params.TonemapCurve.class);

    /**
     * <p>High-level global contrast/gamma/tonemapping control.</p>
     * <p>When switching to an application-defined contrast curve by setting
     * {@link CaptureRequest#TONEMAP_MODE android.tonemap.mode} to CONTRAST_CURVE, the curve is defined
     * per-channel with a set of <code>(in, out)</code> points that specify the
     * mapping from input high-bit-depth pixel value to the output
     * low-bit-depth value.  Since the actual pixel ranges of both input
     * and output may change depending on the camera pipeline, the values
     * are specified by normalized floating-point numbers.</p>
     * <p>More-complex color mapping operations such as 3D color look-up
     * tables, selective chroma enhancement, or other non-linear color
     * transforms will be disabled when {@link CaptureRequest#TONEMAP_MODE android.tonemap.mode} is
     * CONTRAST_CURVE.</p>
     * <p>This must be set to a valid mode in
     * {@link CameraCharacteristics#TONEMAP_AVAILABLE_TONE_MAP_MODES android.tonemap.availableToneMapModes}.</p>
     * <p>When using either FAST or HIGH_QUALITY, the camera device will
     * emit its own tonemap curve in {@link CaptureRequest#TONEMAP_CURVE android.tonemap.curve}.
     * These values are always available, and as close as possible to the
     * actually used nonlinear/nonglobal transforms.</p>
     * <p>If a request is sent with CONTRAST_CURVE with the camera device's
     * provided curve in FAST or HIGH_QUALITY, the image's tonemap will be
     * roughly the same.</p>
     *
     * @see CameraCharacteristics#TONEMAP_AVAILABLE_TONE_MAP_MODES
     * @see CaptureRequest#TONEMAP_CURVE
     * @see CaptureRequest#TONEMAP_MODE
     * @see #TONEMAP_MODE_CONTRAST_CURVE
     * @see #TONEMAP_MODE_FAST
     * @see #TONEMAP_MODE_HIGH_QUALITY
     */
    public static final Key<Integer> TONEMAP_MODE =
            new Key<Integer>("android.tonemap.mode", int.class);

    /**
     * <p>This LED is nominally used to indicate to the user
     * that the camera is powered on and may be streaming images back to the
     * Application Processor. In certain rare circumstances, the OS may
     * disable this when video is processed locally and not transmitted to
     * any untrusted applications.</p>
     * <p>In particular, the LED <em>must</em> always be on when the data could be
     * transmitted off the device. The LED <em>should</em> always be on whenever
     * data is stored locally on the device.</p>
     * <p>The LED <em>may</em> be off if a trusted application is using the data that
     * doesn't violate the above rules.</p>
     * @hide
     */
    public static final Key<Boolean> LED_TRANSMIT =
            new Key<Boolean>("android.led.transmit", boolean.class);

    /**
     * <p>Whether black-level compensation is locked
     * to its current values, or is free to vary.</p>
     * <p>Whether the black level offset was locked for this frame.  Should be
     * ON if {@link CaptureRequest#BLACK_LEVEL_LOCK android.blackLevel.lock} was ON in the capture request, unless
     * a change in other capture settings forced the camera device to
     * perform a black level reset.</p>
     *
     * @see CaptureRequest#BLACK_LEVEL_LOCK
     */
    public static final Key<Boolean> BLACK_LEVEL_LOCK =
            new Key<Boolean>("android.blackLevel.lock", boolean.class);

    /**
     * <p>The frame number corresponding to the last request
     * with which the output result (metadata + buffers) has been fully
     * synchronized.</p>
     * <p>When a request is submitted to the camera device, there is usually a
     * delay of several frames before the controls get applied. A camera
     * device may either choose to account for this delay by implementing a
     * pipeline and carefully submit well-timed atomic control updates, or
     * it may start streaming control changes that span over several frame
     * boundaries.</p>
     * <p>In the latter case, whenever a request's settings change relative to
     * the previous submitted request, the full set of changes may take
     * multiple frame durations to fully take effect. Some settings may
     * take effect sooner (in less frame durations) than others.</p>
     * <p>While a set of control changes are being propagated, this value
     * will be CONVERGING.</p>
     * <p>Once it is fully known that a set of control changes have been
     * finished propagating, and the resulting updated control settings
     * have been read back by the camera device, this value will be set
     * to a non-negative frame number (corresponding to the request to
     * which the results have synchronized to).</p>
     * <p>Older camera device implementations may not have a way to detect
     * when all camera controls have been applied, and will always set this
     * value to UNKNOWN.</p>
     * <p>FULL capability devices will always have this value set to the
     * frame number of the request corresponding to this result.</p>
     * <p><em>Further details</em>:</p>
     * <ul>
     * <li>Whenever a request differs from the last request, any future
     * results not yet returned may have this value set to CONVERGING (this
     * could include any in-progress captures not yet returned by the camera
     * device, for more details see pipeline considerations below).</li>
     * <li>Submitting a series of multiple requests that differ from the
     * previous request (e.g. r1, r2, r3 s.t. r1 != r2 != r3)
     * moves the new synchronization frame to the last non-repeating
     * request (using the smallest frame number from the contiguous list of
     * repeating requests).</li>
     * <li>Submitting the same request repeatedly will not change this value
     * to CONVERGING, if it was already a non-negative value.</li>
     * <li>When this value changes to non-negative, that means that all of the
     * metadata controls from the request have been applied, all of the
     * metadata controls from the camera device have been read to the
     * updated values (into the result), and all of the graphics buffers
     * corresponding to this result are also synchronized to the request.</li>
     * </ul>
     * <p><em>Pipeline considerations</em>:</p>
     * <p>Submitting a request with updated controls relative to the previously
     * submitted requests may also invalidate the synchronization state
     * of all the results corresponding to currently in-flight requests.</p>
     * <p>In other words, results for this current request and up to
     * {@link CameraCharacteristics#REQUEST_PIPELINE_MAX_DEPTH android.request.pipelineMaxDepth} prior requests may have their
     * android.sync.frameNumber change to CONVERGING.</p>
     *
     * @see CameraCharacteristics#REQUEST_PIPELINE_MAX_DEPTH
     * @see #SYNC_FRAME_NUMBER_CONVERGING
     * @see #SYNC_FRAME_NUMBER_UNKNOWN
     * @hide
     */
    public static final Key<Long> SYNC_FRAME_NUMBER =
            new Key<Long>("android.sync.frameNumber", long.class);

    /*~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~
     * End generated code
     *~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~O@*/
}
