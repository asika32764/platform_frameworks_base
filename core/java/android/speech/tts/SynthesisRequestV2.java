/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.speech.tts.TextToSpeechClient.UtteranceId;
import android.util.Log;

/**
 * Service-side representation of a synthesis request from a V2 API client. Contains:
 * <ul>
 *   <li>The markup object to synthesize containing the utterance.</li>
 *   <li>The id of the utterance (String, result of {@link UtteranceId#toUniqueString()}</li>
 *   <li>The synthesis voice name (String, result of {@link VoiceInfo#getName()})</li>
 *   <li>Voice parameters (Bundle of parameters)</li>
 *   <li>Audio parameters (Bundle of parameters)</li>
 * </ul>
 */
public final class SynthesisRequestV2 implements Parcelable {

    private static final String TAG = "SynthesisRequestV2";

    /** Synthesis markup */
    private final Markup mMarkup;

    /** Synthesis id. */
    private final String mUtteranceId;

    /** Voice ID. */
    private final String mVoiceName;

    /** Voice Parameters. */
    private final Bundle mVoiceParams;

    /** Audio Parameters. */
    private final Bundle mAudioParams;

    /**
     * Constructor for test purposes.
     */
    public SynthesisRequestV2(Markup markup, String utteranceId, String voiceName,
            Bundle voiceParams, Bundle audioParams) {
        this.mMarkup = markup;
        this.mUtteranceId = utteranceId;
        this.mVoiceName = voiceName;
        this.mVoiceParams = voiceParams;
        this.mAudioParams = audioParams;
    }

    /**
     * Parcel based constructor.
     *
     * @hide
     */
    public SynthesisRequestV2(Parcel in) {
        this.mMarkup = (Markup) in.readValue(Markup.class.getClassLoader());
        this.mUtteranceId = in.readString();
        this.mVoiceName = in.readString();
        this.mVoiceParams = in.readBundle();
        this.mAudioParams = in.readBundle();
    }

    /**
     * Constructor to request the synthesis of a sentence.
     */
    SynthesisRequestV2(Markup markup, String utteranceId, RequestConfig rconfig) {
        this.mMarkup = markup;
        this.mUtteranceId = utteranceId;
        this.mVoiceName = rconfig.getVoice().getName();
        this.mVoiceParams = rconfig.getVoiceParams();
        this.mAudioParams = rconfig.getAudioParams();
    }

    /**
     * Write to parcel.
     *
     * @hide
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(mMarkup);
        dest.writeString(mUtteranceId);
        dest.writeString(mVoiceName);
        dest.writeBundle(mVoiceParams);
        dest.writeBundle(mAudioParams);
    }

    /**
     * @return the text which should be synthesized.
     */
    public String getText() {
        if (mMarkup.getPlainText() == null) {
            Log.e(TAG, "Plaintext of markup is null.");
            return "";
        }
        return mMarkup.getPlainText();
    }

    /**
     * @return the markup which should be synthesized.
     */
    public Markup getMarkup() {
        return mMarkup;
    }

    /**
     * @return the id of the synthesis request. It's an output of a call to the
     * {@link UtteranceId#toUniqueString()} method of the {@link UtteranceId} associated with
     * this request.
     */
    public String getUtteranceId() {
        return mUtteranceId;
    }

    /**
     * @return the name of the voice to use for this synthesis request. Result of a call to
     * the {@link VoiceInfo#getName()} method.
     */
    public String getVoiceName() {
        return mVoiceName;
    }

    /**
     * @return bundle of voice parameters.
     */
    public Bundle getVoiceParams() {
        return mVoiceParams;
    }

    /**
     * @return bundle of audio parameters.
     */
    public Bundle getAudioParams() {
        return mAudioParams;
    }

    /**
     * Parcel creators.
     *
     * @hide
     */
    public static final Parcelable.Creator<SynthesisRequestV2> CREATOR =
            new Parcelable.Creator<SynthesisRequestV2>() {
        @Override
        public SynthesisRequestV2 createFromParcel(Parcel source) {
            return new SynthesisRequestV2(source);
        }

        @Override
        public SynthesisRequestV2[] newArray(int size) {
            return new SynthesisRequestV2[size];
        }
    };

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }
}
