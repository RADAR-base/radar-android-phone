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
import android.os.Parcelable;

import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Arrays;
import java.util.List;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class PhoneLocationProvider extends DeviceServiceProvider<BaseDeviceState> {
    static final String PHONE_LOCATION_GPS_INTERVAL = "phone.location.gps.interval";
    static final String PHONE_LOCATION_GPS_INTERVAL_REDUCED = "phone.location.gps.interval.reduced";
    static final String PHONE_LOCATION_NETWORK_INTERVAL = "phone.location.network.interval";
    static final String PHONE_LOCATION_NETWORK_INTERVAL_REDUCED = "phone.location.network.interval.reduced";
    static final String PHONE_LOCATION_BATTERY_LEVEL_REDUCED = "phone.location.battery.level.reduced";
    static final String PHONE_LOCATION_BATTERY_LEVEL_MINIMUM = "phone.location.battery.level.minimum";

    private static final int LOCATION_GPS_INTERVAL_DEFAULT = 60*60; // seconds
    private static final int LOCATION_GPS_INTERVAL_REDUCED_DEFAULT = 5 * LOCATION_GPS_INTERVAL_DEFAULT; // seconds
    private static final int LOCATION_NETWORK_INTERVAL_DEFAULT = 10*60; // seconds
    private static final int LOCATION_NETWORK_INTERVAL_REDUCED_DEFAULT = 5 * LOCATION_NETWORK_INTERVAL_DEFAULT; // seconds

    private static final float MINIMUM_BATTERY_LEVEL_DEFAULT = 0.15f;
    private static final float REDUCED_BATTERY_LEVEL_DEFAULT = 0.3f;

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
    protected void configure(Bundle bundle) {
        super.configure(bundle);
        RadarConfiguration config = getConfig();

        bundle.putInt(RadarConfiguration.RADAR_PREFIX + PHONE_LOCATION_GPS_INTERVAL, config.getInt(PHONE_LOCATION_GPS_INTERVAL, LOCATION_GPS_INTERVAL_DEFAULT));
        bundle.putInt(RadarConfiguration.RADAR_PREFIX + PHONE_LOCATION_GPS_INTERVAL_REDUCED, config.getInt(PHONE_LOCATION_GPS_INTERVAL_REDUCED, LOCATION_GPS_INTERVAL_REDUCED_DEFAULT));
        bundle.putInt(RadarConfiguration.RADAR_PREFIX + PHONE_LOCATION_NETWORK_INTERVAL, config.getInt(PHONE_LOCATION_NETWORK_INTERVAL, LOCATION_NETWORK_INTERVAL_DEFAULT));
        bundle.putInt(RadarConfiguration.RADAR_PREFIX + PHONE_LOCATION_NETWORK_INTERVAL_REDUCED, config.getInt(PHONE_LOCATION_NETWORK_INTERVAL_REDUCED, LOCATION_NETWORK_INTERVAL_REDUCED_DEFAULT));
        bundle.putFloat(RadarConfiguration.RADAR_PREFIX + PHONE_LOCATION_BATTERY_LEVEL_REDUCED, config.getFloat(PHONE_LOCATION_BATTERY_LEVEL_REDUCED, REDUCED_BATTERY_LEVEL_DEFAULT));
        bundle.putFloat(RadarConfiguration.RADAR_PREFIX + PHONE_LOCATION_BATTERY_LEVEL_MINIMUM, config.getFloat(PHONE_LOCATION_BATTERY_LEVEL_MINIMUM, MINIMUM_BATTERY_LEVEL_DEFAULT));
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
