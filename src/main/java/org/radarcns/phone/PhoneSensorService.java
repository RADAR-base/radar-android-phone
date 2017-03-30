package org.radarcns.phone;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.util.PersistentStorage;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;

import java.util.Arrays;
import java.util.List;

import static org.radarcns.android.RadarConfiguration.SOURCE_ID_KEY;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneSensorService extends DeviceService {
    private String sourceId;

    @Override
    protected DeviceManager createDeviceManager() {
        return new PhoneSensorManager(this, getDataHandler(), getUserId(), getSourceId());
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        PhoneState newStatus = new PhoneState();
        newStatus.setStatus(DeviceStatusListener.Status.DISCONNECTED);
        return newStatus;
    }

    @Override
    protected PhoneSensorTopics getTopics() {
        return PhoneSensorTopics.getInstance();
    }

    @Override
    protected List<AvroTopic<MeasurementKey, ? extends SpecificRecord>> getCachedTopics() {
        return Arrays.<AvroTopic<MeasurementKey, ? extends SpecificRecord>>asList(
                getTopics().getAccelerationTopic(), getTopics().getLightTopic());
    }

    public String getSourceId() {
        if (sourceId == null) {
            sourceId = new PersistentStorage(getClass()).loadOrStoreUUID(SOURCE_ID_KEY);
        }
        return sourceId;
    }
}
