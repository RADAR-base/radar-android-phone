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

import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Arrays;
import java.util.List;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class PhoneLocationProvider extends DeviceServiceProvider<BaseDeviceState> {
    @Override
    public Class<?> getServiceClass() {
        return PhoneLocationService.class;
    }

    @Override
    public Parcelable.Creator<BaseDeviceState> getStateCreator() {
        return BaseDeviceState.CREATOR;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.phoneLocationServiceDisplayName);
    }

    @Override
    public List<String> needsPermissions() {
        return Arrays.asList(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION);
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }
}
