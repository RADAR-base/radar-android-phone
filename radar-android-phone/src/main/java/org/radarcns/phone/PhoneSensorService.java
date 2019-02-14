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

import android.hardware.Sensor;
import android.support.annotation.NonNull;
import android.util.SparseIntArray;

import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneSensorService extends DeviceService<PhoneState> {
    static final int PHONE_SENSOR_INTERVAL_DEFAULT = 200;
    static final int PHONE_SENSOR_BATTERY_INTERVAL_DEFAULT_SECONDS = 600;
    static final String PHONE_SENSOR_INTERVAL = "phone_sensor_default_interval";
    static final String PHONE_SENSOR_GYROSCOPE_INTERVAL = "phone_sensor_gyroscope_interval";
    static final String PHONE_SENSOR_MAGNETIC_FIELD_INTERVAL = "phone_sensor_magneticfield_interval";
    static final String PHONE_SENSOR_STEP_COUNT_INTERVAL = "phone_sensor_steps_interval";
    static final String PHONE_SENSOR_ACCELERATION_INTERVAL = "phone_sensor_acceleration_interval";
    static final String PHONE_SENSOR_LIGHT_INTERVAL = "phone_sensor_light_interval";
    static final String PHONE_SENSOR_BATTERY_INTERVAL_SECONDS = "phone_sensor_battery_interval_seconds";

    private static final Logger logger = LoggerFactory.getLogger(PhoneSensorService.class);

    private SparseIntArray sensorDelays;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorDelays = new SparseIntArray(5);
    }

    @Override
    protected PhoneSensorManager createDeviceManager() {
        logger.info("Creating PhoneSensorManager");
        return new PhoneSensorManager(this);
    }

    @Override
    protected void configureDeviceManager(DeviceManager<PhoneState> manager, RadarConfiguration config) {
        PhoneSensorManager phoneManager = (PhoneSensorManager) manager;

        int defaultInterval = config.getInt(PHONE_SENSOR_INTERVAL, PHONE_SENSOR_INTERVAL_DEFAULT);

        sensorDelays.put(Sensor.TYPE_ACCELEROMETER, config.getInt(PHONE_SENSOR_ACCELERATION_INTERVAL, defaultInterval));
        sensorDelays.put(Sensor.TYPE_MAGNETIC_FIELD, config.getInt(PHONE_SENSOR_MAGNETIC_FIELD_INTERVAL, defaultInterval));
        sensorDelays.put(Sensor.TYPE_GYROSCOPE, config.getInt(PHONE_SENSOR_GYROSCOPE_INTERVAL, defaultInterval));
        sensorDelays.put(Sensor.TYPE_LIGHT, config.getInt(PHONE_SENSOR_LIGHT_INTERVAL, defaultInterval));
        sensorDelays.put(Sensor.TYPE_STEP_COUNTER, config.getInt(PHONE_SENSOR_STEP_COUNT_INTERVAL, defaultInterval));

        phoneManager.setSensorDelays(sensorDelays);
        phoneManager.setBatteryUpdateInterval(
                config.getInt(PHONE_SENSOR_BATTERY_INTERVAL_SECONDS, PHONE_SENSOR_BATTERY_INTERVAL_DEFAULT_SECONDS),
                TimeUnit.SECONDS);

    }

    @NonNull
    @Override
    protected PhoneState getDefaultState() {
        return new PhoneState();
    }
}
