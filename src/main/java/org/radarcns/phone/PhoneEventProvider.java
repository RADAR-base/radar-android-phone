/*
 * Copyright 2017 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarcns.phone;

import android.os.Parcelable;

import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Arrays;
import java.util.List;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
//import static android.Manifest.permission.PACKAGE_USAGE_STATS;

public class PhoneEventProvider extends DeviceServiceProvider<PhoneState> {
    @Override
    public Class<?> getServiceClass() {
        return PhoneEventService.class;
    }

    @Override
    public Parcelable.Creator<PhoneState> getStateCreator() {
        return PhoneState.CREATOR;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.phoneServiceDisplayName);
    }

    @Override
    public List<String> needsPermissions() {
        // TODO: add PACKAGE_USAGE_STATS
        //Check if permission enabled
//        if (appUsages.getUsageStatsList().isEmpty()){
//            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
//            startActivity(intent);
//        }
        return Arrays.asList(WRITE_EXTERNAL_STORAGE, INTERNET);
    }
}
