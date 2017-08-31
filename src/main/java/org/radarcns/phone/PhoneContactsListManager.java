package org.radarcns.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import org.radarcns.android.data.DataCache;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.key.MeasurementKey;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PhoneContactsListManager extends AbstractDeviceManager<PhoneContactsListService, BaseDeviceState> implements Runnable {
    private static final int CONTACTS_LIST_UPDATE_REQUEST_CODE = 15765692;
    private static final String ACTION_UPDATE_CONTACTS_LIST = "org.radarcns.phone.PhoneContactsListManager.ACTION_UPDATE_CONTACTS_LIST";
    private static final String[] PROJECTION = {BaseColumns._ID};
    public static final String CONTACT_IDS = "contact_ids";

    private final SharedPreferences preferences;
    private final OfflineProcessor processor;
    private final DataCache<MeasurementKey, PhoneContactsList> contactsTable;
    private Set<String> savedContactIds;

    public PhoneContactsListManager(PhoneContactsListService service) {
        super(service, service.getDefaultState(), service.getDataHandler(), service.getUserId(), service.getSourceId());

        preferences = service.getSharedPreferences(PhoneContactsListManager.class.getName(), Context.MODE_PRIVATE);
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

        int added = 0;
        int removed = 0;
        if (savedContactIds != null) {
            for (String id : newContactIds) {
                if (!savedContactIds.contains(id)) {
                    added++;
                }
            }

            for (String id : savedContactIds) {
                if (!newContactIds.contains(id)) {
                    removed++;
                }
            }
        }

        savedContactIds = newContactIds;
        preferences.edit().putStringSet(CONTACT_IDS, savedContactIds).apply();

        double timestamp = System.currentTimeMillis() / 1000.0;
        send(contactsTable, new PhoneContactsList(timestamp, timestamp, added, removed, newContactIds.size()));
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
