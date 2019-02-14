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

import android.support.annotation.NonNull;

import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;

import java.util.concurrent.TimeUnit;

public class PhoneContactsListService extends DeviceService<BaseDeviceState> {
    private static final String PHONE_CONTACTS_LIST_INTERVAL = "phone_contacts_list_interval_seconds";
    public static final long PHONE_CONTACTS_LIST_INTERVAL_DEFAULT = TimeUnit.DAYS.toSeconds(1);

    @Override
    protected PhoneContactListManager createDeviceManager() {
        return new PhoneContactListManager(this);
    }

    @Override
    protected void configureDeviceManager(DeviceManager<BaseDeviceState> manager, RadarConfiguration config) {
        PhoneContactListManager phoneManager = (PhoneContactListManager) manager;
        phoneManager.setCheckInterval(
                config.getLong(PHONE_CONTACTS_LIST_INTERVAL,
                        PHONE_CONTACTS_LIST_INTERVAL_DEFAULT),
                TimeUnit.SECONDS);
    }

    @NonNull
    @Override
    protected BaseDeviceState getDefaultState() {
        return new BaseDeviceState();
    }

    @Override
    protected boolean isBluetoothConnectionRequired() {
        return false;
    }
}
