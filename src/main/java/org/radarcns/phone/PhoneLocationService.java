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
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;

import static org.radarcns.phone.PhoneLocationProvider.INTERVAL_GPS_KEY;
import static org.radarcns.phone.PhoneLocationProvider.INTERVAL_GPS_REDUCED_KEY;
import static org.radarcns.phone.PhoneLocationProvider.INTERVAL_NETWORK_KEY;
import static org.radarcns.phone.PhoneLocationProvider.INTERVAL_NETWORK_REDUCED_KEY;
import static org.radarcns.phone.PhoneLocationProvider.MINIMUM_BATTERY_LEVEL_KEY;
import static org.radarcns.phone.PhoneLocationProvider.REDUCED_BATTERY_LEVEL_KEY;

public class PhoneLocationService extends DeviceService<BaseDeviceState> {
    private int gpsInterval;
    private int gpsIntervalReduced;
    private int networkInterval;
    private int networkIntervalReduced;
    private float batteryLevelMinimum;
    private float batteryLevelReduced;

    @Override
    protected PhoneLocationManager createDeviceManager() {
        PhoneLocationManager manager = new PhoneLocationManager(this);
        configureManager(manager);
        return manager;
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        return new BaseDeviceState();
    }

    @Override
    protected PhoneLocationTopics getTopics() {
        return PhoneLocationTopics.getInstance();
    }

    private void configureManager(PhoneLocationManager manager) {
        manager.setBatteryLevels(batteryLevelMinimum, batteryLevelReduced);
        manager.setIntervals(gpsInterval, gpsIntervalReduced, networkInterval, networkIntervalReduced);
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        gpsInterval = bundle.getInt(INTERVAL_GPS_KEY);
        gpsIntervalReduced = bundle.getInt(INTERVAL_GPS_REDUCED_KEY);
        networkInterval = bundle.getInt(INTERVAL_NETWORK_KEY);
        networkIntervalReduced = bundle.getInt(INTERVAL_NETWORK_REDUCED_KEY);
        batteryLevelMinimum = bundle.getFloat(MINIMUM_BATTERY_LEVEL_KEY);
        batteryLevelReduced = bundle.getFloat(REDUCED_BATTERY_LEVEL_KEY);
        DeviceManager manager = getDeviceManager();
        if (manager != null) {
            configureManager((PhoneLocationManager) getDeviceManager());
        }
    }
}
