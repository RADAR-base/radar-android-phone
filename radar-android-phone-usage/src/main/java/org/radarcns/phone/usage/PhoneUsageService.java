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

package org.radarcns.phone.usage;

import android.support.annotation.NonNull;

import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;

import java.util.concurrent.TimeUnit;

/**
 * A service that manages the phone sensor manager and a TableDataHandler to send store the data of
 * the phone sensors and send it to a Kafka REST proxy.
 */
public class PhoneUsageService extends DeviceService<BaseDeviceState> {
    private static final String PHONE_USAGE_INTERVAL = "phone_usage_interval_seconds";
    static final long USAGE_EVENT_PERIOD_DEFAULT = 60*60; // one hour

    @Override
    protected PhoneUsageManager createDeviceManager() {
        return new PhoneUsageManager(this);
    }

    @Override
    protected void configureDeviceManager(DeviceManager<BaseDeviceState> manager, RadarConfiguration config) {
        PhoneUsageManager phoneManager = (PhoneUsageManager) manager;
        phoneManager.setUsageEventUpdateRate(
                config.getLong(PHONE_USAGE_INTERVAL, USAGE_EVENT_PERIOD_DEFAULT),
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
