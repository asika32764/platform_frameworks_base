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

package com.android.printspooler.ui;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;

import android.widget.TextView;
import com.android.printspooler.R;

/**
 * Fragment for showing a work in progress UI.
 */
public final class PrintProgressFragment extends Fragment {

    public interface OnCancelRequestListener {
        public void onCancelRequest();
    }

    public static PrintProgressFragment newInstance() {
        return new PrintProgressFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup root,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.print_progress_fragment, root, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Button cancelButton = (Button) view.findViewById(R.id.cancel_button);
        final TextView message = (TextView) view.findViewById(R.id.message);

        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Activity activity = getActivity();
                if (activity instanceof OnCancelRequestListener) {
                    ((OnCancelRequestListener) getActivity()).onCancelRequest();
                }
                cancelButton.setVisibility(View.GONE);
                message.setVisibility(View.VISIBLE);
            }
        });
    }
}
