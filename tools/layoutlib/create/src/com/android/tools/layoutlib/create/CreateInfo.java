/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.tools.layoutlib.create;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;
import com.android.tools.layoutlib.java.AutoCloseable;
import com.android.tools.layoutlib.java.Charsets;
import com.android.tools.layoutlib.java.IntegralToString;
import com.android.tools.layoutlib.java.Objects;
import com.android.tools.layoutlib.java.UnsafeByteSequence;

/**
 * Describes the work to be done by {@link AsmGenerator}.
 */
public final class CreateInfo implements ICreateInfo {

    /**
     * Returns the list of class from layoutlib_create to inject in layoutlib.
     * The list can be empty but must not be null.
     */
    @Override
    public Class<?>[] getInjectedClasses() {
        return INJECTED_CLASSES;
    }

    /**
     * Returns the list of methods to rewrite as delegates.
     * The list can be empty but must not be null.
     */
    @Override
    public String[] getDelegateMethods() {
        return DELEGATE_METHODS;
    }

    /**
     * Returns the list of classes on which to delegate all native methods.
     * The list can be empty but must not be null.
     */
    @Override
    public String[] getDelegateClassNatives() {
        return DELEGATE_CLASS_NATIVES;
    }

    /**
     * Returns The list of methods to stub out. Each entry must be in the form
     * "package.package.OuterClass$InnerClass#MethodName".
     * The list can be empty but must not be null.
     * <p/>
     * This usage is deprecated. Please use method 'delegates' instead.
     */
    @Override
    public String[] getOverriddenMethods() {
        return OVERRIDDEN_METHODS;
    }

    /**
     * Returns the list of classes to rename, must be an even list: the binary FQCN
     * of class to replace followed by the new FQCN.
     * The list can be empty but must not be null.
     */
    @Override
    public String[] getRenamedClasses() {
        return RENAMED_CLASSES;
    }

    /**
     * Returns the list of classes for which the methods returning them should be deleted.
     * The array contains a list of null terminated section starting with the name of the class
     * to rename in which the methods are deleted, followed by a list of return types identifying
     * the methods to delete.
     * The list can be empty but must not be null.
     */
    @Override
    public String[] getDeleteReturns() {
        return DELETE_RETURNS;
    }

    /**
     * Returns the list of classes to refactor, must be an even list: the binary FQCN of class to
     * replace followed by the new FQCN. All references to the old class should be updated to the
     * new class. The list can be empty but must not be null.
     */
    @Override
    public String[] getJavaPkgClasses() {
      return JAVA_PKG_CLASSES;
    }
    //-----

    /**
     * The list of class from layoutlib_create to inject in layoutlib.
     */
    private final static Class<?>[] INJECTED_CLASSES = new Class<?>[] {
            OverrideMethod.class,
            MethodListener.class,
            MethodAdapter.class,
            ICreateInfo.class,
            CreateInfo.class,
            LayoutlibDelegate.class,
            /* Java package classes */
            AutoCloseable.class,
            Objects.class,
            IntegralToString.class,
            UnsafeByteSequence.class,
            Charsets.class,
        };

    /**
     * The list of methods to rewrite as delegates.
     */
    public final static String[] DELEGATE_METHODS = new String[] {
        "android.app.Fragment#instantiate", //(Landroid/content/Context;Ljava/lang/String;Landroid/os/Bundle;)Landroid/app/Fragment;",
        "android.content.res.Resources$Theme#obtainStyledAttributes",
        "android.content.res.Resources$Theme#resolveAttribute",
        "android.content.res.Resources$Theme#resolveAttributes",
        "android.content.res.Resources#localeToLanguageTag",
        "android.content.res.AssetManager#newTheme",
        "android.content.res.AssetManager#deleteTheme",
        "android.content.res.AssetManager#applyThemeStyle",
        "android.content.res.TypedArray#getValueAt",
        "android.content.res.TypedArray#obtain",
        "android.graphics.BitmapFactory#finishDecode",
        "android.graphics.Typeface#getSystemFontConfigLocation",
        "android.os.Handler#sendMessageAtTime",
        "android.os.HandlerThread#run",
        "android.text.format.DateFormat#is24HourFormat",
        "android.util.Xml#newPullParser",
        "android.view.Choreographer#getRefreshRate",
        "android.view.Display#updateDisplayInfoLocked",
        "android.view.Display#getWindowManager",
        "android.view.LayoutInflater#rInflate",
        "android.view.LayoutInflater#parseInclude",
        "android.view.View#isInEditMode",
        "android.view.ViewRootImpl#isInTouchMode",
        "android.view.WindowManagerGlobal#getWindowManagerService",
        "android.view.inputmethod.InputMethodManager#getInstance",
        "android.view.MenuInflater#registerMenu",
        "com.android.internal.view.menu.MenuBuilder#createNewMenuItem",
        "com.android.internal.util.XmlUtils#convertValueToInt",
        "com.android.internal.textservice.ITextServicesManager$Stub#asInterface",
        "android.os.SystemProperties#native_get",
    };

