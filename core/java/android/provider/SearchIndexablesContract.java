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

package android.provider;

import android.content.ContentResolver;

/**
 * Describe the contract for an Indexable data.
 *
 * @hide
 */
public class SearchIndexablesContract {

    /**
     * Intent action used to identify {@link SearchIndexablesProvider}
     * instances. This is used in the {@code <intent-filter>} of a {@code <provider>}.
     */
    public static final String PROVIDER_INTERFACE =
            "android.content.action.SEARCH_INDEXABLES_PROVIDER";

    private static final String SETTINGS = "settings";

    /**
     * Indexable reference names.
     */
    public static final String INDEXABLES_XML_RES = "indexables_xml_res";

    /**
     * ContentProvider path for indexable xml resources.
     */
    public static final String INDEXABLES_XML_RES_PATH = SETTINGS + "/" + INDEXABLES_XML_RES;

    /**
     * Indexable raw data names.
     */
    public static final String INDEXABLES_RAW = "indexables_raw";

    /**
     * ContentProvider path for indexable raw data.
     */
    public static final String INDEXABLES_RAW_PATH = SETTINGS + "/" + INDEXABLES_RAW;

    /**
     * Non indexable data keys.
     */
    public static final String NON_INDEXABLES_KEYS = "non_indexables_key";

    /**
     * ContentProvider path for non indexable data keys.
     */
    public static final String NON_INDEXABLES_KEYS_PATH = SETTINGS + "/" + NON_INDEXABLES_KEYS;

    /**
     * Indexable xml resources colums.
     */
    public static final String[] INDEXABLES_XML_RES_COLUMNS = new String[] {
            XmlResource.COLUMN_RANK,                    // 0
            XmlResource.COLUMN_XML_RESID,               // 1
            XmlResource.COLUMN_CLASS_NAME,              // 2
            XmlResource.COLUMN_ICON_RESID,              // 3
            XmlResource.COLUMN_INTENT_ACTION,           // 4
            XmlResource.COLUMN_INTENT_TARGET_PACKAGE,   // 5
            XmlResource.COLUMN_INTENT_TARGET_CLASS      // 6
    };

    /**
     * Indexable xml resources colums indices.
     */
    public static final int COLUMN_INDEX_XML_RES_RANK = 0;
    public static final int COLUMN_INDEX_XML_RES_RESID = 1;
    public static final int COLUMN_INDEX_XML_RES_CLASS_NAME = 2;
    public static final int COLUMN_INDEX_XML_RES_ICON_RESID = 3;
    public static final int COLUMN_INDEX_XML_RES_INTENT_ACTION = 4;
    public static final int COLUMN_INDEX_XML_RES_INTENT_TARGET_PACKAGE = 5;
    public static final int COLUMN_INDEX_XML_RES_INTENT_TARGET_CLASS = 6;

    /**
     * Indexable raw data colums.
     */
    public static final String[] INDEXABLES_RAW_COLUMNS = new String[] {
            RawData.COLUMN_RANK,                    // 0
            RawData.COLUMN_TITLE,                   // 1
            RawData.COLUMN_SUMMARY_ON,              // 2
            RawData.COLUMN_SUMMARY_OFF,             // 3
            RawData.COLUMN_ENTRIES,                 // 4
            RawData.COLUMN_KEYWORDS,                // 5
            RawData.COLUMN_SCREEN_TITLE,            // 6
            RawData.COLUMN_CLASS_NAME,              // 7
            RawData.COLUMN_ICON_RESID,              // 8
            RawData.COLUMN_INTENT_ACTION,           // 9
            RawData.COLUMN_INTENT_TARGET_PACKAGE,   // 10
            RawData.COLUMN_INTENT_TARGET_CLASS,     // 11
            RawData.COLUMN_KEY,                     // 12
    };

