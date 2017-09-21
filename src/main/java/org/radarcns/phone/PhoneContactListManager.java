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

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import avro.shaded.com.google.common.collect.Sets;
import org.radarcns.android.data.DataCache;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.key.MeasurementKey;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PhoneContactListManager extends AbstractDeviceManager<PhoneContactsListService, BaseDeviceState> implements Runnable {
    private static final int CONTACTS_LIST_UPDATE_REQUEST_CODE = 15765692;
    private static final String ACTION_UPDATE_CONTACTS_LIST = "org.radarcns.phone.PhoneContactListManager.ACTION_UPDATE_CONTACTS_LIST";
    private static final String[] PROJECTION = {BaseColumns._ID};
    public static final String CONTACT_IDS = "contact_ids";

    private final SharedPreferences preferences;
    private final OfflineProcessor processor;
    private final DataCache<MeasurementKey, PhoneContactList> contactsTable;
    private Set<String> savedContactIds;

    public PhoneContactListManager(PhoneContactsListService service) {
        super(service, service.getDefaultState(), service.getDataHandler(), service.getUserId(), service.getSourceId());

        preferences = service.getSharedPreferences(PhoneContactListManager.class.getName(), Context.MODE_PRIVATE);
        contactsTable = getCache(service.getTopics().getContactListTopic());

        processor = new OfflineProcessor(service, this, CONTACTS_LIST_UPDATE_REQUEST_CODE,
                ACTION_UPDATE_CONTACTS_LIST, service.getCheckInterval(), false);
    }

    @Override
    public void start(@NonNull Set<String> set) {
        savedContactIds = preferences.getStringSet(CONTACT_IDS, Collections.<String>emptySet());
        processor.start();

        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    @Override
    public void close() throws IOException {
        processor.close();
        super.close();
    }

    @Override
    public void run() {
        Set<String> newContactIds = getContactIds();

        Integer added = null;
        Integer removed = null;


        if (savedContactIds != null) {
            added = Sets.difference(newContactIds, savedContactIds).size();
            removed = Sets.difference(savedContactIds, newContactIds).size();
        }

        savedContactIds = newContactIds;
        preferences.edit().putStringSet(CONTACT_IDS, savedContactIds).apply();

        double timestamp = System.currentTimeMillis() / 1000.0;
        send(contactsTable, new PhoneContactList(timestamp, timestamp, added, removed, newContactIds.size()));
    }

    private Set<String> getContactIds() {
        Set<String> contactIds = new HashSet<>();

        try (Cursor cursor = getService().getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                PROJECTION, null, null, null)) {
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    contactIds.add(cursor.getString(0));
                    cursor.moveToNext();
                }
            }
        }

        return contactIds;
    }

    void setCheckInterval(long checkInterval) {
        processor.setInterval(checkInterval);
    }
}
