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

import org.radarcns.android.device.DeviceTopics;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;

public class PhoneBluetoothTopics extends DeviceTopics {
    private final AvroTopic<MeasurementKey, PhoneBluetoothDevices> bluetoothDevicesTopic;

    private static volatile PhoneBluetoothTopics instance;

    private PhoneBluetoothTopics() {
        bluetoothDevicesTopic= createTopic("android_phone_bluetooth_devices",
                PhoneBluetoothDevices.getClassSchema(),
                PhoneBluetoothDevices.class);
    }

    public static PhoneBluetoothTopics getInstance() {
        if (instance == null) {
            synchronized (PhoneBluetoothTopics.class) {
                if (instance == null) {
                    instance = new PhoneBluetoothTopics();
                }
            }
        }
        return instance;
    }

    public AvroTopic<MeasurementKey, PhoneBluetoothDevices> getBluetoothDevicesTopic() {
        return bluetoothDevicesTopic;
    }
}
