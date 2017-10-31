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
import static org.radarcns.phone.PhoneBluetoothProvider.BLUETOOTH_DEVICES_SCAN_INTERVAL_DEFAULT;
import static org.radarcns.phone.PhoneBluetoothProvider.PHONE_BLUETOOTH_DEVICES_SCAN_INTERVAL_KEY;

public class PhoneBluetoothService extends DeviceService<BaseDeviceState> {
    private long checkInterval = BLUETOOTH_DEVICES_SCAN_INTERVAL_DEFAULT;

    @Override
    protected PhoneBluetoothManager createDeviceManager() {
        return new PhoneBluetoothManager(this);
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        return new BaseDeviceState();
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    @Override
    protected void onInvocation(Bundle bundle) {
        super.onInvocation(bundle);
        checkInterval = bundle.getLong(PHONE_BLUETOOTH_DEVICES_SCAN_INTERVAL_KEY);

        PhoneBluetoothManager manager = (PhoneBluetoothManager) getDeviceManager();
        if (manager != null) {
            manager.setCheckInterval(checkInterval);
        }
    }

    @Override
    protected boolean isBluetoothConnectionRequired() {
        return false;
    }
}
