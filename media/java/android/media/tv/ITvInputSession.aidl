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

package android.media.tv;

import android.graphics.Rect;
import android.net.Uri;
import android.view.Surface;

/**
 * Sub-interface of ITvInputService which is created per session and has its own context.
 * @hide
 */
oneway interface ITvInputSession {
    void release();

    void setSurface(in Surface surface);
    // TODO: Remove this once it becomes irrelevant for applications to handle audio focus. The plan
    // is to introduce some new concepts that will solve a number of problems in audio policy today.
    void setVolume(float volume);
    void tune(in Uri channelUri);

    void createOverlayView(in IBinder windowToken, in Rect frame);
    void relayoutOverlayView(in Rect frame);
    void removeOverlayView();
}
