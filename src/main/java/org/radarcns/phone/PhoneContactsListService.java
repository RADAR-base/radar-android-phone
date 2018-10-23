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
import android.support.annotation.NonNull;

import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceService;

import java.util.concurrent.TimeUnit;

import static org.radarcns.phone.PhoneContactListProvider.PHONE_CONTACTS_LIST_INTERVAL_DEFAULT;
import static org.radarcns.phone.PhoneContactListProvider.PHONE_CONTACTS_LIST_INTERVAL_KEY;

public class PhoneContactsListService extends DeviceService<BaseDeviceState> {
    private volatile long checkInterval = PHONE_CONTACTS_LIST_INTERVAL_DEFAULT;

    @Override
    protected PhoneContactListManager createDeviceManager() {
        return new PhoneContactListManager(this);
    }

    @Override
    protected BaseDeviceState getDefaultState() {
        return new BaseDeviceState();
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    @Override
    protected void onInvocation(@NonNull Bundle bundle) {
        super.onInvocation(bundle);
        checkInterval = bundle.getLong(PHONE_CONTACTS_LIST_INTERVAL_KEY);

        PhoneContactListManager manager = (PhoneContactListManager) getDeviceManager();
        if (manager != null) {
            manager.setCheckInterval(checkInterval, TimeUnit.SECONDS);
        }
    }

    @Override
    protected boolean isBluetoothConnectionRequired() {
        return false;
    }
}