    /**
     * Indexable raw data colums indices.
     */
    public static final int COLUMN_INDEX_RAW_RANK = 0;
    public static final int COLUMN_INDEX_RAW_TITLE = 1;
    public static final int COLUMN_INDEX_RAW_SUMMARY_ON = 2;
    public static final int COLUMN_INDEX_RAW_SUMMARY_OFF = 3;
    public static final int COLUMN_INDEX_RAW_ENTRIES = 4;
    public static final int COLUMN_INDEX_RAW_KEYWORDS = 5;
    public static final int COLUMN_INDEX_RAW_SCREEN_TITLE = 6;
    public static final int COLUMN_INDEX_RAW_CLASS_NAME = 7;
    public static final int COLUMN_INDEX_RAW_ICON_RESID = 8;
    public static final int COLUMN_INDEX_RAW_INTENT_ACTION = 9;
    public static final int COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE = 10;
    public static final int COLUMN_INDEX_RAW_INTENT_TARGET_CLASS = 11;
    public static final int COLUMN_INDEX_RAW_KEY = 12;

    /**
     * Indexable raw data colums.
     */
    public static final String[] NON_INDEXABLES_KEYS_COLUMNS = new String[] {
            NonIndexableKey.COLUMN_KEY_VALUE      // 0
    };

    /**
     * Non indexable data keys colums indices.
     */
    public static final int COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE = 0;

    /**
     * Constants related to a {@link SearchIndexableResource}.
     *
     * This is a description of
     */
    public static final class XmlResource extends BaseColumns {
        private XmlResource() {
        }

        public static final String MIME_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE +
                "/" + INDEXABLES_XML_RES;

        /**
         * XML resource ID for the {@link android.preference.PreferenceScreen} to load and index.
         */
        public static final String COLUMN_XML_RESID = "xmlResId";
    }

    /**
     * Constants related to a {@link SearchIndexableData}.
     *
     * This is the raw data that is stored into an Index. This is related to
     * {@link android.preference.Preference} and its attributes like
     * {@link android.preference.Preference#getTitle()},
     * {@link android.preference.Preference#getSummary()}, etc.
     *
     */
    public static final class RawData extends BaseColumns {
        private RawData() {
        }

        public static final String MIME_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE +
                "/" + INDEXABLES_RAW;

        /**
         * Title's raw data.
         */
        public static final String COLUMN_TITLE = "title";

        /**
         * Summary's raw data when the data is "ON".
         */
        public static final String COLUMN_SUMMARY_ON = "summaryOn";

        /**
         * Summary's raw data when the data is "OFF".
         */
        public static final String COLUMN_SUMMARY_OFF = "summaryOff";

        /**
         * Entries associated with the raw data (when the data can have several values).
         */
        public static final String COLUMN_ENTRIES = "entries";

        /**
         * Keywords' raw data.
         */
        public static final String COLUMN_KEYWORDS = "keywords";

        /**
         * Fragment or Activity title associated with the raw data.
         */
        public static final String COLUMN_SCREEN_TITLE = "screenTitle";

        /**
         * Key associated with the raw data. The key needs to be unique.
         */
        public static final String COLUMN_KEY = "key";
    }

    /**
     * Constants related to a {@link SearchIndexableResource} and {@link SearchIndexableData}.
     *
     * This is a description of a data (thru its unique key) that cannot be indexed.
     */
    public static final class NonIndexableKey extends BaseColumns {
        private NonIndexableKey() {
        }

        public static final String MIME_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE +
                "/" + NON_INDEXABLES_KEYS;

        /**
         * Key for the non indexable data.
         */
        public static final String COLUMN_KEY_VALUE = "key";
    }

    /**
     * The base columns.
     */
    private static class BaseColumns {
        private BaseColumns() {
        }

        /**
         * Rank of the data. This is an integer used for ranking the search results. This is
         * application specific.
         */
        public static final String COLUMN_RANK = "rank";

        /**
         * Class name associated with the data (usually a Fragment class name).
         */
        public static final String COLUMN_CLASS_NAME = "className";

        /**
         * Icon resource ID for the data.
         */
        public static final String COLUMN_ICON_RESID = "iconResId";

        /**
         * Intent action associated with the data.
         */
        public static final String COLUMN_INTENT_ACTION = "intentAction";

        /**
         * Intent target package associated with the data.
         */
        public static final String COLUMN_INTENT_TARGET_PACKAGE = "intentTargetPackage";

        /**
         * Intent target class associated with the data.
         */
        public static final String COLUMN_INTENT_TARGET_CLASS = "intentTargetClass";
    }
}
