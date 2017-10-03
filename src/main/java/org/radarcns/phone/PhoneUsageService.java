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
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceService;

import static org.radarcns.phone.PhoneUsageProvider.PHONE_USAGE_INTERVAL_KEY;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneUsageService extends DeviceService<BaseDeviceState> {
    private long usageEventInterval;

    @Override
    protected PhoneUsageManager createDeviceManager() {
        return new PhoneUsageManager(this, usageEventInterval);
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        return new BaseDeviceState();
    }

    @Override
    protected PhoneUsageTopics getTopics() {
        return PhoneUsageTopics.getInstance();
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        usageEventInterval = bundle.getLong(PHONE_USAGE_INTERVAL_KEY);

        PhoneUsageManager manager = (PhoneUsageManager) getDeviceManager();
        if (manager != null) {
            manager.setUsageEventUpdateRate(usageEventInterval);
        }
    }
}
