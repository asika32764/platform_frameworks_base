/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.devicepolicy;

import static android.Manifest.permission.MANAGE_CA_CERTIFICATES;

import com.android.internal.R;
import com.android.internal.app.IAppOpsService;
import com.android.internal.os.storage.ExternalStorageFormatter;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.JournaledFile;
import com.android.internal.util.XmlUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.org.conscrypt.TrustedCertificateStore;
import com.android.server.SystemService;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.IActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IDevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.net.ConnectivityManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ProxyInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.Process;
import android.os.RecoverySystem;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.security.Credentials;
import android.security.IKeyChainService;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;
import android.view.IWindowManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of the device policy APIs.
 */
public class DevicePolicyManagerService extends IDevicePolicyManager.Stub {

    private static final String LOG_TAG = "DevicePolicyManagerService";

    private static final String DEVICE_POLICIES_XML = "device_policies.xml";

    private static final String LOCK_TASK_COMPONENTS_XML = "lock-task-component";

    private static final int REQUEST_EXPIRE_PASSWORD = 5571;

    private static final long MS_PER_DAY = 86400 * 1000;

    private static final long EXPIRATION_GRACE_PERIOD_MS = 5 * MS_PER_DAY; // 5 days, in ms

    protected static final String ACTION_EXPIRED_PASSWORD_NOTIFICATION
            = "com.android.server.ACTION_EXPIRED_PASSWORD_NOTIFICATION";

    private static final int MONITORING_CERT_NOTIFICATION_ID = R.string.ssl_ca_cert_warning;

    private static final boolean DBG = false;

    private static final String ATTR_PERMISSION_PROVIDER = "permission-provider";

    final Context mContext;
    final UserManager mUserManager;
    final PowerManager.WakeLock mWakeLock;

    IPowerManager mIPowerManager;
    IWindowManager mIWindowManager;
    NotificationManager mNotificationManager;

    // Stores and loads state on device and profile owners.
    private DeviceOwner mDeviceOwner;

    /**
     * Whether or not device admin feature is supported. If it isn't return defaults for all
     * public methods.
     */
    private boolean mHasFeature;

    public static final class Lifecycle extends SystemService {
        private DevicePolicyManagerService mService;

        public Lifecycle(Context context) {
            super(context);
            mService = new DevicePolicyManagerService(context);
        }

