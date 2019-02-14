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

import android.support.annotation.NonNull;

import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;

public class PhoneLocationService extends DeviceService<BaseDeviceState> {
    private static final String PHONE_LOCATION_GPS_INTERVAL = "phone_location_gps_interval";
    private static final String PHONE_LOCATION_GPS_INTERVAL_REDUCED = "phone_location_gps_interval_reduced";
    private static final String PHONE_LOCATION_NETWORK_INTERVAL = "phone_location_network_interval";
    private static final String PHONE_LOCATION_NETWORK_INTERVAL_REDUCED = "phone_location_network_interval_reduced";
    private static final String PHONE_LOCATION_BATTERY_LEVEL_REDUCED = "phone_location_battery_level_reduced";
    private static final String PHONE_LOCATION_BATTERY_LEVEL_MINIMUM = "phone_location_battery_level_minimum";

    private static final int LOCATION_GPS_INTERVAL_DEFAULT = 15*60; // seconds
    private static final int LOCATION_GPS_INTERVAL_REDUCED_DEFAULT = 4 * LOCATION_GPS_INTERVAL_DEFAULT; // seconds
    private static final int LOCATION_NETWORK_INTERVAL_DEFAULT = 5*60; // seconds
    private static final int LOCATION_NETWORK_INTERVAL_REDUCED_DEFAULT = 4 * LOCATION_NETWORK_INTERVAL_DEFAULT; // seconds

    private static final float MINIMUM_BATTERY_LEVEL_DEFAULT = 0.15f;
    private static final float REDUCED_BATTERY_LEVEL_DEFAULT = 0.3f;

    @Override
    protected PhoneLocationManager createDeviceManager() {
        return new PhoneLocationManager(this);
    }

    @Override
    protected void configureDeviceManager(DeviceManager<BaseDeviceState> manager, RadarConfiguration config) {
        PhoneLocationManager phoneManager = (PhoneLocationManager) manager;
        phoneManager.setBatteryLevels(
                config.getFloat(PHONE_LOCATION_BATTERY_LEVEL_MINIMUM, MINIMUM_BATTERY_LEVEL_DEFAULT),
                config.getFloat(PHONE_LOCATION_BATTERY_LEVEL_REDUCED, REDUCED_BATTERY_LEVEL_DEFAULT));
        phoneManager.setIntervals(
                config.getInt(PHONE_LOCATION_GPS_INTERVAL, LOCATION_GPS_INTERVAL_DEFAULT),
                config.getInt(PHONE_LOCATION_GPS_INTERVAL_REDUCED, LOCATION_GPS_INTERVAL_REDUCED_DEFAULT),
                config.getInt(PHONE_LOCATION_NETWORK_INTERVAL, LOCATION_NETWORK_INTERVAL_DEFAULT),
                config.getInt(PHONE_LOCATION_NETWORK_INTERVAL_REDUCED, LOCATION_NETWORK_INTERVAL_REDUCED_DEFAULT));
    }

    @NonNull
    @Override
    protected BaseDeviceState getDefaultState() {
        return new BaseDeviceState();
    }

    @Override
    protected boolean isBluetoothConnectionRequired() {
        return false;
    }
}
