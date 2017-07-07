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

/** Topic manager for topics concerning the Phone sensors. */
public class PhoneSensorTopics extends DeviceTopics {
    private static final Object syncObject = new Object();
    private static PhoneSensorTopics instance = null;

    private final AvroTopic<MeasurementKey, PhoneAcceleration> accelerationTopic;
    private final AvroTopic<MeasurementKey, PhoneBatteryLevel> batteryLevelTopic;
    private final AvroTopic<MeasurementKey, PhoneLight> lightTopic;
    private final AvroTopic<MeasurementKey, PhoneUserInteraction> interactionTopic;
    private final AvroTopic<MeasurementKey, PhoneStepCount> stepCountTopic;
    private final AvroTopic<MeasurementKey, PhoneGyroscope> gyroscopeTopic;
    private final AvroTopic<MeasurementKey, PhoneMagneticField> magneticFieldTopic;

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
        interactionTopic = createTopic("android_phone_user_interaction",
                PhoneUserInteraction.getClassSchema(),
                PhoneUserInteraction.class);
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

    public AvroTopic<MeasurementKey, PhoneAcceleration> getAccelerationTopic() {
        return accelerationTopic;
    }

    public AvroTopic<MeasurementKey, PhoneBatteryLevel> getBatteryLevelTopic() {
        return batteryLevelTopic;
    }

    public AvroTopic<MeasurementKey, PhoneLight> getLightTopic() {
        return lightTopic;
    }

    public AvroTopic<MeasurementKey, PhoneUserInteraction> getUserInteractionTopic() {
        return interactionTopic;
    }

    public AvroTopic<MeasurementKey, PhoneStepCount> getStepCountTopic() {
        return stepCountTopic;
    }

    public AvroTopic<MeasurementKey, PhoneGyroscope> getGyroscopeTopic() {
        return gyroscopeTopic;
    }

    public AvroTopic<MeasurementKey, PhoneMagneticField> getMagneticFieldTopic() {
        return magneticFieldTopic;
    }
}
