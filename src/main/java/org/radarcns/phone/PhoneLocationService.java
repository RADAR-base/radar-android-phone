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
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;

import static org.radarcns.android.RadarConfiguration.SOURCE_ID_KEY;
import static org.radarcns.phone.PhoneLocationProvider.PHONE_LOCATION_BATTERY_LEVEL_MINIMUM;
import static org.radarcns.phone.PhoneLocationProvider.PHONE_LOCATION_BATTERY_LEVEL_REDUCED;
import static org.radarcns.phone.PhoneLocationProvider.PHONE_LOCATION_GPS_INTERVAL;
import static org.radarcns.phone.PhoneLocationProvider.PHONE_LOCATION_GPS_INTERVAL_REDUCED;
import static org.radarcns.phone.PhoneLocationProvider.PHONE_LOCATION_NETWORK_INTERVAL;
import static org.radarcns.phone.PhoneLocationProvider.PHONE_LOCATION_NETWORK_INTERVAL_REDUCED;

public class PhoneLocationService extends DeviceService {
    private String sourceId;
    private int gpsInterval;
    private int gpsIntervalReduced;
    private int networkInterval;
    private int networkIntervalReduced;
    private float batteryLevelMinimum;
    private float batteryLevelReduced;

    @Override
    protected DeviceManager createDeviceManager() {
        PhoneLocationManager manager = new PhoneLocationManager(this, getDataHandler(), getUserId(), getSourceId());
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

    public String getSourceId() {
        if (sourceId == null) {
            sourceId = RadarConfiguration.getOrSetUUID(getApplicationContext(), SOURCE_ID_KEY);
        }
        return sourceId;
    }

    private void configureManager(PhoneLocationManager manager) {
        manager.setBatteryLevels(batteryLevelMinimum, batteryLevelReduced);
        manager.setIntervals(gpsInterval, gpsIntervalReduced, networkInterval, networkIntervalReduced);
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        gpsInterval = bundle.getInt(PHONE_LOCATION_GPS_INTERVAL);
        gpsIntervalReduced = bundle.getInt(PHONE_LOCATION_GPS_INTERVAL_REDUCED);
        networkInterval = bundle.getInt(PHONE_LOCATION_NETWORK_INTERVAL);
        networkIntervalReduced = bundle.getInt(PHONE_LOCATION_NETWORK_INTERVAL_REDUCED);
        batteryLevelMinimum = bundle.getInt(PHONE_LOCATION_BATTERY_LEVEL_MINIMUM);
        batteryLevelReduced = bundle.getFloat(PHONE_LOCATION_BATTERY_LEVEL_REDUCED);
        DeviceManager manager = getDeviceManager();
        if (manager != null) {
            configureManager((PhoneLocationManager) getDeviceManager());
        }
    }
}
