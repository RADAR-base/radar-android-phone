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

import android.os.Bundle;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Arrays;
import java.util.List;

import static android.Manifest.permission.READ_CALL_LOG;
import static android.Manifest.permission.READ_SMS;

public class PhoneLogProvider extends DeviceServiceProvider<BaseDeviceState> {
    private static final String PREFIX = PhoneLogProvider.class.getName() + '.';
    private static final String CALL_SMS_LOG_INTERVAL = "call_sms_log_interval_seconds";
    public static final String CALL_SMS_LOG_INTERVAL_KEY = PREFIX + CALL_SMS_LOG_INTERVAL;
    private static final long CALL_SMS_LOG_INTERVAL_DEFAULT = 24 * 60 * 60; // seconds

    @Override
    public Class<?> getServiceClass() {
        return PhoneLogService.class;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.phoneLogServiceDisplayName);
    }

    @Override
    protected void configure(Bundle bundle) {
        super.configure(bundle);
        RadarConfiguration config = getConfig();
        bundle.putLong(CALL_SMS_LOG_INTERVAL_KEY, config.getLong(CALL_SMS_LOG_INTERVAL, CALL_SMS_LOG_INTERVAL_DEFAULT));
    }

    @Override
    public List<String> needsPermissions() {
        return Arrays.asList(READ_CALL_LOG, READ_SMS);
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }
}
