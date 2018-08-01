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
import android.os.Bundle;
import android.util.SparseIntArray;
import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.device.DeviceService;
import org.radarcns.kafka.ObservationKey;
import org.radarcns.topic.AvroTopic;

import java.util.Arrays;
import java.util.List;

import static org.radarcns.phone.PhoneSensorProvider.PHONE_SENSOR_ACCELERATION_INTERVAL;
import static org.radarcns.phone.PhoneSensorProvider.PHONE_SENSOR_GYROSCOPE_INTERVAL;
import static org.radarcns.phone.PhoneSensorProvider.PHONE_SENSOR_LIGHT_INTERVAL;
import static org.radarcns.phone.PhoneSensorProvider.PHONE_SENSOR_MAGNETIC_FIELD_INTERVAL;
import static org.radarcns.phone.PhoneSensorProvider.PHONE_SENSOR_STEP_COUNT_INTERVAL;
import static org.radarcns.phone.PhoneSensorProvider.PHONE_SENSOR_BATTERY_INTERVAL;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneSensorService extends DeviceService<PhoneState> {
    private SparseIntArray sensorDelays;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorDelays = new SparseIntArray(5);
    }

    @Override
    protected PhoneSensorManager createDeviceManager() {
        PhoneSensorManager manager = new PhoneSensorManager(this);
        manager.setSensorDelays(sensorDelays);
        return manager;
    }

    @Override
    protected PhoneState getDefaultState() {
        return new PhoneState();
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        sensorDelays.put(Sensor.TYPE_ACCELEROMETER, bundle.getInt(PHONE_SENSOR_ACCELERATION_INTERVAL));
        sensorDelays.put(Sensor.TYPE_MAGNETIC_FIELD, bundle.getInt(PHONE_SENSOR_MAGNETIC_FIELD_INTERVAL));
        sensorDelays.put(Sensor.TYPE_GYROSCOPE, bundle.getInt(PHONE_SENSOR_GYROSCOPE_INTERVAL));
        sensorDelays.put(Sensor.TYPE_LIGHT, bundle.getInt(PHONE_SENSOR_LIGHT_INTERVAL));
        sensorDelays.put(Sensor.TYPE_STEP_COUNTER, bundle.getInt(PHONE_SENSOR_STEP_COUNT_INTERVAL));
        PhoneSensorManager manager = (PhoneSensorManager) getDeviceManager();
        if (manager != null) {
            manager.setSensorDelays(sensorDelays);
            manager.setBatteryUpdateInterval(bundle.getInt(PHONE_SENSOR_BATTERY_INTERVAL));
        }
    }

    @Override
    protected boolean isBluetoothConnectionRequired() {
        return false;
    }
}
