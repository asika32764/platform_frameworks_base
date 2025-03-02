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

package android.graphics;

import com.android.ide.common.rendering.api.LayoutLog;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.impl.DelegateManager;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.content.res.AssetManager;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.graphics.Typeface_Delegate.SYSTEM_FONTS;

/**
 * Delegate implementing the native methods of android.graphics.FontFamily
 *
 * Through the layoutlib_create tool, the original native methods of FontFamily have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 * This class behaves like the original native implementation, but in Java, keeping previously
 * native data into its own objects and mapping them to int that are sent back and forth between
 * it and the original FontFamily class.
 *
 * @see DelegateManager
 */
public class FontFamily_Delegate {

    // FONT_SUFFIX_ITALIC will always match FONT_SUFFIX_BOLDITALIC and hence it must be checked
    // separately.
    private static final String FONT_SUFFIX_BOLDITALIC = "BoldItalic.ttf";
    private static final String FONT_SUFFIX_BOLD = "Bold.ttf";
    private static final String FONT_SUFFIX_ITALIC = "Italic.ttf";

    /**
     * A class associating {@link Font} with its metadata.
     */
    private static final class FontInfo {
        Font mFont;
        /** Regular, Bold, Italic, or BoldItalic. */
        int mStyle;
    }

    // ---- delegate manager ----
    private static final DelegateManager<FontFamily_Delegate> sManager =
            new DelegateManager<FontFamily_Delegate>(FontFamily_Delegate.class);

    // ---- delegate helper data ----
    private static String sFontLocation;
    private static final List<FontFamily_Delegate> sPostInitDelegate = new
            ArrayList<FontFamily_Delegate>();


    // ---- delegate data ----
    private List<FontInfo> mFonts = new ArrayList<FontInfo>();
    /**
     * The variant of the Font Family - compact or elegant.
     * 0 is unspecified, 1 is compact and 2 is elegant. This needs to be kept in sync with values in
     * android.graphics.FontFamily
     *
     * @see Paint#setElegantTextHeight(boolean)
     */
    private FontVariant mVariant;
    // Path of fonts that haven't been created since sFontLoader hasn't been initialized.
    private List<String> mPath = new ArrayList<String>();


    // ---- Public Helper methods ----

    public static FontFamily_Delegate getDelegate(long nativeFontFamily) {
        return sManager.getDelegate(nativeFontFamily);
    }

    public static synchronized void setFontLocation(String fontLocation) {
        sFontLocation = fontLocation;
        for (FontFamily_Delegate fontFamily : sPostInitDelegate) {
            fontFamily.init();
        }
        sPostInitDelegate.clear();
    }

    public Font getFont(int style) {
        FontInfo plainFont = null;
        for (FontInfo font : mFonts) {
            if (font.mStyle == style) {
                return font.mFont;
            }
            if (font.mStyle == Font.PLAIN && plainFont == null) {
                plainFont = font;
            }
        }

        // No font with the mentioned style is found. Try to derive one.
        if (plainFont != null && style > 0 && style < 4) {
            FontInfo styledFont = new FontInfo();
            styledFont.mFont = plainFont.mFont.deriveFont(style);
            styledFont.mStyle = style;
            // Add the font to the list of fonts so that we don't have to derive it the next time.
            mFonts.add(styledFont);
            return styledFont.mFont;
        }
        return null;
    }

    public FontVariant getVariant() {
        return mVariant;
    }


    // ---- native methods ----

    @LayoutlibDelegate
    /*package*/ static long nCreateFamily(String lang, int variant) {
        // TODO: support lang. This is required for japanese locale.
        FontFamily_Delegate delegate = new FontFamily_Delegate();
        // variant can be 0, 1 or 2.
        assert variant < 3;
        delegate.mVariant = FontVariant.values()[variant];
        if (sFontLocation != null) {
            delegate.init();
        } else {
            sPostInitDelegate.add(delegate);
        }
        return sManager.addNewDelegate(delegate);
    }

    @LayoutlibDelegate
    /*package*/ static void nUnrefFamily(long nativePtr) {
        // Removing the java reference for the object doesn't mean that it's freed for garbage
        // collection. Typeface_Delegate may still hold a reference for it.
        sManager.removeJavaReferenceFor(nativePtr);
    }

    @LayoutlibDelegate
    /*package*/ static boolean nAddFont(long nativeFamily, String path) {
        FontFamily_Delegate delegate = getDelegate(nativeFamily);
        if (delegate != null) {
            if (sFontLocation == null) {
                delegate.mPath.add(path);
                return true;
            }
            return delegate.addFont(path);
        }
        return false;
    }

    @LayoutlibDelegate
    /*package*/ static boolean nAddFontFromAsset(long nativeFamily, AssetManager mgr, String path) {
        Bridge.getLog().fidelityWarning(LayoutLog.TAG_UNSUPPORTED,
                "FontFamily.addFontFromAsset is not supported.", null /*throwable*/, null /*data*/);
        return false;
    }

    private void init() {
        for (String path : mPath) {
            addFont(path);
        }
        mPath = null;
    }

    private boolean addFont(String path) {
        Font font = loadFont(path);
        if (font == null) {
            return false;
        }
        FontInfo fontInfo = new FontInfo();
        fontInfo.mFont = font;
        addFontMetadata(fontInfo, path);
        // TODO ensure that mFonts doesn't have the font with this style already.
        mFonts.add(fontInfo);
        return true;
    }

    private static void addFontMetadata(FontInfo fontInfo, String path) {
        int style = Font.PLAIN;
        String fontName = path.substring(path.lastIndexOf('/'), path.length());
        if (fontName.endsWith(FONT_SUFFIX_BOLDITALIC)) {
            style = Font.BOLD | Font.ITALIC;
        } else if (fontName.endsWith(FONT_SUFFIX_BOLD)) {
            style = Font.BOLD;
        } else if (fontName.endsWith(FONT_SUFFIX_ITALIC)) {
            style = Font.ITALIC;
        }
        fontInfo.mStyle = style;
    }

    private static Font loadFont(String path) {
        if (path.startsWith(SYSTEM_FONTS) ) {
            String relativePath = path.substring(SYSTEM_FONTS.length());
            File f = new File(sFontLocation, relativePath);

            try {
                return Font.createFont(Font.TRUETYPE_FONT, f);
            } catch (Exception e) {
                Bridge.getLog().fidelityWarning(LayoutLog.TAG_BROKEN,
                        String.format("Unable to load font %1$s", relativePath),
                        e /*throwable*/, null /*data*/);
            }
        } else {
            Bridge.getLog().fidelityWarning(LayoutLog.TAG_UNSUPPORTED,
                    "Only platform fonts located in " + SYSTEM_FONTS + "can be loaded.",
                    null /*throwable*/, null /*data*/);
        }

        return null;
    }


    // ---- Public helper class ----

    public enum FontVariant {
        // The order needs to be kept in sync with android.graphics.FontFamily.
        NONE, COMPACT, ELEGANT
    }
}
