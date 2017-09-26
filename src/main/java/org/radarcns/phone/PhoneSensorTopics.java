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
import org.radarcns.kafka.ObservationKey;
import org.radarcns.passive.phone.PhoneAcceleration;
import org.radarcns.passive.phone.PhoneBatteryLevel;
import org.radarcns.passive.phone.PhoneGyroscope;
import org.radarcns.passive.phone.PhoneLight;
import org.radarcns.passive.phone.PhoneMagneticField;
import org.radarcns.passive.phone.PhoneStepCount;
import org.radarcns.topic.AvroTopic;

/** Topic manager for topics concerning the Phone sensors. */
public class PhoneSensorTopics extends DeviceTopics {
    private static final Object syncObject = new Object();
    private static PhoneSensorTopics instance = null;

    private final AvroTopic<ObservationKey, PhoneAcceleration> accelerationTopic;
    private final AvroTopic<ObservationKey, PhoneBatteryLevel> batteryLevelTopic;
    private final AvroTopic<ObservationKey, PhoneLight> lightTopic;
    private final AvroTopic<ObservationKey, PhoneStepCount> stepCountTopic;
    private final AvroTopic<ObservationKey, PhoneGyroscope> gyroscopeTopic;
    private final AvroTopic<ObservationKey, PhoneMagneticField> magneticFieldTopic;

    public static PhoneSensorTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new PhoneSensorTopics();
            }
            return instance;
        }
    }

    private PhoneSensorTopics() {
        accelerationTopic = createTopic("android_phone_acceleration",
                PhoneAcceleration.getClassSchema(),
                PhoneAcceleration.class);
        batteryLevelTopic = createTopic("android_phone_battery_level",
                PhoneBatteryLevel.getClassSchema(),
                PhoneBatteryLevel.class);
        lightTopic = createTopic("android_phone_light",
                PhoneLight.getClassSchema(),
                PhoneLight.class);
        stepCountTopic = createTopic("android_phone_step_count",
                PhoneStepCount.getClassSchema(),
                PhoneStepCount.class);
        gyroscopeTopic = createTopic("android_phone_gyroscope",
                PhoneGyroscope.getClassSchema(),
                PhoneGyroscope.class);
        magneticFieldTopic = createTopic("android_phone_magnetic_field",
                PhoneMagneticField.getClassSchema(),
                PhoneMagneticField.class);
    }

    public AvroTopic<ObservationKey, PhoneAcceleration> getAccelerationTopic() {
        return accelerationTopic;
    }

    public AvroTopic<ObservationKey, PhoneBatteryLevel> getBatteryLevelTopic() {
        return batteryLevelTopic;
    }

    public AvroTopic<ObservationKey, PhoneLight> getLightTopic() {
        return lightTopic;
    }

    public AvroTopic<ObservationKey, PhoneStepCount> getStepCountTopic() {
        return stepCountTopic;
    }

    public AvroTopic<ObservationKey, PhoneGyroscope> getGyroscopeTopic() {
        return gyroscopeTopic;
    }

    public AvroTopic<ObservationKey, PhoneMagneticField> getMagneticFieldTopic() {
        return magneticFieldTopic;
    }
}
