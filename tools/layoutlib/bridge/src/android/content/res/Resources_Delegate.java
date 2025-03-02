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

package android.content.res;

import java.util.Locale;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;
import com.ibm.icu.util.ULocale;

/**
 * Delegate used to provide new implementation of a select few methods of {@link Resources}
 *
 * Through the layoutlib_create tool, the original  methods of Resources have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 */
public class Resources_Delegate {

    @LayoutlibDelegate
    /*package*/ static String localeToLanguageTag(Resources res, Locale locale)  {
        return ULocale.forLocale(locale).toLanguageTag();
    }
}
