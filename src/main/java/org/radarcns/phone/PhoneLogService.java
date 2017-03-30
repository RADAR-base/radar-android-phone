package org.radarcns.phone;

import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.util.PersistentStorage;

import static org.radarcns.android.RadarConfiguration.SOURCE_ID_KEY;

public class PhoneLogService extends DeviceService {
    private String sourceId;

    @Override
    protected DeviceManager createDeviceManager() {
        return new PhoneLogManager(this, getDataHandler(), getUserId(), getSourceId());
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        BaseDeviceState state = new BaseDeviceState();
        state.setStatus(DeviceStatusListener.Status.DISCONNECTED);
        return state;
    }

    @Override
    protected PhoneLogTopics getTopics() {
        return PhoneLogTopics.getInstance();
    }

    public String getSourceId() {
        if (sourceId == null) {
            sourceId = new PersistentStorage(getClass()).loadOrStoreUUID(SOURCE_ID_KEY);
        }
        return sourceId;
    }
}
