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

import android.os.Build;
import android.os.Bundle;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Collections;
import java.util.List;

public class PhoneUsageProvider extends DeviceServiceProvider<BaseDeviceState> {
    private static final String PHONE_PREFIX = "org.radarcns.phone.";
    private static final String PHONE_USAGE_INTERVAL = "phone_usage_interval_seconds";
    private static final long USAGE_EVENT_PERIOD_DEFAULT = 60*60; // one hour

    public static final String PHONE_USAGE_INTERVAL_KEY = PHONE_PREFIX + PHONE_USAGE_INTERVAL;

    @Override
    public Class<?> getServiceClass() {
        return PhoneUsageService.class;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.phoneUsageServiceDisplayName);
    }

    @Override
    public List<String> needsPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Collections.singletonList(android.Manifest.permission.PACKAGE_USAGE_STATS);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    protected void configure(Bundle bundle) {
        super.configure(bundle);
        RadarConfiguration config = getConfig();
        bundle.putLong(PHONE_USAGE_INTERVAL_KEY, config.getLong(
                PHONE_USAGE_INTERVAL, USAGE_EVENT_PERIOD_DEFAULT));
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }
}
