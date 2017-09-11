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
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Collections;
import java.util.List;

public class PhoneSensorProvider extends DeviceServiceProvider<PhoneState> {
    static final int PHONE_SENSOR_INTERVAL_DEFAULT = 200;
    static final String PHONE_SENSOR_INTERVAL = "phone_sensor_default_interval";
    static final String PHONE_SENSOR_GYROSCOPE_INTERVAL = "phone_sensor_gyroscope_interval";
    static final String PHONE_SENSOR_MAGNETIC_FIELD_INTERVAL = "phone_sensor_magneticfield_interval";
    static final String PHONE_SENSOR_STEP_COUNT_INTERVAL = "phone_sensor_steps_interval";
    static final String PHONE_SENSOR_ACCELERATION_INTERVAL = "phone_sensor_acceleration_interval";
    static final String PHONE_SENSOR_LIGHT_INTERVAL = "phone_sensor_light_interval";

    @Override
    public Class<?> getServiceClass() {
        return PhoneSensorService.class;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.phoneServiceDisplayName);
    }

    @Override
    protected void configure(Bundle bundle) {
        super.configure(bundle);
        RadarConfiguration config = getConfig();
        int defaultInterval = config.getInt(PHONE_SENSOR_INTERVAL, PHONE_SENSOR_INTERVAL_DEFAULT);
        bundle.putInt(PHONE_SENSOR_INTERVAL, defaultInterval);
        bundle.putInt(PHONE_SENSOR_GYROSCOPE_INTERVAL, getConfig().getInt(PHONE_SENSOR_GYROSCOPE_INTERVAL, defaultInterval));
        bundle.putInt(PHONE_SENSOR_MAGNETIC_FIELD_INTERVAL, getConfig().getInt(PHONE_SENSOR_MAGNETIC_FIELD_INTERVAL, defaultInterval));
        bundle.putInt(PHONE_SENSOR_STEP_COUNT_INTERVAL, getConfig().getInt(PHONE_SENSOR_STEP_COUNT_INTERVAL, defaultInterval));
        bundle.putInt(PHONE_SENSOR_ACCELERATION_INTERVAL, getConfig().getInt(PHONE_SENSOR_ACCELERATION_INTERVAL, defaultInterval));
        bundle.putInt(PHONE_SENSOR_LIGHT_INTERVAL, getConfig().getInt(PHONE_SENSOR_LIGHT_INTERVAL, defaultInterval));
    }

    @Override
    public List<String> needsPermissions() {
        return Collections.emptyList();
    }
}
