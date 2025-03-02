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

package android.os;

import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.impl.DelegateManager;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import java.util.Map;

/**
 * Delegate implementing the native methods of android.os.SystemProperties
 *
 * Through the layoutlib_create tool, the original native methods of SystemProperties have been
 * replaced by calls to methods of the same name in this delegate class.
 *
 * Because it's a stateless class to start with, there's no need to keep a {@link DelegateManager}
 * around to map int to instance of the delegate.
 */
public class SystemProperties_Delegate {

    @LayoutlibDelegate
    /*package*/ static String native_get(String key) {
        return native_get(key, "");
    }

    @LayoutlibDelegate
    /*package*/ static String native_get(String key, String def) {
        Map<String, String> properties = Bridge.getPlatformProperties();
        String value = properties.get(key);
        if (value != null) {
            return value;
        }

        return def;
    }
}
