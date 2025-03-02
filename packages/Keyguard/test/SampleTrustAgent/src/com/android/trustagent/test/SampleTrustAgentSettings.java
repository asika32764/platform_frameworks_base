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
 * limitations under the License
 */

package com.android.trustagent.test;

import android.annotation.Nullable;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

public class SampleTrustAgentSettings extends Activity implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener {

    private static final int TRUST_DURATION_MS = 30 * 1000;

    private CheckBox mReportUnlockAttempts;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_trust_agent_settings);

        findViewById(R.id.enable_trust).setOnClickListener(this);
        findViewById(R.id.revoke_trust).setOnClickListener(this);

        mReportUnlockAttempts = (CheckBox) findViewById(R.id.report_unlock_attempts);
        mReportUnlockAttempts.setOnCheckedChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mReportUnlockAttempts.setChecked(SampleTrustAgent.getReportUnlockAttempts(this));
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.enable_trust) {
            SampleTrustAgent.sendGrantTrust(this, "SampleTrustAgent", TRUST_DURATION_MS,
                    null /* extra */);
        } else if (id == R.id.revoke_trust) {
            SampleTrustAgent.sendRevokeTrust(this);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.report_unlock_attempts) {
            SampleTrustAgent.setReportUnlockAttempts(this, isChecked);
        }
    }
}
