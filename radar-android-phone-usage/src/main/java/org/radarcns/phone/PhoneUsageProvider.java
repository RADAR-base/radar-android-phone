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
import android.support.annotation.NonNull;

import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceServiceProvider;
import org.radarcns.phone.usage.BuildConfig;
import org.radarcns.phone.usage.PhoneUsageService;
import org.radarcns.phone.usage.R;

import java.util.Collections;
import java.util.List;

public class PhoneUsageProvider extends DeviceServiceProvider<BaseDeviceState> {
    public static final String DEVICE_PRODUCER = "ANDROID";
    public static final String DEVICE_MODEL = "PHONE";

    @Override
    public String getDescription() {
        return getRadarService().getString(R.string.phone_usage_description);
    }

    @Override
    public Class<?> getServiceClass() {
        return PhoneUsageService.class;
    }

    @Override
    public String getDisplayName() {
        return getRadarService().getString(R.string.phoneUsageServiceDisplayName);
    }

    @NonNull
    @Override
    public List<String> needsPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Collections.singletonList(android.Manifest.permission.PACKAGE_USAGE_STATS);
        } else {
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public String getDeviceProducer() {
        return DEVICE_PRODUCER;
    }

    @NonNull
    @Override
    public String getDeviceModel() {
        return DEVICE_MODEL;
    }

    @NonNull
    @Override
    public String getVersion() {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }
}
