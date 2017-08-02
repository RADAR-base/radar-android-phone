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

import android.os.Bundle;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;

import static org.radarcns.android.RadarConfiguration.SOURCE_ID_KEY;
import static org.radarcns.phone.PhoneLogProvider.CALL_SMS_LOG_INTERVAL_KEY;

public class PhoneLogService extends DeviceService {
    private String sourceId;
    private long logInterval;

    @Override
    protected DeviceManager createDeviceManager() {
        if (sourceId == null) {
            sourceId = RadarConfiguration.getOrSetUUID(getApplicationContext(), SOURCE_ID_KEY);
        }
        return new PhoneLogManager(this, getDataHandler(), getUserId(), sourceId, logInterval);
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        return new BaseDeviceState();
    }

    @Override
    protected PhoneLogTopics getTopics() {
        return PhoneLogTopics.getInstance();
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        logInterval = bundle.getLong(CALL_SMS_LOG_INTERVAL_KEY);
        PhoneLogManager deviceManager = (PhoneLogManager) getDeviceManager();
        if (deviceManager != null) {
            deviceManager.setCallAndSmsLogUpdateRate(logInterval);
        }
    }
}
