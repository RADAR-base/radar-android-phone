package org.radarcns.phone;

import org.radarcns.android.device.DeviceTopics;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;

/** Topic manager for topics concerning the Phone sensors. */
public class PhoneLogTopics extends DeviceTopics {
    private static final Object syncObject = new Object();
    private static PhoneLogTopics instance = null;

    private final AvroTopic<MeasurementKey, PhoneCall> callTopic;
    private final AvroTopic<MeasurementKey, PhoneSms> smsTopic;

    public static PhoneLogTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new PhoneLogTopics();
            }
            return instance;
        }
    }

    private PhoneLogTopics() {
        callTopic = createTopic("android_phone_call",
                PhoneCall.getClassSchema(),
                PhoneCall.class);
        smsTopic = createTopic("android_phone_sms",
                PhoneSms.getClassSchema(),
                PhoneSms.class);
    }

    public AvroTopic<MeasurementKey, PhoneCall> getCallTopic() {
        return callTopic;
    }

    public AvroTopic<MeasurementKey, PhoneSms> getSmsTopic() {
        return smsTopic;
    }
}