        @Override
        public void onStart() {
            publishBinderService(Context.DEVICE_POLICY_SERVICE, mService);
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == PHASE_LOCK_SETTINGS_READY) {
                mService.systemReady();
            }
        }
    }
    public static class DevicePolicyData {
        int mActivePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        int mActivePasswordLength = 0;
        int mActivePasswordUpperCase = 0;
        int mActivePasswordLowerCase = 0;
        int mActivePasswordLetters = 0;
        int mActivePasswordNumeric = 0;
        int mActivePasswordSymbols = 0;
        int mActivePasswordNonLetter = 0;
        int mFailedPasswordAttempts = 0;

        int mUserHandle;
        int mPasswordOwner = -1;
        long mLastMaximumTimeToLock = -1;

        final HashMap<ComponentName, ActiveAdmin> mAdminMap
                = new HashMap<ComponentName, ActiveAdmin>();
        final ArrayList<ActiveAdmin> mAdminList
                = new ArrayList<ActiveAdmin>();

        // This is the list of component allowed to start lock task mode.
        final List<String> mLockTaskPackages = new ArrayList<String>();

        ComponentName mRestrictionsProvider;

        public DevicePolicyData(int userHandle) {
            mUserHandle = userHandle;
        }
    }

    final SparseArray<DevicePolicyData> mUserData = new SparseArray<DevicePolicyData>();

    Handler mHandler = new Handler();

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE,
                    getSendingUserId());
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || ACTION_EXPIRED_PASSWORD_NOTIFICATION.equals(action)) {
                if (DBG) Slog.v(LOG_TAG, "Sending password expiration notifications for action "
                        + action + " for user " + userHandle);
                mHandler.post(new Runnable() {
                    public void run() {
                        handlePasswordExpirationNotification(userHandle);
                    }
                });
            }
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || KeyChain.ACTION_STORAGE_CHANGED.equals(action)) {
                manageMonitoringCertificateNotification(intent);
            }
            if (Intent.ACTION_USER_REMOVED.equals(action)) {
                removeUserData(userHandle);
            } else if (Intent.ACTION_USER_STARTED.equals(action)
                    || Intent.ACTION_PACKAGE_CHANGED.equals(action)
                    || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                    || Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {

                if (Intent.ACTION_USER_STARTED.equals(action)) {
                    // Reset the policy data
                    synchronized (DevicePolicyManagerService.this) {
                        mUserData.remove(userHandle);
                    }
                }

                handlePackagesChanged(userHandle);
            }
        }
    };

    private IAppOpsService mAppOpsService;

    static class ActiveAdmin {
        private static final String TAG_DISABLE_KEYGUARD_FEATURES = "disable-keyguard-features";
        private static final String TAG_DISABLE_CAMERA = "disable-camera";
        private static final String TAG_DISABLE_ACCOUNT_MANAGEMENT = "disable-account-management";
        private static final String TAG_ACCOUNT_TYPE = "account-type";
        private static final String TAG_ENCRYPTION_REQUESTED = "encryption-requested";
        private static final String TAG_PASSWORD_EXPIRATION_DATE = "password-expiration-date";
        private static final String TAG_PASSWORD_EXPIRATION_TIMEOUT = "password-expiration-timeout";
        private static final String TAG_GLOBAL_PROXY_EXCLUSION_LIST = "global-proxy-exclusion-list";
        private static final String TAG_GLOBAL_PROXY_SPEC = "global-proxy-spec";
        private static final String TAG_SPECIFIES_GLOBAL_PROXY = "specifies-global-proxy";
        private static final String TAG_MAX_FAILED_PASSWORD_WIPE = "max-failed-password-wipe";
        private static final String TAG_MAX_TIME_TO_UNLOCK = "max-time-to-unlock";
        private static final String TAG_MIN_PASSWORD_NONLETTER = "min-password-nonletter";
        private static final String TAG_MIN_PASSWORD_SYMBOLS = "min-password-symbols";
        private static final String TAG_MIN_PASSWORD_NUMERIC = "min-password-numeric";
        private static final String TAG_MIN_PASSWORD_LETTERS = "min-password-letters";
        private static final String TAG_MIN_PASSWORD_LOWERCASE = "min-password-lowercase";
        private static final String TAG_MIN_PASSWORD_UPPERCASE = "min-password-uppercase";
        private static final String TAG_PASSWORD_HISTORY_LENGTH = "password-history-length";
        private static final String TAG_MIN_PASSWORD_LENGTH = "min-password-length";
        private static final String ATTR_VALUE = "value";
        private static final String TAG_PASSWORD_QUALITY = "password-quality";
        private static final String TAG_POLICIES = "policies";

        final DeviceAdminInfo info;

        int passwordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;

        static final int DEF_MINIMUM_PASSWORD_LENGTH = 0;
        int minimumPasswordLength = DEF_MINIMUM_PASSWORD_LENGTH;

        static final int DEF_PASSWORD_HISTORY_LENGTH = 0;
        int passwordHistoryLength = DEF_PASSWORD_HISTORY_LENGTH;

        static final int DEF_MINIMUM_PASSWORD_UPPER_CASE = 0;
        int minimumPasswordUpperCase = DEF_MINIMUM_PASSWORD_UPPER_CASE;

        static final int DEF_MINIMUM_PASSWORD_LOWER_CASE = 0;
        int minimumPasswordLowerCase = DEF_MINIMUM_PASSWORD_LOWER_CASE;

        static final int DEF_MINIMUM_PASSWORD_LETTERS = 1;
        int minimumPasswordLetters = DEF_MINIMUM_PASSWORD_LETTERS;

        static final int DEF_MINIMUM_PASSWORD_NUMERIC = 1;
        int minimumPasswordNumeric = DEF_MINIMUM_PASSWORD_NUMERIC;

        static final int DEF_MINIMUM_PASSWORD_SYMBOLS = 1;
        int minimumPasswordSymbols = DEF_MINIMUM_PASSWORD_SYMBOLS;

        static final int DEF_MINIMUM_PASSWORD_NON_LETTER = 0;
        int minimumPasswordNonLetter = DEF_MINIMUM_PASSWORD_NON_LETTER;

        static final long DEF_MAXIMUM_TIME_TO_UNLOCK = 0;
        long maximumTimeToUnlock = DEF_MAXIMUM_TIME_TO_UNLOCK;

        static final int DEF_MAXIMUM_FAILED_PASSWORDS_FOR_WIPE = 0;
        int maximumFailedPasswordsForWipe = DEF_MAXIMUM_FAILED_PASSWORDS_FOR_WIPE;

        static final long DEF_PASSWORD_EXPIRATION_TIMEOUT = 0;
        long passwordExpirationTimeout = DEF_PASSWORD_EXPIRATION_TIMEOUT;

        static final long DEF_PASSWORD_EXPIRATION_DATE = 0;
        long passwordExpirationDate = DEF_PASSWORD_EXPIRATION_DATE;

        static final int DEF_KEYGUARD_FEATURES_DISABLED = 0; // none
        int disabledKeyguardFeatures = DEF_KEYGUARD_FEATURES_DISABLED;

        boolean encryptionRequested = false;
        boolean disableCamera = false;
        Set<String> accountTypesWithManagementDisabled = new HashSet<String>();

        // TODO: review implementation decisions with frameworks team
        boolean specifiesGlobalProxy = false;
        String globalProxySpec = null;
        String globalProxyExclusionList = null;

        ActiveAdmin(DeviceAdminInfo _info) {
            info = _info;
        }

        int getUid() { return info.getActivityInfo().applicationInfo.uid; }

        public UserHandle getUserHandle() {
            return new UserHandle(UserHandle.getUserId(info.getActivityInfo().applicationInfo.uid));
        }

        void writeToXml(XmlSerializer out)
                throws IllegalArgumentException, IllegalStateException, IOException {
            out.startTag(null, TAG_POLICIES);
            info.writePoliciesToXml(out);
            out.endTag(null, TAG_POLICIES);
            if (passwordQuality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                out.startTag(null, TAG_PASSWORD_QUALITY);
                out.attribute(null, ATTR_VALUE, Integer.toString(passwordQuality));
                out.endTag(null, TAG_PASSWORD_QUALITY);
                if (minimumPasswordLength != DEF_MINIMUM_PASSWORD_LENGTH) {
                    out.startTag(null, TAG_MIN_PASSWORD_LENGTH);
                    out.attribute(null, ATTR_VALUE, Integer.toString(minimumPasswordLength));
                    out.endTag(null, TAG_MIN_PASSWORD_LENGTH);
                }
                if(passwordHistoryLength != DEF_PASSWORD_HISTORY_LENGTH) {
                    out.startTag(null, TAG_PASSWORD_HISTORY_LENGTH);
                    out.attribute(null, ATTR_VALUE, Integer.toString(passwordHistoryLength));
                    out.endTag(null, TAG_PASSWORD_HISTORY_LENGTH);
                }
                if (minimumPasswordUpperCase != DEF_MINIMUM_PASSWORD_UPPER_CASE) {
                    out.startTag(null, TAG_MIN_PASSWORD_UPPERCASE);
                    out.attribute(null, ATTR_VALUE, Integer.toString(minimumPasswordUpperCase));
                    out.endTag(null, TAG_MIN_PASSWORD_UPPERCASE);
                }
                if (minimumPasswordLowerCase != DEF_MINIMUM_PASSWORD_LOWER_CASE) {
                    out.startTag(null, TAG_MIN_PASSWORD_LOWERCASE);
                    out.attribute(null, ATTR_VALUE, Integer.toString(minimumPasswordLowerCase));
                    out.endTag(null, TAG_MIN_PASSWORD_LOWERCASE);
                }
                if (minimumPasswordLetters != DEF_MINIMUM_PASSWORD_LETTERS) {
                    out.startTag(null, TAG_MIN_PASSWORD_LETTERS);
                    out.attribute(null, ATTR_VALUE, Integer.toString(minimumPasswordLetters));
                    out.endTag(null, TAG_MIN_PASSWORD_LETTERS);
                }
                if (minimumPasswordNumeric != DEF_MINIMUM_PASSWORD_NUMERIC) {
                    out.startTag(null, TAG_MIN_PASSWORD_NUMERIC);
                    out.attribute(null, ATTR_VALUE, Integer.toString(minimumPasswordNumeric));
                    out.endTag(null, TAG_MIN_PASSWORD_NUMERIC);
                }
                if (minimumPasswordSymbols != DEF_MINIMUM_PASSWORD_SYMBOLS) {
                    out.startTag(null, TAG_MIN_PASSWORD_SYMBOLS);
                    out.attribute(null, ATTR_VALUE, Integer.toString(minimumPasswordSymbols));
                    out.endTag(null, TAG_MIN_PASSWORD_SYMBOLS);
                }
                if (minimumPasswordNonLetter > DEF_MINIMUM_PASSWORD_NON_LETTER) {
                    out.startTag(null, TAG_MIN_PASSWORD_NONLETTER);
                    out.attribute(null, ATTR_VALUE, Integer.toString(minimumPasswordNonLetter));
                    out.endTag(null, TAG_MIN_PASSWORD_NONLETTER);
                }
            }
            if (maximumTimeToUnlock != DEF_MAXIMUM_TIME_TO_UNLOCK) {
                out.startTag(null, TAG_MAX_TIME_TO_UNLOCK);
                out.attribute(null, ATTR_VALUE, Long.toString(maximumTimeToUnlock));
                out.endTag(null, TAG_MAX_TIME_TO_UNLOCK);
            }
            if (maximumFailedPasswordsForWipe != DEF_MAXIMUM_FAILED_PASSWORDS_FOR_WIPE) {
                out.startTag(null, TAG_MAX_FAILED_PASSWORD_WIPE);
                out.attribute(null, ATTR_VALUE, Integer.toString(maximumFailedPasswordsForWipe));
                out.endTag(null, TAG_MAX_FAILED_PASSWORD_WIPE);
            }
            if (specifiesGlobalProxy) {
                out.startTag(null, TAG_SPECIFIES_GLOBAL_PROXY);
                out.attribute(null, ATTR_VALUE, Boolean.toString(specifiesGlobalProxy));
                out.endTag(null, TAG_SPECIFIES_GLOBAL_PROXY);
                if (globalProxySpec != null) {
                    out.startTag(null, TAG_GLOBAL_PROXY_SPEC);
                    out.attribute(null, ATTR_VALUE, globalProxySpec);
                    out.endTag(null, TAG_GLOBAL_PROXY_SPEC);
                }
                if (globalProxyExclusionList != null) {
                    out.startTag(null, TAG_GLOBAL_PROXY_EXCLUSION_LIST);
                    out.attribute(null, ATTR_VALUE, globalProxyExclusionList);
                    out.endTag(null, TAG_GLOBAL_PROXY_EXCLUSION_LIST);
                }
            }
            if (passwordExpirationTimeout != DEF_PASSWORD_EXPIRATION_TIMEOUT) {
                out.startTag(null, TAG_PASSWORD_EXPIRATION_TIMEOUT);
                out.attribute(null, ATTR_VALUE, Long.toString(passwordExpirationTimeout));
                out.endTag(null, TAG_PASSWORD_EXPIRATION_TIMEOUT);
            }
            if (passwordExpirationDate != DEF_PASSWORD_EXPIRATION_DATE) {
                out.startTag(null, TAG_PASSWORD_EXPIRATION_DATE);
                out.attribute(null, ATTR_VALUE, Long.toString(passwordExpirationDate));
                out.endTag(null, TAG_PASSWORD_EXPIRATION_DATE);
            }
            if (encryptionRequested) {
                out.startTag(null, TAG_ENCRYPTION_REQUESTED);
                out.attribute(null, ATTR_VALUE, Boolean.toString(encryptionRequested));
                out.endTag(null, TAG_ENCRYPTION_REQUESTED);
            }
            if (disableCamera) {
                out.startTag(null, TAG_DISABLE_CAMERA);
                out.attribute(null, ATTR_VALUE, Boolean.toString(disableCamera));
                out.endTag(null, TAG_DISABLE_CAMERA);
            }
            if (disabledKeyguardFeatures != DEF_KEYGUARD_FEATURES_DISABLED) {
                out.startTag(null, TAG_DISABLE_KEYGUARD_FEATURES);
                out.attribute(null, ATTR_VALUE, Integer.toString(disabledKeyguardFeatures));
                out.endTag(null, TAG_DISABLE_KEYGUARD_FEATURES);
            }
            if (!accountTypesWithManagementDisabled.isEmpty()) {
                out.startTag(null, TAG_DISABLE_ACCOUNT_MANAGEMENT);
                for (String ac : accountTypesWithManagementDisabled) {
                    out.startTag(null, TAG_ACCOUNT_TYPE);
                    out.attribute(null, ATTR_VALUE, ac);
                    out.endTag(null, TAG_ACCOUNT_TYPE);
                }
                out.endTag(null,  TAG_DISABLE_ACCOUNT_MANAGEMENT);
            }
        }

        void readFromXml(XmlPullParser parser)
                throws XmlPullParserException, IOException {
            int outerDepth = parser.getDepth();
            int type;
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                String tag = parser.getName();
                if (TAG_POLICIES.equals(tag)) {
                    info.readPoliciesFromXml(parser);
                } else if (TAG_PASSWORD_QUALITY.equals(tag)) {
                    passwordQuality = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MIN_PASSWORD_LENGTH.equals(tag)) {
                    minimumPasswordLength = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_PASSWORD_HISTORY_LENGTH.equals(tag)) {
                    passwordHistoryLength = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MIN_PASSWORD_UPPERCASE.equals(tag)) {
                    minimumPasswordUpperCase = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MIN_PASSWORD_LOWERCASE.equals(tag)) {
                    minimumPasswordLowerCase = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MIN_PASSWORD_LETTERS.equals(tag)) {
                    minimumPasswordLetters = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MIN_PASSWORD_NUMERIC.equals(tag)) {
                    minimumPasswordNumeric = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MIN_PASSWORD_SYMBOLS.equals(tag)) {
                    minimumPasswordSymbols = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MIN_PASSWORD_NONLETTER.equals(tag)) {
                    minimumPasswordNonLetter = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MAX_TIME_TO_UNLOCK.equals(tag)) {
                    maximumTimeToUnlock = Long.parseLong(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_MAX_FAILED_PASSWORD_WIPE.equals(tag)) {
                    maximumFailedPasswordsForWipe = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_SPECIFIES_GLOBAL_PROXY.equals(tag)) {
                    specifiesGlobalProxy = Boolean.parseBoolean(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_GLOBAL_PROXY_SPEC.equals(tag)) {
                    globalProxySpec =
                        parser.getAttributeValue(null, ATTR_VALUE);
                } else if (TAG_GLOBAL_PROXY_EXCLUSION_LIST.equals(tag)) {
                    globalProxyExclusionList =
                        parser.getAttributeValue(null, ATTR_VALUE);
                } else if (TAG_PASSWORD_EXPIRATION_TIMEOUT.equals(tag)) {
                    passwordExpirationTimeout = Long.parseLong(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_PASSWORD_EXPIRATION_DATE.equals(tag)) {
                    passwordExpirationDate = Long.parseLong(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_ENCRYPTION_REQUESTED.equals(tag)) {
                    encryptionRequested = Boolean.parseBoolean(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_DISABLE_CAMERA.equals(tag)) {
                    disableCamera = Boolean.parseBoolean(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_DISABLE_KEYGUARD_FEATURES.equals(tag)) {
                    disabledKeyguardFeatures = Integer.parseInt(
                            parser.getAttributeValue(null, ATTR_VALUE));
                } else if (TAG_DISABLE_ACCOUNT_MANAGEMENT.equals(tag)) {
                    int outerDepthDAM = parser.getDepth();
                    int typeDAM;
                    while ((typeDAM=parser.next()) != XmlPullParser.END_DOCUMENT
                            && (typeDAM != XmlPullParser.END_TAG
                                    || parser.getDepth() > outerDepthDAM)) {
                        if (typeDAM == XmlPullParser.END_TAG || typeDAM == XmlPullParser.TEXT) {
                            continue;
                        }
                        String tagDAM = parser.getName();
                        if (TAG_ACCOUNT_TYPE.equals(tagDAM)) {
                            accountTypesWithManagementDisabled.add(
                                    parser.getAttributeValue(null, ATTR_VALUE));
                        } else {
                            Slog.w(LOG_TAG, "Unknown tag under " + tag +  ": " + tagDAM);
                        }
                    }
                } else {
                    Slog.w(LOG_TAG, "Unknown admin tag: " + tag);
                }
                XmlUtils.skipCurrentTag(parser);
            }
        }

        void dump(String prefix, PrintWriter pw) {
            pw.print(prefix); pw.print("uid="); pw.println(getUid());
            pw.print(prefix); pw.println("policies:");
            ArrayList<DeviceAdminInfo.PolicyInfo> pols = info.getUsedPolicies();
            if (pols != null) {
                for (int i=0; i<pols.size(); i++) {
                    pw.print(prefix); pw.print("  "); pw.println(pols.get(i).tag);
                }
            }
            pw.print(prefix); pw.print("passwordQuality=0x");
                    pw.println(Integer.toHexString(passwordQuality));
            pw.print(prefix); pw.print("minimumPasswordLength=");
                    pw.println(minimumPasswordLength);
            pw.print(prefix); pw.print("passwordHistoryLength=");
                    pw.println(passwordHistoryLength);
            pw.print(prefix); pw.print("minimumPasswordUpperCase=");
                    pw.println(minimumPasswordUpperCase);
            pw.print(prefix); pw.print("minimumPasswordLowerCase=");
                    pw.println(minimumPasswordLowerCase);
            pw.print(prefix); pw.print("minimumPasswordLetters=");
                    pw.println(minimumPasswordLetters);
            pw.print(prefix); pw.print("minimumPasswordNumeric=");
                    pw.println(minimumPasswordNumeric);
            pw.print(prefix); pw.print("minimumPasswordSymbols=");
                    pw.println(minimumPasswordSymbols);
            pw.print(prefix); pw.print("minimumPasswordNonLetter=");
                    pw.println(minimumPasswordNonLetter);
            pw.print(prefix); pw.print("maximumTimeToUnlock=");
                    pw.println(maximumTimeToUnlock);
            pw.print(prefix); pw.print("maximumFailedPasswordsForWipe=");
                    pw.println(maximumFailedPasswordsForWipe);
            pw.print(prefix); pw.print("specifiesGlobalProxy=");
                    pw.println(specifiesGlobalProxy);
            pw.print(prefix); pw.print("passwordExpirationTimeout=");
                    pw.println(passwordExpirationTimeout);
            pw.print(prefix); pw.print("passwordExpirationDate=");
                    pw.println(passwordExpirationDate);
            if (globalProxySpec != null) {
                pw.print(prefix); pw.print("globalProxySpec=");
                        pw.println(globalProxySpec);
            }
            if (globalProxyExclusionList != null) {
                pw.print(prefix); pw.print("globalProxyEclusionList=");
                        pw.println(globalProxyExclusionList);
            }
            pw.print(prefix); pw.print("encryptionRequested=");
                    pw.println(encryptionRequested);
            pw.print(prefix); pw.print("disableCamera=");
                    pw.println(disableCamera);
            pw.print(prefix); pw.print("disabledKeyguardFeatures=");
                    pw.println(disabledKeyguardFeatures);
        }
    }

    private void handlePackagesChanged(int userHandle) {
        boolean removed = false;
        if (DBG) Slog.d(LOG_TAG, "Handling package changes for user " + userHandle);
        DevicePolicyData policy = getUserData(userHandle);
        IPackageManager pm = AppGlobals.getPackageManager();
        for (int i = policy.mAdminList.size() - 1; i >= 0; i--) {
            ActiveAdmin aa = policy.mAdminList.get(i);
            try {
                if (pm.getPackageInfo(aa.info.getPackageName(), 0, userHandle) == null
                        || pm.getReceiverInfo(aa.info.getComponent(), 0, userHandle) == null) {
                    removed = true;
                    policy.mAdminList.remove(i);
                    policy.mAdminMap.remove(aa.info.getComponent());
                }
            } catch (RemoteException re) {
                // Shouldn't happen
            }
        }
        if (removed) {
            validatePasswordOwnerLocked(policy);
            syncDeviceCapabilitiesLocked(policy);
            saveSettingsLocked(policy.mUserHandle);
        }
    }

    /**
     * Instantiates the service.
     */
    public DevicePolicyManagerService(Context context) {
        mContext = context;
        mUserManager = UserManager.get(mContext);
        mHasFeature = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_DEVICE_ADMIN);
        mWakeLock = ((PowerManager)context.getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DPM");
        if (!mHasFeature) {
            // Skip the rest of the initialization
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(ACTION_EXPIRED_PASSWORD_NOTIFICATION);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(Intent.ACTION_USER_STARTED);
        filter.addAction(KeyChain.ACTION_STORAGE_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        context.registerReceiverAsUser(mReceiver, UserHandle.ALL, filter, null, mHandler);
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        context.registerReceiverAsUser(mReceiver, UserHandle.ALL, filter, null, mHandler);
    }

    /**
     * Creates and loads the policy data from xml.
     * @param userHandle the user for whom to load the policy data
     * @return
     */
    DevicePolicyData getUserData(int userHandle) {
        synchronized (this) {
            DevicePolicyData policy = mUserData.get(userHandle);
            if (policy == null) {
                policy = new DevicePolicyData(userHandle);
                mUserData.append(userHandle, policy);
                loadSettingsLocked(policy, userHandle);
            }
            return policy;
        }
    }

    void removeUserData(int userHandle) {
        synchronized (this) {
            if (userHandle == UserHandle.USER_OWNER) {
                Slog.w(LOG_TAG, "Tried to remove device policy file for user 0! Ignoring.");
                return;
            }
            if (mDeviceOwner != null) {
                mDeviceOwner.removeProfileOwner(userHandle);
                mDeviceOwner.writeOwnerFile();
            }

            DevicePolicyData policy = mUserData.get(userHandle);
            if (policy != null) {
                mUserData.remove(userHandle);
            }
            File policyFile = new File(Environment.getUserSystemDirectory(userHandle),
                    DEVICE_POLICIES_XML);
            policyFile.delete();
            Slog.i(LOG_TAG, "Removed device policy file " + policyFile.getAbsolutePath());
        }
    }

    void loadDeviceOwner() {
        synchronized (this) {
            mDeviceOwner = DeviceOwner.load();
        }
    }

    /**
     * Set an alarm for an upcoming event - expiration warning, expiration, or post-expiration
     * reminders.  Clears alarm if no expirations are configured.
     */
    protected void setExpirationAlarmCheckLocked(Context context, DevicePolicyData policy) {
        final long expiration = getPasswordExpirationLocked(null, policy.mUserHandle);
        final long now = System.currentTimeMillis();
        final long timeToExpire = expiration - now;
        final long alarmTime;
        if (expiration == 0) {
            // No expirations are currently configured:  Cancel alarm.
            alarmTime = 0;
        } else if (timeToExpire <= 0) {
            // The password has already expired:  Repeat every 24 hours.
            alarmTime = now + MS_PER_DAY;
        } else {
            // Selecting the next alarm time:  Roll forward to the next 24 hour multiple before
            // the expiration time.
            long alarmInterval = timeToExpire % MS_PER_DAY;
            if (alarmInterval == 0) {
                alarmInterval = MS_PER_DAY;
            }
            alarmTime = now + alarmInterval;
        }

        long token = Binder.clearCallingIdentity();
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcastAsUser(context, REQUEST_EXPIRE_PASSWORD,
                    new Intent(ACTION_EXPIRED_PASSWORD_NOTIFICATION),
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT,
                    new UserHandle(policy.mUserHandle));
            am.cancel(pi);
            if (alarmTime != 0) {
                am.set(AlarmManager.RTC, alarmTime, pi);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private IPowerManager getIPowerManager() {
        if (mIPowerManager == null) {
            IBinder b = ServiceManager.getService(Context.POWER_SERVICE);
            mIPowerManager = IPowerManager.Stub.asInterface(b);
        }
        return mIPowerManager;
    }

    private IWindowManager getWindowManager() {
        if (mIWindowManager == null) {
            IBinder b = ServiceManager.getService(Context.WINDOW_SERVICE);
            mIWindowManager = IWindowManager.Stub.asInterface(b);
        }
        return mIWindowManager;
    }

    private NotificationManager getNotificationManager() {
        if (mNotificationManager == null) {
            mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mNotificationManager;
    }

    ActiveAdmin getActiveAdminUncheckedLocked(ComponentName who, int userHandle) {
        ActiveAdmin admin = getUserData(userHandle).mAdminMap.get(who);
        if (admin != null
                && who.getPackageName().equals(admin.info.getActivityInfo().packageName)
                && who.getClassName().equals(admin.info.getActivityInfo().name)) {
            return admin;
        }
        return null;
    }

    ActiveAdmin getActiveAdminForCallerLocked(ComponentName who, int reqPolicy)
            throws SecurityException {
        final int callingUid = Binder.getCallingUid();
        final int userHandle = UserHandle.getUserId(callingUid);
        final DevicePolicyData policy = getUserData(userHandle);

        List<ActiveAdmin> candidates = new ArrayList<ActiveAdmin>();

        // Build a list of admins for this uid matching the given ComponentName
        if (who != null) {
            ActiveAdmin admin = policy.mAdminMap.get(who);
            if (admin == null) {
                throw new SecurityException("No active admin " + who);
            }
            if (admin.getUid() != callingUid) {
                throw new SecurityException("Admin " + who + " is not owned by uid "
                        + Binder.getCallingUid());
            }
            candidates.add(admin);
        } else {
            for (ActiveAdmin admin : policy.mAdminList) {
                if (admin.getUid() == callingUid) {
                    candidates.add(admin);
                }
            }
        }

        // Try to find an admin which can use reqPolicy
        for (ActiveAdmin admin : candidates) {
            boolean ownsDevice = isDeviceOwner(admin.info.getPackageName());
            boolean ownsProfile = (getProfileOwner(userHandle) != null
                    && getProfileOwner(userHandle).equals(admin.info.getPackageName()));

            if (reqPolicy == DeviceAdminInfo.USES_POLICY_DEVICE_OWNER) {
                if (ownsDevice) {
                    return admin;
                }
            } else if (reqPolicy == DeviceAdminInfo.USES_POLICY_PROFILE_OWNER) {
                if (ownsDevice || ownsProfile) {
                    return admin;
                }
            } else {
                if (admin.info.usesPolicy(reqPolicy)) {
                    return admin;
                }
            }
        }

        if (who != null) {
            if (reqPolicy == DeviceAdminInfo.USES_POLICY_DEVICE_OWNER) {
                throw new SecurityException("Admin " + candidates.get(0).info.getComponent()
                         + " does not own the device");
            }
            if (reqPolicy == DeviceAdminInfo.USES_POLICY_PROFILE_OWNER) {
                throw new SecurityException("Admin " + candidates.get(0).info.getComponent()
                        + " does not own the profile");
            }
            throw new SecurityException("Admin " + candidates.get(0).info.getComponent()
                    + " did not specify uses-policy for: "
                    + candidates.get(0).info.getTagForPolicy(reqPolicy));
        } else {
            throw new SecurityException("No active admin owned by uid "
                    + Binder.getCallingUid() + " for policy #" + reqPolicy);
        }
    }

    void sendAdminCommandLocked(ActiveAdmin admin, String action) {
        sendAdminCommandLocked(admin, action, null);
    }

    void sendAdminCommandLocked(ActiveAdmin admin, String action, BroadcastReceiver result) {
        sendAdminCommandLocked(admin, action, null, result);
    }

    /**
     * Send an update to one specific admin, get notified when that admin returns a result.
     */
    void sendAdminCommandLocked(ActiveAdmin admin, String action, Bundle adminExtras,
            BroadcastReceiver result) {
        Intent intent = new Intent(action);
        intent.setComponent(admin.info.getComponent());
        if (action.equals(DeviceAdminReceiver.ACTION_PASSWORD_EXPIRING)) {
            intent.putExtra("expiration", admin.passwordExpirationDate);
        }
        if (adminExtras != null) {
            intent.putExtras(adminExtras);
        }
        if (result != null) {
            mContext.sendOrderedBroadcastAsUser(intent, admin.getUserHandle(),
                    null, result, mHandler, Activity.RESULT_OK, null, null);
        } else {
            mContext.sendBroadcastAsUser(intent, admin.getUserHandle());
        }
    }

    /**
     * Send an update to all admins of a user that enforce a specified policy.
     */
    void sendAdminCommandLocked(String action, int reqPolicy, int userHandle) {
        final DevicePolicyData policy = getUserData(userHandle);
        final int count = policy.mAdminList.size();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                final ActiveAdmin admin = policy.mAdminList.get(i);
                if (admin.info.usesPolicy(reqPolicy)) {
                    sendAdminCommandLocked(admin, action);
                }
            }
        }
    }

    /**
     * Send an update intent to all admins of a user and its profiles. Only send to admins that
     * enforce a specified policy.
     */
    private void sendAdminCommandToSelfAndProfilesLocked(String action, int reqPolicy,
            int userHandle) {
        List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
        for (UserInfo ui : profiles) {
            int id = ui.getUserHandle().getIdentifier();
            sendAdminCommandLocked(action, reqPolicy, id);
        }
    }

    void removeActiveAdminLocked(final ComponentName adminReceiver, int userHandle) {
        final ActiveAdmin admin = getActiveAdminUncheckedLocked(adminReceiver, userHandle);
        if (admin != null) {
            sendAdminCommandLocked(admin,
                    DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLED,
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            synchronized (DevicePolicyManagerService.this) {
                                int userHandle = admin.getUserHandle().getIdentifier();
                                DevicePolicyData policy = getUserData(userHandle);
                                boolean doProxyCleanup = admin.info.usesPolicy(
                                        DeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY);
                                policy.mAdminList.remove(admin);
                                policy.mAdminMap.remove(adminReceiver);
                                validatePasswordOwnerLocked(policy);
                                syncDeviceCapabilitiesLocked(policy);
                                if (doProxyCleanup) {
                                    resetGlobalProxyLocked(getUserData(userHandle));
                                }
                                saveSettingsLocked(userHandle);
                                updateMaximumTimeToLockLocked(policy);
                            }
                        }
            });
        }
    }

    public DeviceAdminInfo findAdmin(ComponentName adminName, int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        enforceCrossUserPermission(userHandle);
        Intent resolveIntent = new Intent();
        resolveIntent.setComponent(adminName);
        List<ResolveInfo> infos = mContext.getPackageManager().queryBroadcastReceivers(
                resolveIntent,
                PackageManager.GET_META_DATA | PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS,
                userHandle);
        if (infos == null || infos.size() <= 0) {
            throw new IllegalArgumentException("Unknown admin: " + adminName);
        }

        try {
            return new DeviceAdminInfo(mContext, infos.get(0));
        } catch (XmlPullParserException e) {
            Slog.w(LOG_TAG, "Bad device admin requested for user=" + userHandle + ": " + adminName,
                    e);
            return null;
        } catch (IOException e) {
            Slog.w(LOG_TAG, "Bad device admin requested for user=" + userHandle + ": " + adminName,
                    e);
            return null;
        }
    }

    private static JournaledFile makeJournaledFile(int userHandle) {
        final String base = userHandle == 0
                ? "/data/system/" + DEVICE_POLICIES_XML
                : new File(Environment.getUserSystemDirectory(userHandle), DEVICE_POLICIES_XML)
                        .getAbsolutePath();
        return new JournaledFile(new File(base), new File(base + ".tmp"));
    }

    private void saveSettingsLocked(int userHandle) {
        DevicePolicyData policy = getUserData(userHandle);
        JournaledFile journal = makeJournaledFile(userHandle);
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(journal.chooseForWrite(), false);
            XmlSerializer out = new FastXmlSerializer();
            out.setOutput(stream, "utf-8");
            out.startDocument(null, true);

            out.startTag(null, "policies");
            if (policy.mRestrictionsProvider != null) {
                out.attribute(null, ATTR_PERMISSION_PROVIDER,
                        policy.mRestrictionsProvider.flattenToString());
            }

            final int N = policy.mAdminList.size();
            for (int i=0; i<N; i++) {
                ActiveAdmin ap = policy.mAdminList.get(i);
                if (ap != null) {
                    out.startTag(null, "admin");
                    out.attribute(null, "name", ap.info.getComponent().flattenToString());
                    ap.writeToXml(out);
                    out.endTag(null, "admin");
                }
            }

            if (policy.mPasswordOwner >= 0) {
                out.startTag(null, "password-owner");
                out.attribute(null, "value", Integer.toString(policy.mPasswordOwner));
                out.endTag(null, "password-owner");
            }

            if (policy.mFailedPasswordAttempts != 0) {
                out.startTag(null, "failed-password-attempts");
                out.attribute(null, "value", Integer.toString(policy.mFailedPasswordAttempts));
                out.endTag(null, "failed-password-attempts");
            }

            if (policy.mActivePasswordQuality != 0 || policy.mActivePasswordLength != 0
                    || policy.mActivePasswordUpperCase != 0 || policy.mActivePasswordLowerCase != 0
                    || policy.mActivePasswordLetters != 0 || policy.mActivePasswordNumeric != 0
                    || policy.mActivePasswordSymbols != 0 || policy.mActivePasswordNonLetter != 0) {
                out.startTag(null, "active-password");
                out.attribute(null, "quality", Integer.toString(policy.mActivePasswordQuality));
                out.attribute(null, "length", Integer.toString(policy.mActivePasswordLength));
                out.attribute(null, "uppercase", Integer.toString(policy.mActivePasswordUpperCase));
                out.attribute(null, "lowercase", Integer.toString(policy.mActivePasswordLowerCase));
                out.attribute(null, "letters", Integer.toString(policy.mActivePasswordLetters));
                out.attribute(null, "numeric", Integer
                        .toString(policy.mActivePasswordNumeric));
                out.attribute(null, "symbols", Integer.toString(policy.mActivePasswordSymbols));
                out.attribute(null, "nonletter", Integer.toString(policy.mActivePasswordNonLetter));
                out.endTag(null, "active-password");
            }

            for (int i=0; i<policy.mLockTaskPackages.size(); i++) {
                String component = policy.mLockTaskPackages.get(i);
                out.startTag(null, LOCK_TASK_COMPONENTS_XML);
                out.attribute(null, "name", component);
                out.endTag(null, LOCK_TASK_COMPONENTS_XML);
            }

            out.endTag(null, "policies");

            out.endDocument();
            stream.close();
            journal.commit();
            sendChangedNotification(userHandle);
        } catch (IOException e) {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException ex) {
                // Ignore
            }
            journal.rollback();
        }
    }

    private void sendChangedNotification(int userHandle) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        long ident = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(intent, new UserHandle(userHandle));
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void loadSettingsLocked(DevicePolicyData policy, int userHandle) {
        JournaledFile journal = makeJournaledFile(userHandle);
        FileInputStream stream = null;
        File file = journal.chooseForRead();
        try {
            stream = new FileInputStream(file);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);

            int type;
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
            }
            String tag = parser.getName();
            if (!"policies".equals(tag)) {
                throw new XmlPullParserException(
                        "Settings do not start with policies tag: found " + tag);
            }

            // Extract the permission provider component name if available
            String permissionProvider = parser.getAttributeValue(null, ATTR_PERMISSION_PROVIDER);
            if (permissionProvider != null) {
                policy.mRestrictionsProvider = ComponentName.unflattenFromString(permissionProvider);
            }

            type = parser.next();
            int outerDepth = parser.getDepth();
            policy.mLockTaskPackages.clear();
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                tag = parser.getName();
                if ("admin".equals(tag)) {
                    String name = parser.getAttributeValue(null, "name");
                    try {
                        DeviceAdminInfo dai = findAdmin(
                                ComponentName.unflattenFromString(name), userHandle);
                        if (DBG && (UserHandle.getUserId(dai.getActivityInfo().applicationInfo.uid)
                                != userHandle)) {
                            Slog.w(LOG_TAG, "findAdmin returned an incorrect uid "
                                    + dai.getActivityInfo().applicationInfo.uid + " for user "
                                    + userHandle);
                        }
                        if (dai != null) {
                            ActiveAdmin ap = new ActiveAdmin(dai);
                            ap.readFromXml(parser);
                            policy.mAdminMap.put(ap.info.getComponent(), ap);
                            policy.mAdminList.add(ap);
                        }
                    } catch (RuntimeException e) {
                        Slog.w(LOG_TAG, "Failed loading admin " + name, e);
                    }
                } else if ("failed-password-attempts".equals(tag)) {
                    policy.mFailedPasswordAttempts = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                    XmlUtils.skipCurrentTag(parser);
                } else if ("password-owner".equals(tag)) {
                    policy.mPasswordOwner = Integer.parseInt(
                            parser.getAttributeValue(null, "value"));
                    XmlUtils.skipCurrentTag(parser);
                } else if ("active-password".equals(tag)) {
                    policy.mActivePasswordQuality = Integer.parseInt(
                            parser.getAttributeValue(null, "quality"));
                    policy.mActivePasswordLength = Integer.parseInt(
                            parser.getAttributeValue(null, "length"));
                    policy.mActivePasswordUpperCase = Integer.parseInt(
                            parser.getAttributeValue(null, "uppercase"));
                    policy.mActivePasswordLowerCase = Integer.parseInt(
                            parser.getAttributeValue(null, "lowercase"));
                    policy.mActivePasswordLetters = Integer.parseInt(
                            parser.getAttributeValue(null, "letters"));
                    policy.mActivePasswordNumeric = Integer.parseInt(
                            parser.getAttributeValue(null, "numeric"));
                    policy.mActivePasswordSymbols = Integer.parseInt(
                            parser.getAttributeValue(null, "symbols"));
                    policy.mActivePasswordNonLetter = Integer.parseInt(
                            parser.getAttributeValue(null, "nonletter"));
                    XmlUtils.skipCurrentTag(parser);
                } else if (LOCK_TASK_COMPONENTS_XML.equals(tag)) {
                    policy.mLockTaskPackages.add(parser.getAttributeValue(null, "name"));
                    XmlUtils.skipCurrentTag(parser);
                } else {
                    Slog.w(LOG_TAG, "Unknown tag: " + tag);
                    XmlUtils.skipCurrentTag(parser);
                }
            }
        } catch (NullPointerException e) {
            Slog.w(LOG_TAG, "failed parsing " + file + " " + e);
        } catch (NumberFormatException e) {
            Slog.w(LOG_TAG, "failed parsing " + file + " " + e);
        } catch (XmlPullParserException e) {
            Slog.w(LOG_TAG, "failed parsing " + file + " " + e);
        } catch (FileNotFoundException e) {
            // Don't be noisy, this is normal if we haven't defined any policies.
        } catch (IOException e) {
            Slog.w(LOG_TAG, "failed parsing " + file + " " + e);
        } catch (IndexOutOfBoundsException e) {
            Slog.w(LOG_TAG, "failed parsing " + file + " " + e);
        }
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        // Validate that what we stored for the password quality matches
        // sufficiently what is currently set.  Note that this is only
        // a sanity check in case the two get out of sync; this should
        // never normally happen.
        LockPatternUtils utils = new LockPatternUtils(mContext);
        if (utils.getActivePasswordQuality() < policy.mActivePasswordQuality) {
            Slog.w(LOG_TAG, "Active password quality 0x"
                    + Integer.toHexString(policy.mActivePasswordQuality)
                    + " does not match actual quality 0x"
                    + Integer.toHexString(utils.getActivePasswordQuality()));
            policy.mActivePasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
            policy.mActivePasswordLength = 0;
            policy.mActivePasswordUpperCase = 0;
            policy.mActivePasswordLowerCase = 0;
            policy.mActivePasswordLetters = 0;
            policy.mActivePasswordNumeric = 0;
            policy.mActivePasswordSymbols = 0;
            policy.mActivePasswordNonLetter = 0;
        }

        validatePasswordOwnerLocked(policy);
        syncDeviceCapabilitiesLocked(policy);
        updateMaximumTimeToLockLocked(policy);
    }

    static void validateQualityConstant(int quality) {
        switch (quality) {
            case DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED:
            case DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK:
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                return;
        }
        throw new IllegalArgumentException("Invalid quality constant: 0x"
                + Integer.toHexString(quality));
    }

    void validatePasswordOwnerLocked(DevicePolicyData policy) {
        if (policy.mPasswordOwner >= 0) {
            boolean haveOwner = false;
            for (int i = policy.mAdminList.size() - 1; i >= 0; i--) {
                if (policy.mAdminList.get(i).getUid() == policy.mPasswordOwner) {
                    haveOwner = true;
                    break;
                }
            }
            if (!haveOwner) {
                Slog.w(LOG_TAG, "Previous password owner " + policy.mPasswordOwner
                        + " no longer active; disabling");
                policy.mPasswordOwner = -1;
            }
        }
    }

    /**
     * Pushes down policy information to the system for any policies related to general device
     * capabilities that need to be enforced by lower level services (e.g. Camera services).
     */
    void syncDeviceCapabilitiesLocked(DevicePolicyData policy) {
        // Ensure the status of the camera is synced down to the system. Interested native services
        // should monitor this value and act accordingly.
        boolean systemState = SystemProperties.getBoolean(SYSTEM_PROP_DISABLE_CAMERA, false);
        boolean cameraDisabled = getCameraDisabled(null, policy.mUserHandle);
        if (cameraDisabled != systemState) {
            long token = Binder.clearCallingIdentity();
            try {
                String value = cameraDisabled ? "1" : "0";
                if (DBG) Slog.v(LOG_TAG, "Change in camera state ["
                        + SYSTEM_PROP_DISABLE_CAMERA + "] = " + value);
                SystemProperties.set(SYSTEM_PROP_DISABLE_CAMERA, value);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    public void systemReady() {
        if (!mHasFeature) {
            return;
        }
        synchronized (this) {
            loadSettingsLocked(getUserData(UserHandle.USER_OWNER), UserHandle.USER_OWNER);
            loadDeviceOwner();
        }
        cleanUpOldUsers();
        mAppOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));
        if (mDeviceOwner != null) {
            if (mDeviceOwner.hasDeviceOwner()) {
                try {
                    mAppOpsService.setDeviceOwner(mDeviceOwner.getDeviceOwnerPackageName());
                } catch (RemoteException e) {
                    Log.w(LOG_TAG, "Unable to notify AppOpsService of DeviceOwner", e);
                }
            }
            for (Integer i : mDeviceOwner.getProfileOwnerKeys()) {
                try {
                    mAppOpsService.setProfileOwner(mDeviceOwner.getProfileOwnerPackageName(i), i);
                } catch (RemoteException e) {
                    Log.w(LOG_TAG, "Unable to notify AppOpsService of ProfileOwner", e);
                }
            }
        }
    }

    private void cleanUpOldUsers() {
        // This is needed in case the broadcast {@link Intent.ACTION_USER_REMOVED} was not handled
        // before reboot
        Set<Integer> usersWithProfileOwners;
        Set<Integer> usersWithData;
        synchronized(this) {
            usersWithProfileOwners = mDeviceOwner != null
                    ? mDeviceOwner.getProfileOwnerKeys() : new HashSet<Integer>();
            usersWithData = new HashSet<Integer>();
            for (int i = 0; i < mUserData.size(); i++) {
                usersWithData.add(mUserData.keyAt(i));
            }
        }
        List<UserInfo> allUsers = mUserManager.getUsers();

        Set<Integer> deletedUsers = new HashSet<Integer>();
        deletedUsers.addAll(usersWithProfileOwners);
        deletedUsers.addAll(usersWithData);
        for (UserInfo userInfo : allUsers) {
            deletedUsers.remove(userInfo.id);
        }
        for (Integer userId : deletedUsers) {
            removeUserData(userId);
        }
    }

    private void handlePasswordExpirationNotification(int userHandle) {
        synchronized (this) {
            final long now = System.currentTimeMillis();

            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo ui : profiles) {
                int profileUserHandle = ui.getUserHandle().getIdentifier();
                final DevicePolicyData policy = getUserData(profileUserHandle);
                final int count = policy.mAdminList.size();
                if (count > 0) {
                    for (int i = 0; i < count; i++) {
                        final ActiveAdmin admin = policy.mAdminList.get(i);
                        if (admin.info.usesPolicy(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD)
                                && admin.passwordExpirationTimeout > 0L
                                && now >= admin.passwordExpirationDate - EXPIRATION_GRACE_PERIOD_MS
                                && admin.passwordExpirationDate > 0L) {
                            sendAdminCommandLocked(admin,
                                    DeviceAdminReceiver.ACTION_PASSWORD_EXPIRING);
                        }
                    }
                }
            }
            setExpirationAlarmCheckLocked(mContext, getUserData(userHandle));
        }
    }

    private void manageMonitoringCertificateNotification(Intent intent) {
        final NotificationManager notificationManager = getNotificationManager();

        final boolean hasCert = DevicePolicyManager.hasAnyCaCertsInstalled();
        if (! hasCert) {
            if (intent.getAction().equals(KeyChain.ACTION_STORAGE_CHANGED)) {
                for (UserInfo user : mUserManager.getUsers()) {
                    notificationManager.cancelAsUser(
                            null, MONITORING_CERT_NOTIFICATION_ID, user.getUserHandle());
                }
            }
            return;
        }
        final boolean isManaged = getDeviceOwner() != null;
        int smallIconId;
        String contentText;
        if (isManaged) {
            contentText = mContext.getString(R.string.ssl_ca_cert_noti_managed,
                    getDeviceOwnerName());
            smallIconId = R.drawable.stat_sys_certificate_info;
        } else {
            contentText = mContext.getString(R.string.ssl_ca_cert_noti_by_unknown);
            smallIconId = android.R.drawable.stat_sys_warning;
        }

        Intent dialogIntent = new Intent(Settings.ACTION_MONITORING_CERT_INFO);
        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        dialogIntent.setPackage("com.android.settings");
        // Notification will be sent individually to all users. The activity should start as
        // whichever user is current when it starts.
        PendingIntent notifyIntent = PendingIntent.getActivityAsUser(mContext, 0, dialogIntent,
                PendingIntent.FLAG_UPDATE_CURRENT, null, UserHandle.CURRENT);

        Notification noti = new Notification.Builder(mContext)
            .setSmallIcon(smallIconId)
            .setContentTitle(mContext.getString(R.string.ssl_ca_cert_warning))
            .setContentText(contentText)
            .setContentIntent(notifyIntent)
            .setPriority(Notification.PRIORITY_HIGH)
            .setShowWhen(false)
            .build();

        // If this is a boot intent, this will fire for each user. But if this is a storage changed
        // intent, it will fire once, so we need to notify all users.
        if (intent.getAction().equals(KeyChain.ACTION_STORAGE_CHANGED)) {
            for (UserInfo user : mUserManager.getUsers()) {
                notificationManager.notifyAsUser(
                        null, MONITORING_CERT_NOTIFICATION_ID, noti, user.getUserHandle());
            }
        } else {
            notificationManager.notifyAsUser(
                    null, MONITORING_CERT_NOTIFICATION_ID, noti, UserHandle.CURRENT);
        }
    }

    /**
     * @param adminReceiver The admin to add
     * @param refreshing true = update an active admin, no error
     */
    public void setActiveAdmin(ComponentName adminReceiver, boolean refreshing, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        setActiveAdmin(adminReceiver, refreshing, userHandle, null);
    }

    private void setActiveAdmin(ComponentName adminReceiver, boolean refreshing, int userHandle,
            Bundle onEnableData) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_DEVICE_ADMINS, null);
        enforceCrossUserPermission(userHandle);

        DevicePolicyData policy = getUserData(userHandle);
        DeviceAdminInfo info = findAdmin(adminReceiver, userHandle);
        if (info == null) {
            throw new IllegalArgumentException("Bad admin: " + adminReceiver);
        }
        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                if (!refreshing
                        && getActiveAdminUncheckedLocked(adminReceiver, userHandle) != null) {
                    throw new IllegalArgumentException("Admin is already added");
                }
                ActiveAdmin newAdmin = new ActiveAdmin(info);
                policy.mAdminMap.put(adminReceiver, newAdmin);
                int replaceIndex = -1;
                if (refreshing) {
                    final int N = policy.mAdminList.size();
                    for (int i=0; i < N; i++) {
                        ActiveAdmin oldAdmin = policy.mAdminList.get(i);
                        if (oldAdmin.info.getComponent().equals(adminReceiver)) {
                            replaceIndex = i;
                            break;
                        }
                    }
                }
                if (replaceIndex == -1) {
                    policy.mAdminList.add(newAdmin);
                    enableIfNecessary(info.getPackageName(), userHandle);
                } else {
                    policy.mAdminList.set(replaceIndex, newAdmin);
                }
                saveSettingsLocked(userHandle);
                sendAdminCommandLocked(newAdmin, DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED,
                        onEnableData, null);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    public boolean isAdminActive(ComponentName adminReceiver, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            return getActiveAdminUncheckedLocked(adminReceiver, userHandle) != null;
        }
    }

    public boolean hasGrantedPolicy(ComponentName adminReceiver, int policyId, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            ActiveAdmin administrator = getActiveAdminUncheckedLocked(adminReceiver, userHandle);
            if (administrator == null) {
                throw new SecurityException("No active admin " + adminReceiver);
            }
            return administrator.info.usesPolicy(policyId);
        }
    }

    @SuppressWarnings("unchecked")
    public List<ComponentName> getActiveAdmins(int userHandle) {
        if (!mHasFeature) {
            return Collections.EMPTY_LIST;
        }

        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            if (N <= 0) {
                return null;
            }
            ArrayList<ComponentName> res = new ArrayList<ComponentName>(N);
            for (int i=0; i<N; i++) {
                res.add(policy.mAdminList.get(i).info.getComponent());
            }
            return res;
        }
    }

    public boolean packageHasActiveAdmins(String packageName, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            for (int i=0; i<N; i++) {
                if (policy.mAdminList.get(i).info.getPackageName().equals(packageName)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void removeActiveAdmin(ComponentName adminReceiver, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(adminReceiver, userHandle);
            if (admin == null) {
                return;
            }
            if (admin.getUid() != Binder.getCallingUid()) {
                // If trying to remove device owner, refuse when the caller is not the owner.
                if (isDeviceOwner(adminReceiver.getPackageName())) {
                    return;
                }
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.MANAGE_DEVICE_ADMINS, null);
            }
            long ident = Binder.clearCallingIdentity();
            try {
                removeActiveAdminLocked(adminReceiver, userHandle);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    public void setPasswordQuality(ComponentName who, int quality, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        validateQualityConstant(quality);
        enforceCrossUserPermission(userHandle);

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.passwordQuality != quality) {
                ap.passwordQuality = quality;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordQuality(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int mode = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.passwordQuality : mode;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (mode < admin.passwordQuality) {
                        mode = admin.passwordQuality;
                    }
                }
            }
            return mode;
        }
    }

    public void setPasswordMinimumLength(ComponentName who, int length, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordLength != length) {
                ap.minimumPasswordLength = length;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordMinimumLength(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.minimumPasswordLength : length;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (length < admin.minimumPasswordLength) {
                        length = admin.minimumPasswordLength;
                    }
                }
            }
            return length;
        }
    }

    public void setPasswordHistoryLength(ComponentName who, int length, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.passwordHistoryLength != length) {
                ap.passwordHistoryLength = length;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordHistoryLength(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.passwordHistoryLength : length;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (length < admin.passwordHistoryLength) {
                        length = admin.passwordHistoryLength;
                    }
                }
            }
            return length;
        }
    }

    public void setPasswordExpirationTimeout(ComponentName who, long timeout, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            if (timeout < 0) {
                throw new IllegalArgumentException("Timeout must be >= 0 ms");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD);
            // Calling this API automatically bumps the expiration date
            final long expiration = timeout > 0L ? (timeout + System.currentTimeMillis()) : 0L;
            ap.passwordExpirationDate = expiration;
            ap.passwordExpirationTimeout = timeout;
            if (timeout > 0L) {
                Slog.w(LOG_TAG, "setPasswordExpiration(): password will expire on "
                        + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT)
                        .format(new Date(expiration)));
            }
            saveSettingsLocked(userHandle);
            // in case this is the first one
            setExpirationAlarmCheckLocked(mContext, getUserData(userHandle));
        }
    }

    /**
     * Return a single admin's expiration cycle time, or the min of all cycle times.
     * Returns 0 if not configured.
     */
    public long getPasswordExpirationTimeout(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0L;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            long timeout = 0L;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.passwordExpirationTimeout : timeout;
            }

            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (timeout == 0L || (admin.passwordExpirationTimeout != 0L
                            && timeout > admin.passwordExpirationTimeout)) {
                        timeout = admin.passwordExpirationTimeout;
                    }
                }
            }
            return timeout;
        }
    }

    /**
     * Return a single admin's expiration date/time, or the min (soonest) for all admins.
     * Returns 0 if not configured.
     */
    private long getPasswordExpirationLocked(ComponentName who, int userHandle) {
        long timeout = 0L;

        if (who != null) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
            return admin != null ? admin.passwordExpirationDate : timeout;
        }

        List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
        for (UserInfo userInfo : profiles) {
            DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
            final int N = policy.mAdminList.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = policy.mAdminList.get(i);
                if (timeout == 0L || (admin.passwordExpirationDate != 0
                        && timeout > admin.passwordExpirationDate)) {
                    timeout = admin.passwordExpirationDate;
                }
            }
        }
        return timeout;
    }

    public long getPasswordExpiration(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0L;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            return getPasswordExpirationLocked(who, userHandle);
        }
    }

    public void setPasswordMinimumUpperCase(ComponentName who, int length, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordUpperCase != length) {
                ap.minimumPasswordUpperCase = length;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordMinimumUpperCase(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.minimumPasswordUpperCase : length;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (length < admin.minimumPasswordUpperCase) {
                        length = admin.minimumPasswordUpperCase;
                    }
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumLowerCase(ComponentName who, int length, int userHandle) {
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordLowerCase != length) {
                ap.minimumPasswordLowerCase = length;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordMinimumLowerCase(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.minimumPasswordLowerCase : length;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (length < admin.minimumPasswordLowerCase) {
                        length = admin.minimumPasswordLowerCase;
                    }
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumLetters(ComponentName who, int length, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordLetters != length) {
                ap.minimumPasswordLetters = length;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordMinimumLetters(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.minimumPasswordLetters : length;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (length < admin.minimumPasswordLetters) {
                        length = admin.minimumPasswordLetters;
                    }
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumNumeric(ComponentName who, int length, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordNumeric != length) {
                ap.minimumPasswordNumeric = length;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordMinimumNumeric(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.minimumPasswordNumeric : length;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (length < admin.minimumPasswordNumeric) {
                        length = admin.minimumPasswordNumeric;
                    }
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumSymbols(ComponentName who, int length, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordSymbols != length) {
                ap.minimumPasswordSymbols = length;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordMinimumSymbols(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.minimumPasswordSymbols : length;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (length < admin.minimumPasswordSymbols) {
                        length = admin.minimumPasswordSymbols;
                    }
                }
            }
            return length;
        }
    }

    public void setPasswordMinimumNonLetter(ComponentName who, int length, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (ap.minimumPasswordNonLetter != length) {
                ap.minimumPasswordNonLetter = length;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getPasswordMinimumNonLetter(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int length = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.minimumPasswordNonLetter : length;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (length < admin.minimumPasswordNonLetter) {
                        length = admin.minimumPasswordNonLetter;
                    }
                }
            }
            return length;
        }
    }

    public boolean isActivePasswordSufficient(int userHandle) {
        if (!mHasFeature) {
            return true;
        }
        enforceCrossUserPermission(userHandle);

        synchronized (this) {

            // The active password is stored in the user that runs the launcher
            // If the user this is called from is part of a profile group, that is the parent
            // of the group.
            UserInfo parent = getProfileParent(userHandle);
            int id = parent == null ? userHandle : parent.id;
            DevicePolicyData policy = getUserData(id);

            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD);
            if (policy.mActivePasswordQuality < getPasswordQuality(null, userHandle)
                    || policy.mActivePasswordLength < getPasswordMinimumLength(null, userHandle)) {
                return false;
            }
            if (policy.mActivePasswordQuality != DevicePolicyManager.PASSWORD_QUALITY_COMPLEX) {
                return true;
            }
            return policy.mActivePasswordUpperCase >= getPasswordMinimumUpperCase(null, userHandle)
                && policy.mActivePasswordLowerCase >= getPasswordMinimumLowerCase(null, userHandle)
                && policy.mActivePasswordLetters >= getPasswordMinimumLetters(null, userHandle)
                && policy.mActivePasswordNumeric >= getPasswordMinimumNumeric(null, userHandle)
                && policy.mActivePasswordSymbols >= getPasswordMinimumSymbols(null, userHandle)
                && policy.mActivePasswordNonLetter >= getPasswordMinimumNonLetter(null, userHandle);
        }
    }

    public int getCurrentFailedPasswordAttempts(int userHandle) {
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_WATCH_LOGIN);

            // The active password is stored in the parent.
            DevicePolicyData policy = getUserData(getProfileParent(userHandle).id);

            return policy.mFailedPasswordAttempts;
        }
    }

    public void setMaximumFailedPasswordsForWipe(ComponentName who, int num, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_WIPE_DATA);
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_WATCH_LOGIN);
            if (ap.maximumFailedPasswordsForWipe != num) {
                ap.maximumFailedPasswordsForWipe = num;
                saveSettingsLocked(userHandle);
            }
        }
    }

    public int getMaximumFailedPasswordsForWipe(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            int count = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.maximumFailedPasswordsForWipe : count;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (count == 0) {
                        count = admin.maximumFailedPasswordsForWipe;
                    } else if (admin.maximumFailedPasswordsForWipe != 0
                            && count > admin.maximumFailedPasswordsForWipe) {
                        count = admin.maximumFailedPasswordsForWipe;
                    }
                }
            }
            return count;
        }
    }

    public boolean resetPassword(String password, int flags, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        enforceCrossUserPermission(userHandle);
        enforceNotManagedProfile(userHandle, "reset the password");

        int quality;
        synchronized (this) {
            // This api can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_RESET_PASSWORD);
            quality = getPasswordQuality(null, userHandle);
            if (quality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                int realQuality = LockPatternUtils.computePasswordQuality(password);
                if (realQuality < quality
                        && quality != DevicePolicyManager.PASSWORD_QUALITY_COMPLEX) {
                    Slog.w(LOG_TAG, "resetPassword: password quality 0x"
                            + Integer.toHexString(realQuality)
                            + " does not meet required quality 0x"
                            + Integer.toHexString(quality));
                    return false;
                }
                quality = Math.max(realQuality, quality);
            }
            int length = getPasswordMinimumLength(null, userHandle);
            if (password.length() < length) {
                Slog.w(LOG_TAG, "resetPassword: password length " + password.length()
                        + " does not meet required length " + length);
                return false;
            }
            if (quality == DevicePolicyManager.PASSWORD_QUALITY_COMPLEX) {
                int letters = 0;
                int uppercase = 0;
                int lowercase = 0;
                int numbers = 0;
                int symbols = 0;
                int nonletter = 0;
                for (int i = 0; i < password.length(); i++) {
                    char c = password.charAt(i);
                    if (c >= 'A' && c <= 'Z') {
                        letters++;
                        uppercase++;
                    } else if (c >= 'a' && c <= 'z') {
                        letters++;
                        lowercase++;
                    } else if (c >= '0' && c <= '9') {
                        numbers++;
                        nonletter++;
                    } else {
                        symbols++;
                        nonletter++;
                    }
                }
                int neededLetters = getPasswordMinimumLetters(null, userHandle);
                if(letters < neededLetters) {
                    Slog.w(LOG_TAG, "resetPassword: number of letters " + letters
                            + " does not meet required number of letters " + neededLetters);
                    return false;
                }
                int neededNumbers = getPasswordMinimumNumeric(null, userHandle);
                if (numbers < neededNumbers) {
                    Slog.w(LOG_TAG, "resetPassword: number of numerical digits " + numbers
                            + " does not meet required number of numerical digits "
                            + neededNumbers);
                    return false;
                }
                int neededLowerCase = getPasswordMinimumLowerCase(null, userHandle);
                if (lowercase < neededLowerCase) {
                    Slog.w(LOG_TAG, "resetPassword: number of lowercase letters " + lowercase
                            + " does not meet required number of lowercase letters "
                            + neededLowerCase);
                    return false;
                }
                int neededUpperCase = getPasswordMinimumUpperCase(null, userHandle);
                if (uppercase < neededUpperCase) {
                    Slog.w(LOG_TAG, "resetPassword: number of uppercase letters " + uppercase
                            + " does not meet required number of uppercase letters "
                            + neededUpperCase);
                    return false;
                }
                int neededSymbols = getPasswordMinimumSymbols(null, userHandle);
                if (symbols < neededSymbols) {
                    Slog.w(LOG_TAG, "resetPassword: number of special symbols " + symbols
                            + " does not meet required number of special symbols " + neededSymbols);
                    return false;
                }
                int neededNonLetter = getPasswordMinimumNonLetter(null, userHandle);
                if (nonletter < neededNonLetter) {
                    Slog.w(LOG_TAG, "resetPassword: number of non-letter characters " + nonletter
                            + " does not meet required number of non-letter characters "
                            + neededNonLetter);
                    return false;
                }
            }
        }

        int callingUid = Binder.getCallingUid();
        DevicePolicyData policy = getUserData(userHandle);
        if (policy.mPasswordOwner >= 0 && policy.mPasswordOwner != callingUid) {
            Slog.w(LOG_TAG, "resetPassword: already set by another uid and not entered by user");
            return false;
        }

        // Don't do this with the lock held, because it is going to call
        // back in to the service.
        long ident = Binder.clearCallingIdentity();
        try {
            LockPatternUtils utils = new LockPatternUtils(mContext);
            utils.saveLockPassword(password, quality, false, userHandle);
            synchronized (this) {
                int newOwner = (flags&DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)
                        != 0 ? callingUid : -1;
                if (policy.mPasswordOwner != newOwner) {
                    policy.mPasswordOwner = newOwner;
                    saveSettingsLocked(userHandle);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return true;
    }

    public void setMaximumTimeToLock(ComponentName who, long timeMs, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_FORCE_LOCK);
            if (ap.maximumTimeToUnlock != timeMs) {
                ap.maximumTimeToUnlock = timeMs;
                saveSettingsLocked(userHandle);
                updateMaximumTimeToLockLocked(getUserData(userHandle));
            }
        }
    }

    void updateMaximumTimeToLockLocked(DevicePolicyData policy) {
        long timeMs = getMaximumTimeToLock(null, policy.mUserHandle);
        if (policy.mLastMaximumTimeToLock == timeMs) {
            return;
        }

        long ident = Binder.clearCallingIdentity();
        try {
            if (timeMs <= 0) {
                timeMs = Integer.MAX_VALUE;
            } else {
                // Make sure KEEP_SCREEN_ON is disabled, since that
                // would allow bypassing of the maximum time to lock.
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
            }

            policy.mLastMaximumTimeToLock = timeMs;

            try {
                getIPowerManager().setMaximumScreenOffTimeoutFromDeviceAdmin((int)timeMs);
            } catch (RemoteException e) {
                Slog.w(LOG_TAG, "Failure talking with power manager", e);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public long getMaximumTimeToLock(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            long time = 0;

            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return admin != null ? admin.maximumTimeToUnlock : time;
            }

            // Return strictest policy for this user and profiles that are visible from this user.
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                DevicePolicyData policy = getUserData(userInfo.getUserHandle().getIdentifier());
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin admin = policy.mAdminList.get(i);
                    if (time == 0) {
                        time = admin.maximumTimeToUnlock;
                    } else if (admin.maximumTimeToUnlock != 0
                            && time > admin.maximumTimeToUnlock) {
                        time = admin.maximumTimeToUnlock;
                    }
                }
            }
            return time;
        }
    }

    public void lockNow() {
        if (!mHasFeature) {
            return;
        }
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_FORCE_LOCK);
            lockNowUnchecked();
        }
    }

    private void lockNowUnchecked() {
        long ident = Binder.clearCallingIdentity();
        try {
            // Power off the display
            getIPowerManager().goToSleep(SystemClock.uptimeMillis(),
                    PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN, 0);
            // Ensure the device is locked
            getWindowManager().lockNow(null);
        } catch (RemoteException e) {
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private boolean isExtStorageEncrypted() {
        String state = SystemProperties.get("vold.decrypt");
        return !"".equals(state);
    }

    public boolean installCaCert(byte[] certBuffer) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(MANAGE_CA_CERTIFICATES, null);
        KeyChainConnection keyChainConnection = null;
        byte[] pemCert;
        try {
            X509Certificate cert = parseCert(certBuffer);
            pemCert =  Credentials.convertToPem(cert);
        } catch (CertificateException ce) {
            Log.e(LOG_TAG, "Problem converting cert", ce);
            return false;
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Problem reading cert", ioe);
            return false;
        }
        try {
            keyChainConnection = KeyChain.bind(mContext);
            try {
                keyChainConnection.getService().installCaCertificate(pemCert);
                return true;
            } finally {
                if (keyChainConnection != null) {
                    keyChainConnection.close();
                    keyChainConnection = null;
                }
            }
        } catch (InterruptedException e1) {
            Log.w(LOG_TAG, "installCaCertsToKeyChain(): ", e1);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static X509Certificate parseCert(byte[] certBuffer)
            throws CertificateException, IOException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(
                certBuffer));
    }

    public void uninstallCaCert(final byte[] certBuffer) {
        mContext.enforceCallingOrSelfPermission(MANAGE_CA_CERTIFICATES, null);
        TrustedCertificateStore certStore = new TrustedCertificateStore();
        String alias = null;
        try {
            X509Certificate cert = parseCert(certBuffer);
            alias = certStore.getCertificateAlias(cert);
        } catch (CertificateException ce) {
            Log.e(LOG_TAG, "Problem creating X509Certificate", ce);
            return;
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Problem reading certificate", ioe);
            return;
        }
        try {
            KeyChainConnection keyChainConnection = KeyChain.bind(mContext);
            IKeyChainService service = keyChainConnection.getService();
            try {
                service.deleteCaCertificate(alias);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "from CaCertUninstaller: ", e);
            } finally {
                keyChainConnection.close();
                keyChainConnection = null;
            }
        } catch (InterruptedException ie) {
            Log.w(LOG_TAG, "CaCertUninstaller: ", ie);
            Thread.currentThread().interrupt();
        }
    }

    void wipeDataLocked(int flags) {
        // If the SD card is encrypted and non-removable, we have to force a wipe.
        boolean forceExtWipe = !Environment.isExternalStorageRemovable() && isExtStorageEncrypted();
        boolean wipeExtRequested = (flags&DevicePolicyManager.WIPE_EXTERNAL_STORAGE) != 0;

        // Note: we can only do the wipe via ExternalStorageFormatter if the volume is not emulated.
        if ((forceExtWipe || wipeExtRequested) && !Environment.isExternalStorageEmulated()) {
            Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
            intent.putExtra(ExternalStorageFormatter.EXTRA_ALWAYS_RESET, true);
            intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
            mWakeLock.acquire(10000);
            mContext.startService(intent);
        } else {
            try {
                RecoverySystem.rebootWipeUserData(mContext);
            } catch (IOException e) {
                Slog.w(LOG_TAG, "Failed requesting data wipe", e);
            }
        }
    }

    public void wipeData(int flags, final int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        if ((flags & DevicePolicyManager.WIPE_EXTERNAL_STORAGE) != 0) {
            enforceNotManagedProfile(userHandle, "wipe external storage");
        }
        synchronized (this) {
            // This API can only be called by an active device admin,
            // so try to retrieve it to check that the caller is one.
            getActiveAdminForCallerLocked(null,
                    DeviceAdminInfo.USES_POLICY_WIPE_DATA);
            long ident = Binder.clearCallingIdentity();
            try {
                wipeDeviceOrUserLocked(flags, userHandle);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    private void wipeDeviceOrUserLocked(int flags, final int userHandle) {
        if (userHandle == UserHandle.USER_OWNER) {
            wipeDataLocked(flags);
        } else {
            lockNowUnchecked();
            mHandler.post(new Runnable() {
                public void run() {
                    try {
                        ActivityManagerNative.getDefault().switchUser(UserHandle.USER_OWNER);
                        (mUserManager)
                                .removeUser(userHandle);
                    } catch (RemoteException re) {
                        // Shouldn't happen
                    }
                }
            });
        }
    }

    public void getRemoveWarning(ComponentName comp, final RemoteCallback result, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);

        synchronized (this) {
            ActiveAdmin admin = getActiveAdminUncheckedLocked(comp, userHandle);
            if (admin == null) {
                try {
                    result.sendResult(null);
                } catch (RemoteException e) {
                }
                return;
            }
            Intent intent = new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLE_REQUESTED);
            intent.setComponent(admin.info.getComponent());
            mContext.sendOrderedBroadcastAsUser(intent, new UserHandle(userHandle),
                    null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        result.sendResult(getResultExtras(false));
                    } catch (RemoteException e) {
                    }
                }
            }, null, Activity.RESULT_OK, null, null);
        }
    }

    public void setActivePasswordState(int quality, int length, int letters, int uppercase,
            int lowercase, int numbers, int symbols, int nonletter, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        enforceNotManagedProfile(userHandle, "set the active password");

        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);
        DevicePolicyData p = getUserData(userHandle);

        validateQualityConstant(quality);

        synchronized (this) {
            if (p.mActivePasswordQuality != quality || p.mActivePasswordLength != length
                    || p.mFailedPasswordAttempts != 0 || p.mActivePasswordLetters != letters
                    || p.mActivePasswordUpperCase != uppercase
                    || p.mActivePasswordLowerCase != lowercase
                    || p.mActivePasswordNumeric != numbers
                    || p.mActivePasswordSymbols != symbols
                    || p.mActivePasswordNonLetter != nonletter) {
                long ident = Binder.clearCallingIdentity();
                try {
                    p.mActivePasswordQuality = quality;
                    p.mActivePasswordLength = length;
                    p.mActivePasswordLetters = letters;
                    p.mActivePasswordLowerCase = lowercase;
                    p.mActivePasswordUpperCase = uppercase;
                    p.mActivePasswordNumeric = numbers;
                    p.mActivePasswordSymbols = symbols;
                    p.mActivePasswordNonLetter = nonletter;
                    p.mFailedPasswordAttempts = 0;
                    saveSettingsLocked(userHandle);
                    updatePasswordExpirationsLocked(userHandle);
                    setExpirationAlarmCheckLocked(mContext, p);
                    sendAdminCommandToSelfAndProfilesLocked(
                            DeviceAdminReceiver.ACTION_PASSWORD_CHANGED,
                            DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD, userHandle);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    /**
     * Called any time the device password is updated. Resets all password expiration clocks.
     */
    private void updatePasswordExpirationsLocked(int userHandle) {
            List<UserInfo> profiles = mUserManager.getProfiles(userHandle);
            for (UserInfo userInfo : profiles) {
                int profileId = userInfo.getUserHandle().getIdentifier();
                DevicePolicyData policy = getUserData(profileId);
                final int N = policy.mAdminList.size();
                if (N > 0) {
                    for (int i=0; i<N; i++) {
                        ActiveAdmin admin = policy.mAdminList.get(i);
                        if (admin.info.usesPolicy(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD)) {
                            long timeout = admin.passwordExpirationTimeout;
                            long expiration = timeout > 0L ? (timeout + System.currentTimeMillis()) : 0L;
                            admin.passwordExpirationDate = expiration;
                        }
                    }
                }
                saveSettingsLocked(profileId);
            }
    }

    public void reportFailedPasswordAttempt(int userHandle) {
        enforceCrossUserPermission(userHandle);
        enforceNotManagedProfile(userHandle, "report failed password attempt");
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);

        synchronized (this) {
            DevicePolicyData policy = getUserData(userHandle);
            long ident = Binder.clearCallingIdentity();
            try {
                policy.mFailedPasswordAttempts++;
                saveSettingsLocked(userHandle);
                if (mHasFeature) {
                    int max = getMaximumFailedPasswordsForWipe(null, userHandle);
                    if (max > 0 && policy.mFailedPasswordAttempts >= max) {
                        wipeDeviceOrUserLocked(0, userHandle);
                    }
                    sendAdminCommandToSelfAndProfilesLocked(
                            DeviceAdminReceiver.ACTION_PASSWORD_FAILED,
                            DeviceAdminInfo.USES_POLICY_WATCH_LOGIN, userHandle);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    public void reportSuccessfulPasswordAttempt(int userHandle) {
        enforceCrossUserPermission(userHandle);
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.BIND_DEVICE_ADMIN, null);

        synchronized (this) {
            DevicePolicyData policy = getUserData(userHandle);
            if (policy.mFailedPasswordAttempts != 0 || policy.mPasswordOwner >= 0) {
                long ident = Binder.clearCallingIdentity();
                try {
                    policy.mFailedPasswordAttempts = 0;
                    policy.mPasswordOwner = -1;
                    saveSettingsLocked(userHandle);
                    if (mHasFeature) {
                        sendAdminCommandToSelfAndProfilesLocked(
                                DeviceAdminReceiver.ACTION_PASSWORD_SUCCEEDED,
                                DeviceAdminInfo.USES_POLICY_WATCH_LOGIN, userHandle);
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    public ComponentName setGlobalProxy(ComponentName who, String proxySpec,
            String exclusionList, int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        enforceCrossUserPermission(userHandle);
        synchronized(this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }

            // Only check if owner has set global proxy. We don't allow other users to set it.
            DevicePolicyData policy = getUserData(UserHandle.USER_OWNER);
            ActiveAdmin admin = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY);

            // Scan through active admins and find if anyone has already
            // set the global proxy.
            Set<ComponentName> compSet = policy.mAdminMap.keySet();
            for (ComponentName component : compSet) {
                ActiveAdmin ap = policy.mAdminMap.get(component);
                if ((ap.specifiesGlobalProxy) && (!component.equals(who))) {
                    // Another admin already sets the global proxy
                    // Return it to the caller.
                    return component;
                }
            }

            // If the user is not the owner, don't set the global proxy. Fail silently.
            if (UserHandle.getCallingUserId() != UserHandle.USER_OWNER) {
                Slog.w(LOG_TAG, "Only the owner is allowed to set the global proxy. User "
                        + userHandle + " is not permitted.");
                return null;
            }
            if (proxySpec == null) {
                admin.specifiesGlobalProxy = false;
                admin.globalProxySpec = null;
                admin.globalProxyExclusionList = null;
            } else {

                admin.specifiesGlobalProxy = true;
                admin.globalProxySpec = proxySpec;
                admin.globalProxyExclusionList = exclusionList;
            }

            // Reset the global proxy accordingly
            // Do this using system permissions, as apps cannot write to secure settings
            long origId = Binder.clearCallingIdentity();
            try {
                resetGlobalProxyLocked(policy);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
            return null;
        }
    }

    public ComponentName getGlobalProxyAdmin(int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        enforceCrossUserPermission(userHandle);
        synchronized(this) {
            DevicePolicyData policy = getUserData(UserHandle.USER_OWNER);
            // Scan through active admins and find if anyone has already
            // set the global proxy.
            final int N = policy.mAdminList.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin ap = policy.mAdminList.get(i);
                if (ap.specifiesGlobalProxy) {
                    // Device admin sets the global proxy
                    // Return it to the caller.
                    return ap.info.getComponent();
                }
            }
        }
        // No device admin sets the global proxy.
        return null;
    }

    public void setRecommendedGlobalProxy(ComponentName who, ProxyInfo proxyInfo) {
        synchronized (this) {
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_DEVICE_OWNER);
        }
        long token = Binder.clearCallingIdentity();
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.setGlobalProxy(proxyInfo);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void resetGlobalProxyLocked(DevicePolicyData policy) {
        final int N = policy.mAdminList.size();
        for (int i = 0; i < N; i++) {
            ActiveAdmin ap = policy.mAdminList.get(i);
            if (ap.specifiesGlobalProxy) {
                saveGlobalProxyLocked(ap.globalProxySpec, ap.globalProxyExclusionList);
                return;
            }
        }
        // No device admins defining global proxies - reset global proxy settings to none
        saveGlobalProxyLocked(null, null);
    }

    private void saveGlobalProxyLocked(String proxySpec, String exclusionList) {
        if (exclusionList == null) {
            exclusionList = "";
        }
        if (proxySpec == null) {
            proxySpec = "";
        }
        // Remove white spaces
        proxySpec = proxySpec.trim();
        String data[] = proxySpec.split(":");
        int proxyPort = 8080;
        if (data.length > 1) {
            try {
                proxyPort = Integer.parseInt(data[1]);
            } catch (NumberFormatException e) {}
        }
        exclusionList = exclusionList.trim();
        ContentResolver res = mContext.getContentResolver();

        ProxyInfo proxyProperties = new ProxyInfo(data[0], proxyPort, exclusionList);
        if (!proxyProperties.isValid()) {
            Slog.e(LOG_TAG, "Invalid proxy properties, ignoring: " + proxyProperties.toString());
            return;
        }
        Settings.Global.putString(res, Settings.Global.GLOBAL_HTTP_PROXY_HOST, data[0]);
        Settings.Global.putInt(res, Settings.Global.GLOBAL_HTTP_PROXY_PORT, proxyPort);
        Settings.Global.putString(res, Settings.Global.GLOBAL_HTTP_PROXY_EXCLUSION_LIST,
                exclusionList);
    }

    /**
     * Set the storage encryption request for a single admin.  Returns the new total request
     * status (for all admins).
     */
    public int setStorageEncryption(ComponentName who, boolean encrypt, int userHandle) {
        if (!mHasFeature) {
            return DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            // Check for permissions
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            // Only owner can set storage encryption
            if (userHandle != UserHandle.USER_OWNER
                    || UserHandle.getCallingUserId() != UserHandle.USER_OWNER) {
                Slog.w(LOG_TAG, "Only owner is allowed to set storage encryption. User "
                        + UserHandle.getCallingUserId() + " is not permitted.");
                return 0;
            }

            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_ENCRYPTED_STORAGE);

            // Quick exit:  If the filesystem does not support encryption, we can exit early.
            if (!isEncryptionSupported()) {
                return DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
            }

            // (1) Record the value for the admin so it's sticky
            if (ap.encryptionRequested != encrypt) {
                ap.encryptionRequested = encrypt;
                saveSettingsLocked(userHandle);
            }

            DevicePolicyData policy = getUserData(UserHandle.USER_OWNER);
            // (2) Compute "max" for all admins
            boolean newRequested = false;
            final int N = policy.mAdminList.size();
            for (int i = 0; i < N; i++) {
                newRequested |= policy.mAdminList.get(i).encryptionRequested;
            }

            // Notify OS of new request
            setEncryptionRequested(newRequested);

            // Return the new global request status
            return newRequested
                    ? DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE
                    : DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE;
        }
    }

    /**
     * Get the current storage encryption request status for a given admin, or aggregate of all
     * active admins.
     */
    public boolean getStorageEncryption(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            // Check for permissions if a particular caller is specified
            if (who != null) {
                // When checking for a single caller, status is based on caller's request
                ActiveAdmin ap = getActiveAdminUncheckedLocked(who, userHandle);
                return ap != null ? ap.encryptionRequested : false;
            }

            // If no particular caller is specified, return the aggregate set of requests.
            // This is short circuited by returning true on the first hit.
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            for (int i = 0; i < N; i++) {
                if (policy.mAdminList.get(i).encryptionRequested) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Get the current encryption status of the device.
     */
    public int getStorageEncryptionStatus(int userHandle) {
        if (!mHasFeature) {
            // Ok to return current status.
        }
        enforceCrossUserPermission(userHandle);
        return getEncryptionStatus();
    }

    /**
     * Hook to low-levels:  This should report if the filesystem supports encrypted storage.
     */
    private boolean isEncryptionSupported() {
        // Note, this can be implemented as
        //   return getEncryptionStatus() != DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
        // But is provided as a separate internal method if there's a faster way to do a
        // simple check for supported-or-not.
        return getEncryptionStatus() != DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
    }

    /**
     * Hook to low-levels:  Reporting the current status of encryption.
     * @return A value such as {@link DevicePolicyManager#ENCRYPTION_STATUS_UNSUPPORTED} or
     * {@link DevicePolicyManager#ENCRYPTION_STATUS_INACTIVE} or
     * {@link DevicePolicyManager#ENCRYPTION_STATUS_ACTIVE}.
     */
    private int getEncryptionStatus() {
        String status = SystemProperties.get("ro.crypto.state", "unsupported");
        if ("encrypted".equalsIgnoreCase(status)) {
            return DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
        } else if ("unencrypted".equalsIgnoreCase(status)) {
            return DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE;
        } else {
            return DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
        }
    }

    /**
     * Hook to low-levels:  If needed, record the new admin setting for encryption.
     */
    private void setEncryptionRequested(boolean encrypt) {
    }

    /**
     * The system property used to share the state of the camera. The native camera service
     * is expected to read this property and act accordingly.
     */
    public static final String SYSTEM_PROP_DISABLE_CAMERA = "sys.secpolicy.camera.disabled";

    /**
     * Disables all device cameras according to the specified admin.
     */
    public void setCameraDisabled(ComponentName who, boolean disabled, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        enforceNotManagedProfile(userHandle, "enable/disable cameras");
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA);
            if (ap.disableCamera != disabled) {
                ap.disableCamera = disabled;
                saveSettingsLocked(userHandle);
            }
            syncDeviceCapabilitiesLocked(getUserData(userHandle));
        }
    }

    /**
     * Gets whether or not all device cameras are disabled for a given admin, or disabled for any
     * active admins.
     */
    public boolean getCameraDisabled(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        synchronized (this) {
            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return (admin != null) ? admin.disableCamera : false;
            }

            DevicePolicyData policy = getUserData(userHandle);
            // Determine whether or not the device camera is disabled for any active admins.
            final int N = policy.mAdminList.size();
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = policy.mAdminList.get(i);
                if (admin.disableCamera) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Selectively disable keyguard features.
     */
    public void setKeyguardDisabledFeatures(ComponentName who, int which, int userHandle) {
        if (!mHasFeature) {
            return;
        }
        enforceCrossUserPermission(userHandle);
        enforceNotManagedProfile(userHandle, "disable keyguard features");
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES);
            if (ap.disabledKeyguardFeatures != which) {
                ap.disabledKeyguardFeatures = which;
                saveSettingsLocked(userHandle);
            }
            syncDeviceCapabilitiesLocked(getUserData(userHandle));
        }
    }

    /**
     * Gets the disabled state for features in keyguard for the given admin,
     * or the aggregate of all active admins if who is null.
     */
    public int getKeyguardDisabledFeatures(ComponentName who, int userHandle) {
        if (!mHasFeature) {
            return 0;
        }
        enforceCrossUserPermission(userHandle);
        synchronized (this) {
            if (who != null) {
                ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
                return (admin != null) ? admin.disabledKeyguardFeatures : 0;
            }

            // Determine which keyguard features are disabled for any active admins.
            DevicePolicyData policy = getUserData(userHandle);
            final int N = policy.mAdminList.size();
            int which = 0;
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = policy.mAdminList.get(i);
                which |= admin.disabledKeyguardFeatures;
            }
            return which;
        }
    }

    @Override
    public boolean setDeviceOwner(String packageName, String ownerName) {
        if (!mHasFeature) {
            return false;
        }
        if (packageName == null
                || !DeviceOwner.isInstalled(packageName, mContext.getPackageManager())) {
            throw new IllegalArgumentException("Invalid package name " + packageName
                    + " for device owner");
        }
        synchronized (this) {
            if (isDeviceProvisioned()) {
                throw new IllegalStateException(
                        "Trying to set device owner but device is already provisioned.");
            }

            if (mDeviceOwner != null && mDeviceOwner.hasDeviceOwner()) {
                throw new IllegalStateException(
                        "Trying to set device owner but device owner is already set.");
            }

            long token = Binder.clearCallingIdentity();
            try {
                mAppOpsService.setDeviceOwner(packageName);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, "Unable to notify AppOpsService of DeviceOwner", e);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            if (mDeviceOwner == null) {
                // Device owner is not set and does not exist, set it.
                mDeviceOwner = DeviceOwner.createWithDeviceOwner(packageName, ownerName);
                mDeviceOwner.writeOwnerFile();
                return true;
            } else {
                // Device owner is not set but a profile owner exists, update Device owner state.
                mDeviceOwner.setDeviceOwner(packageName, ownerName);
                mDeviceOwner.writeOwnerFile();
                return true;
            }
        }
    }

    @Override
    public boolean isDeviceOwner(String packageName) {
        if (!mHasFeature) {
            return false;
        }
        synchronized (this) {
            return mDeviceOwner != null
                    && mDeviceOwner.hasDeviceOwner()
                    && mDeviceOwner.getDeviceOwnerPackageName().equals(packageName);
        }
    }

    @Override
    public String getDeviceOwner() {
        if (!mHasFeature) {
            return null;
        }
        synchronized (this) {
            if (mDeviceOwner != null && mDeviceOwner.hasDeviceOwner()) {
                return mDeviceOwner.getDeviceOwnerPackageName();
            }
        }
        return null;
    }

    @Override
    public String getDeviceOwnerName() {
        if (!mHasFeature) {
            return null;
        }
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MANAGE_USERS, null);
        synchronized (this) {
            if (mDeviceOwner != null) {
                return mDeviceOwner.getDeviceOwnerName();
            }
        }
        return null;
    }

    @Override
    public void clearDeviceOwner(String packageName) {
        if (packageName == null) {
            throw new NullPointerException("packageName is null");
        }
        try {
            int uid = mContext.getPackageManager().getPackageUid(packageName, 0);
            if (uid != Binder.getCallingUid()) {
                throw new SecurityException("Invalid packageName");
            }
        } catch (NameNotFoundException e) {
            throw new SecurityException(e);
        }
        if (!isDeviceOwner(packageName)) {
            throw new SecurityException("clearDeviceOwner can only be called by the device owner");
        }
        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                mUserManager.setUserRestrictions(new Bundle(),
                        new UserHandle(UserHandle.USER_OWNER));
                if (mDeviceOwner != null) {
                    mDeviceOwner.clearDeviceOwner();
                    mDeviceOwner.writeOwnerFile();
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public boolean setProfileOwner(String packageName, String ownerName, int userHandle) {
        if (!mHasFeature) {
            return false;
        }
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MANAGE_USERS, null);

        if (mUserManager.getUserInfo(userHandle) == null) {
            // User doesn't exist.
            throw new IllegalArgumentException(
                    "Attempted to set profile owner for invalid userId: " + userHandle);
        }

        if (packageName == null
                || !DeviceOwner.isInstalledForUser(packageName, userHandle)) {
            throw new IllegalArgumentException("Package name " + packageName
                    + " not installed for userId:" + userHandle);
        }
        synchronized (this) {
            if (isUserSetupComplete(userHandle)) {
                throw new IllegalStateException(
                        "Trying to set profile owner but user is already set-up.");
            }
            long token = Binder.clearCallingIdentity();
            try {
                mAppOpsService.setProfileOwner(packageName, userHandle);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, "Unable to notify AppOpsService of ProfileOwner", e);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            if (mDeviceOwner == null) {
                // Device owner state does not exist, create it.
                mDeviceOwner = DeviceOwner.createWithProfileOwner(packageName, ownerName,
                        userHandle);
                mDeviceOwner.writeOwnerFile();
                return true;
            } else {
                // Device owner already exists, update it.
                mDeviceOwner.setProfileOwner(packageName, ownerName, userHandle);
                mDeviceOwner.writeOwnerFile();
                return true;
            }
        }
    }

    @Override
    public void setProfileEnabled(ComponentName who) {
        if (!mHasFeature) {
            return;
        }
        synchronized (this) {
            // Check for permissions
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            // Check if this is the profile owner who is calling
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);
            int userId = UserHandle.getCallingUserId();
            Slog.d(LOG_TAG, "Enabling the profile for: " + userId);

            long id = Binder.clearCallingIdentity();
            try {
                mUserManager.setUserEnabled(userId);
                Intent intent = new Intent(Intent.ACTION_MANAGED_PROFILE_ADDED);
                intent.putExtra(Intent.EXTRA_USER, new UserHandle(UserHandle.getCallingUserId()));
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY |
                        Intent.FLAG_RECEIVER_FOREGROUND);
                // TODO This should send to parent of profile (which is always owner at the moment).
                mContext.sendBroadcastAsUser(intent, UserHandle.OWNER);
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public String getProfileOwner(int userHandle) {
        if (!mHasFeature) {
            return null;
        }

        synchronized (this) {
            if (mDeviceOwner != null) {
                return mDeviceOwner.getProfileOwnerPackageName(userHandle);
            }
        }
        return null;
    }

    @Override
    public String getProfileOwnerName(int userHandle) {
        if (!mHasFeature) {
            return null;
        }
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MANAGE_USERS, null);

        synchronized (this) {
            if (mDeviceOwner != null) {
                return mDeviceOwner.getProfileOwnerName(userHandle);
            }
        }
        return null;
    }

    private boolean isDeviceProvisioned() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) > 0;
    }

    private boolean isUserSetupComplete(int userId) {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0, userId) > 0;
    }

    private void enforceCrossUserPermission(int userHandle) {
        if (userHandle < 0) {
            throw new IllegalArgumentException("Invalid userId " + userHandle);
        }
        final int callingUid = Binder.getCallingUid();
        if (userHandle == UserHandle.getUserId(callingUid)) return;
        if (callingUid != Process.SYSTEM_UID && callingUid != 0) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, "Must be system or have"
                    + " INTERACT_ACROSS_USERS_FULL permission");
        }
    }

    private void enforceNotManagedProfile(int userHandle, String message) {
        if(isManagedProfile(userHandle)) {
            throw new SecurityException("You can not " + message + " for a managed profile. ");
        }
    }

    private UserInfo getProfileParent(int userHandle) {
        long ident = Binder.clearCallingIdentity();
        try {
            return mUserManager.getProfileParent(userHandle);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private boolean isManagedProfile(int userHandle) {
        long ident = Binder.clearCallingIdentity();
        try {
            return mUserManager.getUserInfo(userHandle).isManagedProfile();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void enableIfNecessary(String packageName, int userId) {
        try {
            IPackageManager ipm = AppGlobals.getPackageManager();
            ApplicationInfo ai = ipm.getApplicationInfo(packageName,
                    PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS,
                    userId);
            if (ai.enabledSetting
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                ipm.setApplicationEnabledSetting(packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP, userId, "DevicePolicyManager");
            }
        } catch (RemoteException e) {
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {

            pw.println("Permission Denial: can't dump DevicePolicyManagerService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }

        final Printer p = new PrintWriterPrinter(pw);

        synchronized (this) {
            p.println("Current Device Policy Manager state:");

            int userCount = mUserData.size();
            for (int u = 0; u < userCount; u++) {
                DevicePolicyData policy = getUserData(mUserData.keyAt(u));
                p.println("  Enabled Device Admins (User " + policy.mUserHandle + "):");
                final int N = policy.mAdminList.size();
                for (int i=0; i<N; i++) {
                    ActiveAdmin ap = policy.mAdminList.get(i);
                    if (ap != null) {
                        pw.print("  "); pw.print(ap.info.getComponent().flattenToShortString());
                                pw.println(":");
                        ap.dump("    ", pw);
                    }
                }

                pw.println(" ");
                pw.print("  mPasswordOwner="); pw.println(policy.mPasswordOwner);
            }
        }
    }

    @Override
    public void addPersistentPreferredActivity(ComponentName who, IntentFilter filter,
            ComponentName activity) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            IPackageManager pm = AppGlobals.getPackageManager();
            long id = Binder.clearCallingIdentity();
            try {
                pm.addPersistentPreferredActivity(filter, activity, UserHandle.getCallingUserId());
            } catch (RemoteException re) {
                // Shouldn't happen
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public void clearPackagePersistentPreferredActivities(ComponentName who, String packageName) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            IPackageManager pm = AppGlobals.getPackageManager();
            long id = Binder.clearCallingIdentity();
            try {
                pm.clearPackagePersistentPreferredActivities(packageName, UserHandle.getCallingUserId());
            } catch (RemoteException re) {
                // Shouldn't happen
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public void setApplicationRestrictions(ComponentName who, String packageName, Bundle settings) {
        final UserHandle userHandle = new UserHandle(UserHandle.getCallingUserId());

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                mUserManager.setApplicationRestrictions(packageName, settings, userHandle);
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public void setRestrictionsProvider(ComponentName who, ComponentName permissionProvider) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            int userHandle = UserHandle.getCallingUserId();
            DevicePolicyData userData = getUserData(userHandle);
            userData.mRestrictionsProvider = permissionProvider;
            saveSettingsLocked(userHandle);
        }
    }

    @Override
    public ComponentName getRestrictionsProvider(int userHandle) {
        synchronized (this) {
            if (Binder.getCallingUid() != Process.SYSTEM_UID) {
                throw new SecurityException("Only the system can query the permission provider");
            }
            DevicePolicyData userData = getUserData(userHandle);
            return userData != null ? userData.mRestrictionsProvider : null;
        }
    }

    public void addCrossProfileIntentFilter(ComponentName who, IntentFilter filter, int flags) {
        int callingUserId = UserHandle.getCallingUserId();
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            IPackageManager pm = AppGlobals.getPackageManager();
            long id = Binder.clearCallingIdentity();
            try {
                if ((flags & DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED) != 0) {
                    pm.addCrossProfileIntentFilter(filter, true /*removable*/, callingUserId,
                            UserHandle.USER_OWNER);
                }
                if ((flags & DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT) != 0) {
                    pm.addCrossProfileIntentFilter(filter, true /*removable*/, UserHandle.USER_OWNER,
                            callingUserId);
                }
            } catch (RemoteException re) {
                // Shouldn't happen
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    public void clearCrossProfileIntentFilters(ComponentName who) {
        int callingUserId = UserHandle.getCallingUserId();
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);
            IPackageManager pm = AppGlobals.getPackageManager();
            long id = Binder.clearCallingIdentity();
            try {
                pm.clearCrossProfileIntentFilters(callingUserId);
                pm.clearCrossProfileIntentFilters(UserHandle.USER_OWNER);
            } catch (RemoteException re) {
                // Shouldn't happen
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public UserHandle createUser(ComponentName who, String name) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_DEVICE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                UserInfo userInfo = mUserManager.createUser(name, 0 /* flags */);
                if (userInfo != null) {
                    return userInfo.getUserHandle();
                }
                return null;
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public UserHandle createAndInitializeUser(ComponentName who, String name,
            String ownerName, ComponentName profileOwnerComponent, Bundle adminExtras) {
        UserHandle user = createUser(who, name);
        long id = Binder.clearCallingIdentity();
        try {
            String profileOwnerPkg = profileOwnerComponent.getPackageName();
            final IPackageManager ipm = AppGlobals.getPackageManager();
            IActivityManager activityManager = ActivityManagerNative.getDefault();

            try {
                // Install the profile owner if not present.
                if (!ipm.isPackageAvailable(profileOwnerPkg, user.getIdentifier())) {
                    ipm.installExistingPackageAsUser(profileOwnerPkg, user.getIdentifier());
                }

                // Start user in background.
                activityManager.startUserInBackground(user.getIdentifier());
            } catch (RemoteException e) {
                Slog.e(LOG_TAG, "Failed to make remote calls for configureUser", e);
            }

            setActiveAdmin(profileOwnerComponent, true, user.getIdentifier(), adminExtras);
            setProfileOwner(profileOwnerPkg, ownerName, user.getIdentifier());
            return user;
        } finally {
            restoreCallingIdentity(id);
        }
    }

    @Override
    public boolean removeUser(ComponentName who, UserHandle userHandle) {
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_DEVICE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                return mUserManager.removeUser(userHandle.getIdentifier());
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public Bundle getApplicationRestrictions(ComponentName who, String packageName) {
        final UserHandle userHandle = new UserHandle(UserHandle.getCallingUserId());

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                return mUserManager.getApplicationRestrictions(packageName, userHandle);
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public void setUserRestriction(ComponentName who, String key, boolean enabled) {
        final UserHandle userHandle = new UserHandle(UserHandle.getCallingUserId());

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                mUserManager.setUserRestriction(key, enabled, userHandle);
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public boolean setApplicationBlocked(ComponentName who, String packageName,
            boolean blocked) {
        int callingUserId = UserHandle.getCallingUserId();
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                IPackageManager pm = AppGlobals.getPackageManager();
                return pm.setApplicationBlockedSettingAsUser(packageName, blocked, callingUserId);
            } catch (RemoteException re) {
                // shouldn't happen
                Slog.e(LOG_TAG, "Failed to setApplicationBlockedSetting", re);
            } finally {
                restoreCallingIdentity(id);
            }
            return false;
        }
    }

    @Override
    public int setApplicationsBlocked(ComponentName who, Intent intent, boolean blocked) {
        int callingUserId = UserHandle.getCallingUserId();
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                IPackageManager pm = AppGlobals.getPackageManager();
                List<ResolveInfo> activitiesToEnable = pm.queryIntentActivities(intent,
                        intent.resolveTypeIfNeeded(mContext.getContentResolver()),
                        PackageManager.GET_DISABLED_COMPONENTS
                                | PackageManager.GET_UNINSTALLED_PACKAGES,
                        callingUserId);

                if (DBG) Slog.d(LOG_TAG, "Enabling activities: " + activitiesToEnable);
                int numberOfAppsUnblocked = 0;
                if (activitiesToEnable != null) {
                    for (ResolveInfo info : activitiesToEnable) {
                        if (info.activityInfo != null) {
                            numberOfAppsUnblocked++;
                            pm.setApplicationBlockedSettingAsUser(info.activityInfo.packageName,
                                    blocked, callingUserId);
                        }
                    }
                }
                return numberOfAppsUnblocked;
            } catch (RemoteException re) {
                // shouldn't happen
                Slog.e(LOG_TAG, "Failed to setApplicationsBlockedSettingsWithIntent", re);
            } finally {
                restoreCallingIdentity(id);
            }
            return 0;
        }
    }

    @Override
    public boolean isApplicationBlocked(ComponentName who, String packageName) {
        int callingUserId = UserHandle.getCallingUserId();
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                IPackageManager pm = AppGlobals.getPackageManager();
                return pm.getApplicationBlockedSettingAsUser(packageName, callingUserId);
            } catch (RemoteException re) {
                // shouldn't happen
                Slog.e(LOG_TAG, "Failed to getApplicationBlockedSettingAsUser", re);
            } finally {
                restoreCallingIdentity(id);
            }
            return false;
        }
    }

    @Override
    public void setAccountManagementDisabled(ComponentName who, String accountType,
            boolean disabled) {
        if (!mHasFeature) {
            return;
        }
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            ActiveAdmin ap = getActiveAdminForCallerLocked(who,
                    DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);
            if (disabled) {
                ap.accountTypesWithManagementDisabled.add(accountType);
            } else {
                ap.accountTypesWithManagementDisabled.remove(accountType);
            }
            saveSettingsLocked(UserHandle.getCallingUserId());
        }
    }

    @Override
    public String[] getAccountTypesWithManagementDisabled() {
        if (!mHasFeature) {
            return null;
        }
        synchronized (this) {
            DevicePolicyData policy = getUserData(UserHandle.getCallingUserId());
            final int N = policy.mAdminList.size();
            HashSet<String> resultSet = new HashSet<String>();
            for (int i = 0; i < N; i++) {
                ActiveAdmin admin = policy.mAdminList.get(i);
                resultSet.addAll(admin.accountTypesWithManagementDisabled);
            }
            return resultSet.toArray(new String[resultSet.size()]);
        }
    }

    /**
     * Sets which packages may enter lock task mode.
     *
     * This function can only be called by the device owner or the profile owner.
     * @param components The list of components allowed to enter lock task mode.
     */
    public void setLockTaskPackages(String[] packages) throws SecurityException {
        // Get the package names of the caller.
        int uid = Binder.getCallingUid();
        String[] packageNames = mContext.getPackageManager().getPackagesForUid(uid);

        synchronized (this) {
            // Check whether any of the package name is the device owner or the profile owner.
            for (int i=0; i<packageNames.length; i++) {
                String packageName = packageNames[i];
                int userHandle = UserHandle.getUserId(uid);
                String profileOwnerPackage = getProfileOwner(userHandle);
                if (isDeviceOwner(packageName) ||
                    (profileOwnerPackage != null && profileOwnerPackage.equals(packageName))) {

                    // If a package name is the device owner or the profile owner,
                    // we update the component list.
                    DevicePolicyData policy = getUserData(userHandle);
                    policy.mLockTaskPackages.clear();
                    if (packages != null) {
                        for (int j = 0; j < packages.length; j++) {
                            String pkg = packages[j];
                            policy.mLockTaskPackages.add(pkg);
                        }
                    }

                    // Store the settings persistently.
                    saveSettingsLocked(userHandle);
                    return;
                }
            }
        }
        throw new SecurityException();
    }

    /**
     * This function returns the list of components allowed to start the task lock mode.
     */
    public String[] getLockTaskPackages() {
        synchronized (this) {
            int userHandle = UserHandle.USER_OWNER;
            DevicePolicyData policy = getUserData(userHandle);
            return policy.mLockTaskPackages.toArray(new String[0]);
        }
    }

    /**
     * This function lets the caller know whether the given package is allowed to start the
     * lock task mode.
     * @param pkg The package to check
     */
    public boolean isLockTaskPermitted(String pkg) {
        // Get current user's devicepolicy
        int uid = Binder.getCallingUid();
        int userHandle = UserHandle.getUserId(uid);
        DevicePolicyData policy = getUserData(userHandle);
        synchronized (this) {
            for (int i = 0; i < policy.mLockTaskPackages.size(); i++) {
                String lockTaskPackage = policy.mLockTaskPackages.get(i);

                // If the given package equals one of the packages stored our list,
                // we allow this package to start lock task mode.
                if (lockTaskPackage.equals(pkg)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void notifyLockTaskModeChanged(boolean isEnabled, String pkg, int userHandle) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("notifyLockTaskModeChanged can only be called by system");
        }
        synchronized (this) {
            final DevicePolicyData policy = getUserData(userHandle);
            Bundle adminExtras = new Bundle();
            adminExtras.putBoolean(DeviceAdminReceiver.EXTRA_LOCK_TASK_ENTERING, isEnabled);
            adminExtras.putString(DeviceAdminReceiver.EXTRA_LOCK_TASK_PACKAGE, pkg);
            for (ActiveAdmin admin : policy.mAdminList) {
                boolean ownsDevice = isDeviceOwner(admin.info.getPackageName());
                boolean ownsProfile = (getProfileOwner(userHandle) != null
                        && getProfileOwner(userHandle).equals(admin.info.getPackageName()));
                if (ownsDevice || ownsProfile) {
                    sendAdminCommandLocked(admin, DeviceAdminReceiver.ACTION_LOCK_TASK_CHANGED,
                            adminExtras, null);
                }
            }
        }
    }

    @Override
    public void setGlobalSetting(ComponentName who, String setting, String value) {
        final ContentResolver contentResolver = mContext.getContentResolver();

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_DEVICE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                Settings.Global.putString(contentResolver, setting, value);
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public void setSecureSetting(ComponentName who, String setting, String value) {
        int callingUserId = UserHandle.getCallingUserId();
        final ContentResolver contentResolver = mContext.getContentResolver();

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            long id = Binder.clearCallingIdentity();
            try {
                Settings.Secure.putStringForUser(contentResolver, setting, value, callingUserId);
            } finally {
                restoreCallingIdentity(id);
            }
        }
    }

    @Override
    public void setMasterVolumeMuted(ComponentName who, boolean on) {
        final ContentResolver contentResolver = mContext.getContentResolver();

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            IAudioService iAudioService = IAudioService.Stub.asInterface(
                    ServiceManager.getService(Context.AUDIO_SERVICE));
            try{
                iAudioService.setMasterMute(on, 0, who.getPackageName(), null);
            } catch (RemoteException re) {
                Slog.e(LOG_TAG, "Failed to setMasterMute", re);
            }
        }
    }

    @Override
    public boolean isMasterVolumeMuted(ComponentName who) {
        final ContentResolver contentResolver = mContext.getContentResolver();

        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            getActiveAdminForCallerLocked(who, DeviceAdminInfo.USES_POLICY_PROFILE_OWNER);

            AudioManager audioManager =
                    (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            return audioManager.isMasterMute();
        }
    }
}