    /**
     * The list of classes on which to delegate all native methods.
     */
    public final static String[] DELEGATE_CLASS_NATIVES = new String[] {
        "android.animation.PropertyValuesHolder",
        "android.graphics.AvoidXfermode",
        "android.graphics.Bitmap",
        "android.graphics.BitmapFactory",
        "android.graphics.BitmapShader",
        "android.graphics.BlurMaskFilter",
        "android.graphics.Canvas",
        "android.graphics.ColorFilter",
        "android.graphics.ColorMatrixColorFilter",
        "android.graphics.ComposePathEffect",
        "android.graphics.ComposeShader",
        "android.graphics.CornerPathEffect",
        "android.graphics.DashPathEffect",
        "android.graphics.DiscretePathEffect",
        "android.graphics.DrawFilter",
        "android.graphics.EmbossMaskFilter",
        "android.graphics.FontFamily",
        "android.graphics.LayerRasterizer",
        "android.graphics.LightingColorFilter",
        "android.graphics.LinearGradient",
        "android.graphics.MaskFilter",
        "android.graphics.Matrix",
        "android.graphics.NinePatch",
        "android.graphics.Paint",
        "android.graphics.PaintFlagsDrawFilter",
        "android.graphics.Path",
        "android.graphics.PathDashPathEffect",
        "android.graphics.PathEffect",
        "android.graphics.PixelXorXfermode",
        "android.graphics.PorterDuffColorFilter",
        "android.graphics.PorterDuffXfermode",
        "android.graphics.RadialGradient",
        "android.graphics.Rasterizer",
        "android.graphics.Region",
        "android.graphics.Shader",
        "android.graphics.SumPathEffect",
        "android.graphics.SweepGradient",
        "android.graphics.Typeface",
        "android.graphics.Xfermode",
        "android.os.SystemClock",
        "android.text.AndroidBidi",
        "android.text.format.Time",
        "android.util.FloatMath",
        "android.view.Display",
        "libcore.icu.DateIntervalFormat",
        "libcore.icu.ICU",
    };

    /**
     * The list of methods to stub out. Each entry must be in the form
     *  "package.package.OuterClass$InnerClass#MethodName".
     *  This usage is deprecated. Please use method 'delegates' instead.
     */
    private final static String[] OVERRIDDEN_METHODS = new String[] {
    };

    /**
     *  The list of classes to rename, must be an even list: the binary FQCN
     *  of class to replace followed by the new FQCN.
     */
    private final static String[] RENAMED_CLASSES =
        new String[] {
            "android.os.ServiceManager",                       "android.os._Original_ServiceManager",
            "android.util.LruCache",                           "android.util._Original_LruCache",
            "android.view.SurfaceView",                        "android.view._Original_SurfaceView",
            "android.view.accessibility.AccessibilityManager", "android.view.accessibility._Original_AccessibilityManager",
            "android.webkit.WebView",                          "android.webkit._Original_WebView",
            "com.android.internal.policy.PolicyManager",       "com.android.internal.policy._Original_PolicyManager",
        };

    /**
     * The list of class references to update, must be an even list: the binary
     * FQCN of class to replace followed by the new FQCN. The classes to
     * replace are to be excluded from the output.
     */
    private final static String[] JAVA_PKG_CLASSES =
        new String[] {
            "java.lang.AutoCloseable",                         "com.android.tools.layoutlib.java.AutoCloseable",
            "java.util.Objects",                               "com.android.tools.layoutlib.java.Objects",
            "java.nio.charset.Charsets",                       "com.android.tools.layoutlib.java.Charsets",
            "java.lang.IntegralToString",                      "com.android.tools.layoutlib.java.IntegralToString",
            "java.lang.UnsafeByteSequence",                    "com.android.tools.layoutlib.java.UnsafeByteSequence",
        };

    /**
     * List of classes for which the methods returning them should be deleted.
     * The array contains a list of null terminated section starting with the name of the class
     * to rename in which the methods are deleted, followed by a list of return types identifying
     * the methods to delete.
     */
    private final static String[] DELETE_RETURNS =
        new String[] {
            null };                         // separator, for next class/methods list.
}

