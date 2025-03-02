/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package android.speech.tts;

/**
 * A callback to return speech data synthesized by a text to speech engine.
 *
 * The engine can provide streaming audio by calling
 * {@link #start}, then {@link #audioAvailable} until all audio has been provided, then finally
 * {@link #done}.
 *
 * {@link #error} can be called at any stage in the synthesis process to
 * indicate that an error has occurred, but if the call is made after a call
 * to {@link #done}, it might be discarded.
 *
 * {@link #done} must be called at the end of synthesis, regardless of errors.
 *
 * All methods can be only called on the synthesis thread.
 */
public interface SynthesisCallback {
    /**
     * @return the maximum number of bytes that the TTS engine can pass in a single call of
     *         {@link #audioAvailable}. Calls to {@link #audioAvailable} with data lengths
     *         larger than this value will not succeed.
     */
    public int getMaxBufferSize();

    /**
     * The service should call this when it starts to synthesize audio for this
     * request.
     *
     * This method should only be called on the synthesis thread,
     * while in {@link TextToSpeechService#onSynthesizeText} or
     * {@link TextToSpeechService#onSynthesizeTextV2}.
     *
     * @param sampleRateInHz Sample rate in HZ of the generated audio.
     * @param audioFormat Audio format of the generated audio. Must be one of
     *         the ENCODING_ constants defined in {@link android.media.AudioFormat}.
     * @param channelCount The number of channels. Must be {@code 1} or {@code 2}.
     * @return {@link TextToSpeech#SUCCESS}, {@link TextToSpeech#ERROR}.
     *          {@link TextToSpeechClient.Status#STOPPED} is also possible if called in context of
     *          {@link TextToSpeechService#onSynthesizeTextV2}.
     */
    public int start(int sampleRateInHz, int audioFormat, int channelCount);

    /**
     * The service should call this method when synthesized audio is ready for consumption.
     *
     * This method should only be called on the synthesis thread,
     * while in {@link TextToSpeechService#onSynthesizeText} or
     * {@link TextToSpeechService#onSynthesizeTextV2}.
     *
     * @param buffer The generated audio data. This method will not hold on to {@code buffer},
     *         so the caller is free to modify it after this method returns.
     * @param offset The offset into {@code buffer} where the audio data starts.
     * @param length The number of bytes of audio data in {@code buffer}. This must be
     *         less than or equal to the return value of {@link #getMaxBufferSize}.
     * @return {@link TextToSpeech#SUCCESS} or {@link TextToSpeech#ERROR}.
     *          {@link TextToSpeechClient.Status#STOPPED} is also possible if called in context of
     *          {@link TextToSpeechService#onSynthesizeTextV2}.
     */
    public int audioAvailable(byte[] buffer, int offset, int length);

    /**
     * The service should call this method when all the synthesized audio for a request has
     * been passed to {@link #audioAvailable}.
     *
     * This method should only be called on the synthesis thread,
     * while in {@link TextToSpeechService#onSynthesizeText} or
     * {@link TextToSpeechService#onSynthesizeTextV2}.
     *
     * This method has to be called if {@link #start} and/or {@link #error} was called.
     *
     * @return {@link TextToSpeech#SUCCESS} or {@link TextToSpeech#ERROR}.
     *          {@link TextToSpeechClient.Status#STOPPED} is also possible if called in context of
     *          {@link TextToSpeechService#onSynthesizeTextV2}.
     */
    public int done();

    /**
     * The service should call this method if the speech synthesis fails.
     *
     * This method should only be called on the synthesis thread,
     * while in {@link TextToSpeechService#onSynthesizeText}.
     */
    public void error();


    /**
     * The service should call this method if the speech synthesis fails.
     *
     * This method should only be called on the synthesis thread,
     * while in {@link TextToSpeechService#onSynthesizeText} or
     * {@link TextToSpeechService#onSynthesizeTextV2}.
     *
     * @param errorCode Error code to pass to the client. One of the ERROR_ values from
     *      {@link TextToSpeechClient.Status}
     */
    public void error(int errorCode);

    /**
     * Communicate to client that the original request can't be done and client-requested
     * fallback is happening.
     *
     * Fallback can be requested by the client by setting
     * {@link TextToSpeechClient.Params#FALLBACK_VOICE_NAME} voice parameter with a id of
     * the voice that is expected to be used for the fallback.
     *
     * This method will fail if user called {@link #start(int, int, int)} and/or
     * {@link #done()}.
     *
     * This method should only be called on the synthesis thread,
     * while in {@link TextToSpeechService#onSynthesizeTextV2}.
     *
     * @return {@link TextToSpeech#SUCCESS}, {@link TextToSpeech#ERROR} if client already
     *          called {@link #start(int, int, int)}, {@link TextToSpeechClient.Status#STOPPED}
     *          if stop was requested.
     */
    public int fallback();

    /**
     * Check if {@link #start} was called or not.
     *
     * This method should only be called on the synthesis thread,
     * while in {@link TextToSpeechService#onSynthesizeText} or
     * {@link TextToSpeechService#onSynthesizeTextV2}.
     *
     * Useful for checking if a fallback from network request is possible.
     */
    public boolean hasStarted();

    /**
     * Check if {@link #done} was called or not.
     *
     * This method should only be called on the synthesis thread,
     * while in {@link TextToSpeechService#onSynthesizeText} or
     * {@link TextToSpeechService#onSynthesizeTextV2}.
     *
     * Useful for checking if a fallback from network request is possible.
     */
    public boolean hasFinished();
}
