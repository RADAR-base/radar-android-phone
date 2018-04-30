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
import android.provider.ContactsContract;
import android.support.annotation.NonNull;

import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.util.OfflineProcessor;
import org.radarcns.kafka.ObservationKey;
import org.radarcns.passive.phone.PhoneContactList;
import org.radarcns.topic.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static android.provider.ContactsContract.ContactsColumns.LOOKUP_KEY;

public class PhoneContactListManager extends AbstractDeviceManager<PhoneContactsListService, BaseDeviceState> implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(PhoneContactListManager.class);

    private static final int CONTACTS_LIST_UPDATE_REQUEST_CODE = 15765692;
    private static final String ACTION_UPDATE_CONTACTS_LIST = "org.radarcns.phone.PhoneContactListManager.ACTION_UPDATE_CONTACTS_LIST";
    private static final String[] LOOKUP_COLUMNS = {LOOKUP_KEY};
    public static final String CONTACT_IDS = "contact_ids";
    public static final String CONTACT_LOOKUPS = "contact_lookups";

    private final SharedPreferences preferences;
    private final OfflineProcessor processor;
    private final AvroTopic<ObservationKey, PhoneContactList> contactsTopic;
    private Set<String> savedContactLookups;

    public PhoneContactListManager(PhoneContactsListService service) {
        super(service);

        preferences = service.getSharedPreferences(PhoneContactListManager.class.getName(), Context.MODE_PRIVATE);
        contactsTopic = createTopic("android_phone_contacts", PhoneContactList.class);

        processor = new OfflineProcessor(service, this, CONTACTS_LIST_UPDATE_REQUEST_CODE,
                ACTION_UPDATE_CONTACTS_LIST, service.getCheckInterval(), false);
    }

    @Override
    public void start(@NonNull Set<String> set) {
        updateStatus(DeviceStatusListener.Status.READY);

        preferences.edit()
                .remove(CONTACT_IDS)
                .apply();

        savedContactLookups = preferences.getStringSet(CONTACT_LOOKUPS, Collections.<String>emptySet());
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
        try {
            Set<String> newContactLookups = getContactLookups();

            Integer added = null;
            Integer removed = null;

            if (!savedContactLookups.isEmpty()) {
                added = differenceSize(newContactLookups, savedContactLookups);
                removed = differenceSize(savedContactLookups, newContactLookups);
            }

            savedContactLookups = newContactLookups;
            preferences.edit().putStringSet(CONTACT_LOOKUPS, savedContactLookups).apply();

            double timestamp = System.currentTimeMillis() / 1000.0;
            send(contactsTopic, new PhoneContactList(timestamp, timestamp, added, removed, newContactLookups.size()));
        } catch (RuntimeException ex) {
            logger.error("Failed to lookup contact IDs", ex);
        }
    }

    private static int differenceSize(Collection<?> collectionA, Collection<?> collectionB) {
        int diff = 0;
        for (Object o : collectionA) {
            if (!collectionB.contains(o)) {
                diff++;
            }
        }
        return diff;
    }

    private Set<String> getContactLookups() {
        Set<String> contactIds = new HashSet<>();

        try (Cursor cursor = getService().getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                LOOKUP_COLUMNS, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
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
