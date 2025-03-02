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

package com.android.systemui.recents;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/* Common code */
public class Utilities {
    /**
     * Calculates a consistent animation duration (ms) for all animations depending on the movement
     * of the object being animated.
     */
    public static int calculateTranslationAnimationDuration(int distancePx) {
        return calculateTranslationAnimationDuration(distancePx, 100);
    }
    public static int calculateTranslationAnimationDuration(int distancePx, int minDuration) {
        RecentsConfiguration config = RecentsConfiguration.getInstance();
        return Math.max(minDuration, (int) (1000f /* ms/s */ *
                (Math.abs(distancePx) / config.animationPxMovementPerSecond)));
    }

    /** Scales a rect about its centroid */
    public static void scaleRectAboutCenter(Rect r, float scale) {
        if (scale != 1.0f) {
            int cx = r.centerX();
            int cy = r.centerY();
            r.offset(-cx, -cy);
            r.left = (int) (r.left * scale + 0.5f);
            r.top = (int) (r.top * scale + 0.5f);
            r.right = (int) (r.right * scale + 0.5f);
            r.bottom = (int) (r.bottom * scale + 0.5f);
            r.offset(cx, cy);
        }
    }

    /** Calculates the luminance-preserved greyscale of a given color. */
    private static int colorToGreyscale(int color) {
        return Math.round(0.2126f * Color.red(color) + 0.7152f * Color.green(color) +
                0.0722f * Color.blue(color));
    }

    /** Returns the ideal color to draw on top of a specified background color. */
    public static int getIdealColorForBackgroundColor(int color, int lightRes, int darkRes) {
        int greyscale = colorToGreyscale(color);
        return (greyscale < 128) ? lightRes : darkRes;
    }
    /** Returns the ideal drawable to draw on top of a specified background color. */
    public static Drawable getIdealResourceForBackgroundColor(int color, Drawable lightRes,
                                                           Drawable darkRes) {
        int greyscale = colorToGreyscale(color);
        return (greyscale < 128) ? lightRes : darkRes;
    }
}
