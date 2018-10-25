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

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.radarcns.phone.PhoneSensorProvider.DEVICE_MODEL;
import static org.radarcns.phone.PhoneSensorProvider.DEVICE_PRODUCER;

public class PhoneBluetoothProvider extends DeviceServiceProvider<BaseDeviceState> {
    private static final String PHONE_PREFIX = "org.radarcns.phone.";
    private static final String PHONE_BLUETOOTH_DEVICES_SCAN_INTERVAL = "bluetooth_devices_scan_interval_seconds";
    public static final String PHONE_BLUETOOTH_DEVICES_SCAN_INTERVAL_KEY =  PHONE_PREFIX + PHONE_BLUETOOTH_DEVICES_SCAN_INTERVAL;
    public static final long BLUETOOTH_DEVICES_SCAN_INTERVAL_DEFAULT = TimeUnit.HOURS.toSeconds(1);

    @Override
    public String getDescription() {
        return getRadarService().getString(R.string.phone_bluetooth_description);
    }

    @Override
    public Class<?> getServiceClass() {
        return PhoneBluetoothService.class;
    }

    @Override
    public String getDisplayName() {
        return getRadarService().getString(R.string.bluetooth_devices);
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }

    @NonNull
    @Override
    public List<String> needsPermissions() {
        return Collections.singletonList(Manifest.permission.BLUETOOTH_ADMIN);
    }

    @NonNull
    @Override
    public List<String> needsFeatures() {
        return Collections.singletonList(PackageManager.FEATURE_BLUETOOTH);
    }

    @Override
    protected void configure(Bundle bundle) {
        super.configure(bundle);
        RadarConfiguration config = getConfig();
        bundle.putLong(PHONE_BLUETOOTH_DEVICES_SCAN_INTERVAL_KEY, config.getLong(
                PHONE_BLUETOOTH_DEVICES_SCAN_INTERVAL, BLUETOOTH_DEVICES_SCAN_INTERVAL_DEFAULT));
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
}
