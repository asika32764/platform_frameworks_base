/*
**
** Copyright 2010, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package android.app.admin;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ProxyInfo;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.UserHandle;

/**
 * Internal IPC interface to the device policy service.
 * {@hide}
 */
interface IDevicePolicyManager {
    void setPasswordQuality(in ComponentName who, int quality, int userHandle);
    int getPasswordQuality(in ComponentName who, int userHandle);

    void setPasswordMinimumLength(in ComponentName who, int length, int userHandle);
    int getPasswordMinimumLength(in ComponentName who, int userHandle);

    void setPasswordMinimumUpperCase(in ComponentName who, int length, int userHandle);
    int getPasswordMinimumUpperCase(in ComponentName who, int userHandle);

    void setPasswordMinimumLowerCase(in ComponentName who, int length, int userHandle);
    int getPasswordMinimumLowerCase(in ComponentName who, int userHandle);

    void setPasswordMinimumLetters(in ComponentName who, int length, int userHandle);
    int getPasswordMinimumLetters(in ComponentName who, int userHandle);

    void setPasswordMinimumNumeric(in ComponentName who, int length, int userHandle);
    int getPasswordMinimumNumeric(in ComponentName who, int userHandle);

    void setPasswordMinimumSymbols(in ComponentName who, int length, int userHandle);
    int getPasswordMinimumSymbols(in ComponentName who, int userHandle);

    void setPasswordMinimumNonLetter(in ComponentName who, int length, int userHandle);
    int getPasswordMinimumNonLetter(in ComponentName who, int userHandle);

    void setPasswordHistoryLength(in ComponentName who, int length, int userHandle);
    int getPasswordHistoryLength(in ComponentName who, int userHandle);

    void setPasswordExpirationTimeout(in ComponentName who, long expiration, int userHandle);
    long getPasswordExpirationTimeout(in ComponentName who, int userHandle);

    long getPasswordExpiration(in ComponentName who, int userHandle);

    boolean isActivePasswordSufficient(int userHandle);
    int getCurrentFailedPasswordAttempts(int userHandle);

    void setMaximumFailedPasswordsForWipe(in ComponentName admin, int num, int userHandle);
    int getMaximumFailedPasswordsForWipe(in ComponentName admin, int userHandle);

    boolean resetPassword(String password, int flags, int userHandle);

    void setMaximumTimeToLock(in ComponentName who, long timeMs, int userHandle);
    long getMaximumTimeToLock(in ComponentName who, int userHandle);

    void lockNow();

    void wipeData(int flags, int userHandle);

    ComponentName setGlobalProxy(in ComponentName admin, String proxySpec, String exclusionList, int userHandle);
    ComponentName getGlobalProxyAdmin(int userHandle);
    void setRecommendedGlobalProxy(in ComponentName admin, in ProxyInfo proxyInfo);

    int setStorageEncryption(in ComponentName who, boolean encrypt, int userHandle);
    boolean getStorageEncryption(in ComponentName who, int userHandle);
    int getStorageEncryptionStatus(int userHandle);

    void setCameraDisabled(in ComponentName who, boolean disabled, int userHandle);
    boolean getCameraDisabled(in ComponentName who, int userHandle);

    void setKeyguardDisabledFeatures(in ComponentName who, int which, int userHandle);
    int getKeyguardDisabledFeatures(in ComponentName who, int userHandle);

    void setActiveAdmin(in ComponentName policyReceiver, boolean refreshing, int userHandle);
    boolean isAdminActive(in ComponentName policyReceiver, int userHandle);
    List<ComponentName> getActiveAdmins(int userHandle);
    boolean packageHasActiveAdmins(String packageName, int userHandle);
    void getRemoveWarning(in ComponentName policyReceiver, in RemoteCallback result, int userHandle);
    void removeActiveAdmin(in ComponentName policyReceiver, int userHandle);
    boolean hasGrantedPolicy(in ComponentName policyReceiver, int usesPolicy, int userHandle);

    void setActivePasswordState(int quality, int length, int letters, int uppercase, int lowercase,
        int numbers, int symbols, int nonletter, int userHandle);
    void reportFailedPasswordAttempt(int userHandle);
    void reportSuccessfulPasswordAttempt(int userHandle);

    boolean setDeviceOwner(String packageName, String ownerName);
    boolean isDeviceOwner(String packageName);
    String getDeviceOwner();
    String getDeviceOwnerName();
    void clearDeviceOwner(String packageName);

    boolean setProfileOwner(String packageName, String ownerName, int userHandle);
    String getProfileOwner(int userHandle);
    String getProfileOwnerName(int userHandle);
    void setProfileEnabled(in ComponentName who);

    boolean installCaCert(in byte[] certBuffer);
    void uninstallCaCert(in byte[] certBuffer);

    void addPersistentPreferredActivity(in ComponentName admin, in IntentFilter filter, in ComponentName activity);
    void clearPackagePersistentPreferredActivities(in ComponentName admin, String packageName);

    void setApplicationRestrictions(in ComponentName who, in String packageName, in Bundle settings);
    Bundle getApplicationRestrictions(in ComponentName who, in String packageName);

    void setRestrictionsProvider(in ComponentName who, in ComponentName provider);
    ComponentName getRestrictionsProvider(int userHandle);

    void setUserRestriction(in ComponentName who, in String key, boolean enable);
    void addCrossProfileIntentFilter(in ComponentName admin, in IntentFilter filter, int flags);
    void clearCrossProfileIntentFilters(in ComponentName admin);

    boolean setApplicationBlocked(in ComponentName admin, in String packageName, boolean blocked);
    int setApplicationsBlocked(in ComponentName admin, in Intent intent, boolean blocked);
    boolean isApplicationBlocked(in ComponentName admin, in String packageName);

    UserHandle createUser(in ComponentName who, in String name);
    UserHandle createAndInitializeUser(in ComponentName who, in String name, in String profileOwnerName, in ComponentName profileOwnerComponent, in Bundle adminExtras);
    boolean removeUser(in ComponentName who, in UserHandle userHandle);

    void setAccountManagementDisabled(in ComponentName who, in String accountType, in boolean disabled);
    String[] getAccountTypesWithManagementDisabled();

    void setLockTaskPackages(in String[] packages);
    String[] getLockTaskPackages();
    boolean isLockTaskPermitted(in String pkg);

    void setGlobalSetting(in ComponentName who, in String setting, in String value);
    void setSecureSetting(in ComponentName who, in String setting, in String value);

    void setMasterVolumeMuted(in ComponentName admin, boolean on);
    boolean isMasterVolumeMuted(in ComponentName admin);

    void notifyLockTaskModeChanged(boolean isEnabled, String pkg, int userId);
}
