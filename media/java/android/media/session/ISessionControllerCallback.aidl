/* Copyright (C) 2014 The Android Open Source Project
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

package android.media.session;

import android.media.MediaMetadata;
import android.media.session.RouteInfo;
import android.media.session.PlaybackState;
import android.os.Bundle;

/**
 * @hide
 */
oneway interface ISessionControllerCallback {
    void onEvent(String event, in Bundle extras);
    void onRouteChanged(in RouteInfo route);

    // These callbacks are for the TransportController
    void onPlaybackStateChanged(in PlaybackState state);
    void onMetadataChanged(in MediaMetadata metadata);
}