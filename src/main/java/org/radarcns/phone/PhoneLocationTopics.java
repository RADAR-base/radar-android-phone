package org.radarcns.phone;

import org.radarcns.android.device.DeviceTopics;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;

/** Topic manager for topics concerning the Phone sensors. */
public class PhoneLocationTopics extends DeviceTopics {
    private static final Object syncObject = new Object();
    private static PhoneLocationTopics instance = null;

    private final AvroTopic<MeasurementKey, PhoneLocation> locationTopic;

    public static PhoneLocationTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new PhoneLocationTopics();
            }
            return instance;
        }
    }

    private PhoneLocationTopics() {
        locationTopic = createTopic("android_phone_location",
                PhoneLocation.getClassSchema(),
                PhoneLocation.class);
    }

    public AvroTopic<MeasurementKey, PhoneLocation> getLocationTopic() {
        return locationTopic;
    }
}
