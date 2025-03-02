/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.layoutlib.bridge.bars;

import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.android.BridgeXmlBlockParser;
import com.android.layoutlib.bridge.impl.ParserFactory;
import com.android.layoutlib.bridge.impl.ResourceHelper;
import com.android.resources.Density;
import com.android.resources.LayoutDirection;
import com.android.resources.ResourceType;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Bitmap_Delegate;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base "bar" class for the window decor around the the edited layout.
 * This is basically an horizontal layout that loads a given layout on creation (it is read
 * through {@link Class#getResourceAsStream(String)}).
 *
 * The given layout should be a merge layout so that all the children belong to this class directly.
 *
 * It also provides a few utility methods to configure the content of the layout.
 */
abstract class CustomBar extends LinearLayout {

    protected abstract TextView getStyleableTextView();

    protected CustomBar(Context context, Density density, int orientation, String layoutPath,
            String name) throws XmlPullParserException {
        super(context);
        setOrientation(orientation);
        if (orientation == LinearLayout.HORIZONTAL) {
            setGravity(Gravity.CENTER_VERTICAL);
        } else {
            setGravity(Gravity.CENTER_HORIZONTAL);
        }

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        XmlPullParser parser = ParserFactory.create(getClass().getResourceAsStream(layoutPath),
                name);

        BridgeXmlBlockParser bridgeParser = new BridgeXmlBlockParser(
                parser, (BridgeContext) context, false /*platformFile*/);

        try {
            inflater.inflate(bridgeParser, this, true);
        } finally {
            bridgeParser.ensurePopped();
        }
    }

    private InputStream getIcon(String iconName, Density[] densityInOut, LayoutDirection direction,
            String[] pathOut, boolean tryOtherDensities) {
        // current density
        Density density = densityInOut[0];

        // bitmap url relative to this class
        if (direction != null) {
            pathOut[0] = "/bars/" + direction.getResourceValue() + "-" + density.getResourceValue()
                    + "/" + iconName;
        } else {
            pathOut[0] = "/bars/" + density.getResourceValue() + "/" + iconName;
        }

        InputStream stream = getClass().getResourceAsStream(pathOut[0]);
        if (stream == null && tryOtherDensities) {
            for (Density d : Density.values()) {
                if (d != density) {
                    densityInOut[0] = d;
                    stream = getIcon(iconName, densityInOut, direction, pathOut,
                            false /*tryOtherDensities*/);
                    if (stream != null) {
                        return stream;
                    }
                }
                // couldn't find resource with direction qualifier. try without.
                if (direction != null) {
                    return getIcon(iconName, densityInOut, null, pathOut, true);
                }
            }
        }

        return stream;
    }

    protected void loadIcon(int index, String iconName, Density density) {
        loadIcon(index, iconName, density, false);
    }

    protected void loadIcon(int index, String iconName, Density density, boolean isRtl) {
        View child = getChildAt(index);
        if (child instanceof ImageView) {
            ImageView imageView = (ImageView) child;

            String[] pathOut = new String[1];
            Density[] densityInOut = new Density[] { density };
            LayoutDirection dir = isRtl ? LayoutDirection.RTL : LayoutDirection.LTR;
            InputStream stream = getIcon(iconName, densityInOut, dir, pathOut,
                    true /*tryOtherDensities*/);
            density = densityInOut[0];

            if (stream != null) {
                // look for a cached bitmap
                Bitmap bitmap = Bridge.getCachedBitmap(pathOut[0], true /*isFramework*/);
                if (bitmap == null) {
                    try {
                        bitmap = Bitmap_Delegate.createBitmap(stream, false /*isMutable*/, density);
                        Bridge.setCachedBitmap(pathOut[0], bitmap, true /*isFramework*/);
                    } catch (IOException e) {
                        return;
                    }
                }

                if (bitmap != null) {
                    BitmapDrawable drawable = new BitmapDrawable(getContext().getResources(),
                            bitmap);
                    imageView.setImageDrawable(drawable);
                }
            }
        }
    }

    protected void loadIcon(int index, String iconReference) {
        ResourceValue value = getResourceValue(iconReference);
        if (value != null) {
            loadIcon(index, value);
        }
    }

    protected void loadIconById(int id, String iconReference) {
        ResourceValue value = getResourceValue(iconReference);
        if (value != null) {
            loadIconById(id, value);
        }
    }


    protected Drawable loadIcon(int index, ResourceType type, String name) {
        BridgeContext bridgeContext = (BridgeContext) mContext;
        RenderResources res = bridgeContext.getRenderResources();

        // find the resource
        ResourceValue value = res.getFrameworkResource(type, name);

        // resolve it if needed
        value = res.resolveResValue(value);
        return loadIcon(index, value);
    }

    private Drawable loadIcon(int index, ResourceValue value) {
        View child = getChildAt(index);
        if (child instanceof ImageView) {
            ImageView imageView = (ImageView) child;

            return loadIcon(imageView, value);
        }

        return null;
    }

    private Drawable loadIconById(int id, ResourceValue value) {
        View child = findViewById(id);
        if (child instanceof ImageView) {
            ImageView imageView = (ImageView) child;

            return loadIcon(imageView, value);
        }

        return null;
    }


    private Drawable loadIcon(ImageView imageView, ResourceValue value) {
        Drawable drawable = ResourceHelper.getDrawable(value, (BridgeContext) mContext);
        if (drawable != null) {
            imageView.setImageDrawable(drawable);
        }

        return drawable;
    }

    protected TextView setText(int index, String stringReference) {
        View child = getChildAt(index);
        if (child instanceof TextView) {
            TextView textView = (TextView) child;
            setText(textView, stringReference);
            return textView;
        }

        return null;
    }

    protected TextView setTextById(int id, String stringReference) {
        View child = findViewById(id);
        if (child instanceof TextView) {
            TextView textView = (TextView) child;
            setText(textView, stringReference);
            return textView;
        }

        return null;
    }

    private void setText(TextView textView, String stringReference) {
        ResourceValue value = getResourceValue(stringReference);
        if (value != null) {
            textView.setText(value.getValue());
        } else {
            textView.setText(stringReference);
        }
    }

    protected void setStyle(String themeEntryName) {

        BridgeContext bridgeContext = (BridgeContext) mContext;
        RenderResources res = bridgeContext.getRenderResources();

        ResourceValue value = res.findItemInTheme(themeEntryName, true /*isFrameworkAttr*/);
        value = res.resolveResValue(value);

        if (value instanceof StyleResourceValue == false) {
            return;
        }

        StyleResourceValue style = (StyleResourceValue) value;

        // get the background
        ResourceValue backgroundValue = res.findItemInStyle(style, "background",
                true /*isFrameworkAttr*/);
        backgroundValue = res.resolveResValue(backgroundValue);
        if (backgroundValue != null) {
            Drawable d = ResourceHelper.getDrawable(backgroundValue, bridgeContext);
            if (d != null) {
                setBackground(d);
            }
        }

        TextView textView = getStyleableTextView();
        if (textView != null) {
            // get the text style
            ResourceValue textStyleValue = res.findItemInStyle(style, "titleTextStyle",
                    true /*isFrameworkAttr*/);
            textStyleValue = res.resolveResValue(textStyleValue);
            if (textStyleValue instanceof StyleResourceValue) {
                StyleResourceValue textStyle = (StyleResourceValue) textStyleValue;

                ResourceValue textSize = res.findItemInStyle(textStyle, "textSize",
                        true /*isFrameworkAttr*/);
                textSize = res.resolveResValue(textSize);

                if (textSize != null) {
                    TypedValue out = new TypedValue();
                    if (ResourceHelper.parseFloatAttribute("textSize", textSize.getValue(), out,
                            true /*requireUnit*/)) {
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                                out.getDimension(bridgeContext.getResources().getDisplayMetrics()));
                    }
                }


                ResourceValue textColor = res.findItemInStyle(textStyle, "textColor",
                        true /*isFrameworkAttr*/);
                textColor = res.resolveResValue(textColor);
                if (textColor != null) {
                    ColorStateList stateList = ResourceHelper.getColorStateList(
                            textColor, bridgeContext);
                    if (stateList != null) {
                        textView.setTextColor(stateList);
                    }
                }
            }
        }
    }

    private ResourceValue getResourceValue(String reference) {
        BridgeContext bridgeContext = (BridgeContext) mContext;
        RenderResources res = bridgeContext.getRenderResources();

        // find the resource
        ResourceValue value = res.findResValue(reference, false /*isFramework*/);

        // resolve it if needed
        return res.resolveResValue(value);
    }
}
