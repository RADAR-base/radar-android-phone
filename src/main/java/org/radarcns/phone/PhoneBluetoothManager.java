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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import org.radarcns.android.data.DataCache;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.key.MeasurementKey;

import java.io.IOException;
import java.util.Set;

public class PhoneBluetoothManager extends AbstractDeviceManager<PhoneBluetoothService, BaseDeviceState> implements Runnable {
    private static final int SCAN_DEVICES_REQUEST_CODE = 3248902;
    private static final String ACTION_SCAN_DEVICES = "org.radarcns.phone.PhoneBluetoothManager.ACTION_SCAN_DEVICES";
    private final OfflineProcessor processor;
    private final DataCache<MeasurementKey, PhoneBluetoothDevices> bluetoothDevicesTable;

    public PhoneBluetoothManager(PhoneBluetoothService service) {
        super(service, service.getDefaultState(), service.getDataHandler(), service.getUserId(), service.getSourceId());

        processor = new OfflineProcessor(service, this, SCAN_DEVICES_REQUEST_CODE,
                ACTION_SCAN_DEVICES, service.getCheckInterval(), true);

        bluetoothDevicesTable =  getCache(service.getTopics().getBluetoothDevicesTopic());
    }


    @Override
    public void run() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        final boolean wasEnabled = bluetoothAdapter.isEnabled();

        if (!wasEnabled) {
            bluetoothAdapter.enable();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
            private int numberOfDevices;

            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case BluetoothDevice.ACTION_FOUND: {
                        numberOfDevices++;
                        break;
                    }

                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED: {
                        getService().unregisterReceiver(this);

                        int bondedDevices = bluetoothAdapter.getBondedDevices().size();

                        if (!wasEnabled) {
                            bluetoothAdapter.disable();
                        }

                        if (!isClosed()) {
                            double now = System.currentTimeMillis() / 1000.0;
                            send(bluetoothDevicesTable,
                                    new PhoneBluetoothDevices(now, now, bondedDevices, numberOfDevices, wasEnabled));
                        }
                        break;
                    }
                }
            }
        };

        getService().registerReceiver(bluetoothReceiver, filter);
        bluetoothAdapter.startDiscovery();
    }

    @Override
    public void start(@NonNull Set<String> set) {
        processor.start();
        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    @Override
    public void close() throws IOException {
        processor.close();
        super.close();
    }

    void setCheckInterval(long checkInterval) {
        processor.setInterval(checkInterval);
    }
}
