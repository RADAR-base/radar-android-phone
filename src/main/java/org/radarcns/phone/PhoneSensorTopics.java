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
    private final AvroTopic<MeasurementKey, PhoneCall> callTopic;
    private final AvroTopic<MeasurementKey, PhoneSms> smsTopic;
    private final AvroTopic<MeasurementKey, PhoneLocation> locationTopic;
    private final AvroTopic<MeasurementKey, PhoneUserInteraction> interactionTopic;

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
        callTopic = createTopic("android_phone_call",
                PhoneCall.getClassSchema(),
                PhoneCall.class);
        smsTopic = createTopic("android_phone_sms",
                PhoneSms.getClassSchema(),
                PhoneSms.class);
        locationTopic = createTopic("android_phone_location",
                PhoneLocation.getClassSchema(),
                PhoneLocation.class);
        interactionTopic = createTopic("android_phone_user_interaction",
                PhoneUserInteraction.getClassSchema(),
                PhoneUserInteraction.class);
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

    public AvroTopic<MeasurementKey, PhoneCall> getCallTopic() {
        return callTopic;
    }

    public AvroTopic<MeasurementKey, PhoneSms> getSmsTopic() {
        return smsTopic;
    }

    public AvroTopic<MeasurementKey, PhoneLocation> getLocationTopic() {
        return locationTopic;
    }

    public AvroTopic<MeasurementKey, PhoneUserInteraction> getUserInteractionTopic() {
        return interactionTopic;
    }
}
