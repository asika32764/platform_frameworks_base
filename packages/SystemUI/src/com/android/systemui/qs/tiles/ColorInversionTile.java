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

package com.android.systemui.qs.tiles;

import android.provider.Settings.Secure;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.SecureSetting;

/** Quick settings tile: Invert colors **/
public class ColorInversionTile extends QSTile<QSTile.BooleanState> {

    private final SecureSetting mSetting;

    private boolean mVisible;

    public ColorInversionTile(Host host) {
        super(host);

        mSetting = new SecureSetting(mContext, mHandler,
                Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED) {
            @Override
            protected void handleValueChanged(int value) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        mSetting.setListening(listening);
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
        mSetting.rebindForCurrentUser();
    }

    @Override
    protected void handleClick() {
        mSetting.setValue(mState.value ? 0 : 1);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean enabled = value != 0;
        if (enabled) {
            mVisible = true;
        }
        state.visible = mVisible;
        state.value = enabled;
        state.label = mContext.getString(R.string.quick_settings_inversion_label);
        state.iconId = enabled ? R.drawable.ic_qs_inversion_on : R.drawable.ic_qs_inversion_off;
    }
}
