/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Defines an oval shape. 
 * The oval can be drawn to a Canvas with its own draw() method,
 * but more graphical control is available if you instead pass
 * the OvalShape to a {@link android.graphics.drawable.ShapeDrawable}.
 */
public class OvalShape extends RectShape {

    /**
     * OvalShape constructor.
     */
    public OvalShape() {}
    
    @Override
    public void draw(Canvas canvas, Paint paint) {
        canvas.drawOval(rect(), paint);
    }

    @Override
    public boolean getOutline(Outline outline) {
        final RectF rect = rect();
        outline.setOval((int) Math.ceil(rect.left), (int) Math.ceil(rect.top),
                (int) Math.floor(rect.right), (int) Math.floor(rect.bottom));
        return true;
    }
}

