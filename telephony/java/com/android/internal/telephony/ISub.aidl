/*
* Copyright (C) 2011-2014 MediaTek Inc.
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

package com.android.internal.telephony;

import android.app.PendingIntent;
import android.telephony.SubInfoRecord;

interface ISub {
    /**
     * Get the SubInfoRecord according to an index
     * @param context Context provided by caller
     * @param subId The unique SubInfoRecord index in database
     * @return SubInfoRecord, maybe null
     */
    SubInfoRecord getSubInfoUsingSubId(long subId);

    /**
     * Get the SubInfoRecord according to an IccId
     * @param context Context provided by caller
     * @param iccId the IccId of SIM card
     * @return SubInfoRecord, maybe null
     */
    List<SubInfoRecord> getSubInfoUsingIccId(String iccId);

    /**
     * Get the SubInfoRecord according to slotId
     * @param context Context provided by caller
     * @param slotId the slot which the SIM is inserted
     * @return SubInfoRecord, maybe null
     */
    List<SubInfoRecord> getSubInfoUsingSlotId(int slotId);

    /**
     * Get all the SubInfoRecord(s) in subinfo database
     * @param context Context provided by caller
     * @return Array list of all SubInfoRecords in database, include thsoe that were inserted before
     */
    List<SubInfoRecord> getAllSubInfoList();

    /**
     * Get the SubInfoRecord(s) of the currently inserted SIM(s)
     * @param context Context provided by caller
     * @return Array list of currently inserted SubInfoRecord(s)
     */
    List<SubInfoRecord> getActivatedSubInfoList();

    /**
     * Get the SUB count of all SUB(s) in subinfo database
     * @param context Context provided by caller
     * @return all SIM count in database, include what was inserted before
     */
    int getAllSubInfoCount();

    /**
     * Add a new SubInfoRecord to subinfo database if needed
     * @param context Context provided by caller
     * @param iccId the IccId of the SIM card
     * @param slotId the slot which the SIM is inserted
     * @return the URL of the newly created row or the updated row
     */
    int addSubInfoRecord(String iccId, int slotId);

    /**
     * Set SIM color by simInfo index
     * @param context Context provided by caller
     * @param color the color of the SIM
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    int setColor(int color, long subId);

    /**
     * Set display name by simInfo index
     * @param context Context provided by caller
     * @param displayName the display name of SIM card
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    int setDisplayName(String displayName, long subId);

    /**
     * Set display name by simInfo index with name source
     * @param context Context provided by caller
     * @param displayName the display name of SIM card
     * @param subId the unique SubInfoRecord index in database
     * @param nameSource, 0: DEFAULT_SOURCE, 1: SIM_SOURCE, 2: USER_INPUT
     * @return the number of records updated
     */
    int setDisplayNameUsingSrc(String displayName, long subId, long nameSource);

    /**
     * Set phone number by subId
     * @param context Context provided by caller
     * @param number the phone number of the SIM
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    int setDispalyNumber(String number, long subId);

    /**
     * Set number display format. 0: none, 1: the first four digits, 2: the last four digits
     * @param context Context provided by caller
     * @param format the display format of phone number
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    int setDisplayNumberFormat(int format, long subId);

    /**
     * Set data roaming by simInfo index
     * @param context Context provided by caller
     * @param roaming 0:Don't allow data when roaming, 1:Allow data when roaming
     * @param subId the unique SubInfoRecord index in database
     * @return the number of records updated
     */
    int setDataRoaming(int roaming, long subId);

    int getSlotId(long subId);

    long[] getSubId(int slotId);

    long getDefaultSubId();

    int clearSubInfo();

    int getPhoneId(long subId);

    /**
     * Get the default data subscription
     * @return Id of the data subscription
     */
    long getDefaultDataSubId();

    void setDefaultDataSubId(long subId);

    long getDefaultVoiceSubId();

    void setDefaultVoiceSubId(long subId);
}
