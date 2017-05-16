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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.Date;

import org.radarcns.android.data.DataCache;
import org.radarcns.android.data.TableDataHandler;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.util.PersistentStorage;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Set;

import static android.content.Context.ALARM_SERVICE;

class PhoneUsageManager extends AbstractDeviceManager<PhoneUsageService, BaseDeviceState> {
    private static final Logger logger = LoggerFactory.getLogger(PhoneUsageManager.class);

    private UsageStatsManager usageStatsManager;

    private String previousEventPackageName;
    private Long previousEventTimestamp;
    private Integer previousEventType;
    private boolean previousEventIsSent = false;

    private static final String PACKAGE_NAME = "package_name";
    private static final String TIMESTAMP = "timestamp";
    private static final String EVENT_TYPE = "event_type";
    private static final String IS_SENT = "is_sent";
    private PersistentStorage persistentStorage = new PersistentStorage(this.getClass());

    private final DataCache<MeasurementKey, PhoneUserInteraction> userInteractionTable;
    private final DataCache<MeasurementKey, PhoneUsageEvent> usageEventTable;

    private static final long USAGE_EVENT_PERIOD_DEFAULT = 6; // seconds

    private PhoneUsageService context;

    public PhoneUsageManager(PhoneUsageService context, TableDataHandler dataHandler, String groupId, String sourceId) {
        super(context, new BaseDeviceState(), dataHandler, groupId, sourceId);

        this.context = context;
        PhoneUsageTopics topics = PhoneUsageTopics.getInstance();
        this.userInteractionTable = dataHandler.getCache(topics.getUserInteractionTopic());
        this.usageEventTable = dataHandler.getCache(topics.getUsageEventTopic());

        usageStatsManager = (UsageStatsManager) context.getSystemService("usagestats");
        this.loadPreviousEvent();

        setName(android.os.Build.MODEL);
        updateStatus(DeviceStatusListener.Status.READY);
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        setUsageEventUpdateRate(USAGE_EVENT_PERIOD_DEFAULT); // Every second
        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final synchronized void setUsageEventUpdateRate(final long period) {
        // Create an intent and alarm that will be wrapped in PendingIntent
        Intent intent = new Intent("ACTIVITY_LAUNCH_WAKE");

        // Create the pending intent and wrap our intent
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this.context, 1, intent, 0);

        // Get alarm manager and schedule it to run every period (seconds)
        AlarmManager alarmManager = (AlarmManager) this.context.getSystemService(ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), period * 1000, pendingIntent);

        // Activity to perform when alarm is triggered
        this.context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                processUsageEvents();
            }
        }, new IntentFilter("ACTIVITY_LAUNCH_WAKE"));

        logger.info("Usage event alarm activated and set to a period of {} seconds", period);
    }

    private void processUsageEvents() {
        // Get events from previous event to now
        // TODO: do not get events earlier than RADAR-CNS app install
        final long queryStartTime = previousEventPackageName == null ? 0 : previousEventTimestamp;
        final long queryEndTime = System.currentTimeMillis();
        UsageEvents usageEvents = usageStatsManager.queryEvents(queryStartTime, queryEndTime);

        // Loop through all events, send opening and closing of app
        // Assume events are ordered on timestamp in ascending order (old to new)
        UsageEvents.Event usageEvent = new UsageEvents.Event();
        while (usageEvents.getNextEvent(usageEvent)) {
            // Ignore config changes
            if (usageEvent.getEventType() == UsageEvents.Event.CONFIGURATION_CHANGE) {
                continue;
            }

            boolean isSent = false;
            if (isNewUsageEvent(usageEvent)) {
                // New event, so previous event was a closing of the previous event (?)
                // Send this closing event
                if (previousEventPackageName != null && !previousEventIsSent)
                    sendUsageEvent(previousEventPackageName, previousEventTimestamp, previousEventType);

                // Send the opening of new event
                sendUsageEvent(usageEvent);
                isSent = true;
            }

            // If not already processed, save
            updatePreviousUsageEvent(usageEvent, isSent);

            usageEvent = new UsageEvents.Event();
        }

        // Store the last previous event on internal memory for the next run
        this.storePreviousEvent();
    }

    private boolean isNewUsageEvent(UsageEvents.Event event) {
        if (previousEventPackageName == null) {
            return true;
        }
        boolean isDifferentPackage = ! event.getPackageName().equals(previousEventPackageName);
        boolean isNotProcessed = event.getTimeStamp() >= previousEventTimestamp;

        return isDifferentPackage && isNotProcessed;
    }

    private void updatePreviousUsageEvent(UsageEvents.Event event, boolean isSent) {
        // Update if this event newer than recorded previous event
        if (previousEventPackageName == null || event.getTimeStamp() >= previousEventTimestamp) {
            previousEventPackageName = event.getPackageName();
            previousEventTimestamp = event.getTimeStamp();
            previousEventType = event.getEventType();
            previousEventIsSent = isSent;
        }
    }

    private void storePreviousEvent() {
        Properties properties = new Properties();
        properties.setProperty(PACKAGE_NAME, previousEventPackageName);
        properties.setProperty(TIMESTAMP, Long.toString(previousEventTimestamp));
        properties.setProperty(EVENT_TYPE, Integer.toString(previousEventType));
        properties.setProperty(IS_SENT, Boolean.toString(previousEventIsSent));

        try {
            persistentStorage.store(properties);
        } catch (IOException ex) {
            logger.warn("Unable to store the previous event.", ex);
        }
    }

    private void loadPreviousEvent() {
        Properties propertiesDefault = new Properties();
        try {
            Properties propertiesOut = persistentStorage.loadOrStore(propertiesDefault);
            previousEventPackageName = propertiesOut.getProperty(PACKAGE_NAME);
            previousEventTimestamp = Long.valueOf(propertiesOut.getProperty(TIMESTAMP));
            previousEventType = Integer.valueOf(propertiesOut.getProperty(EVENT_TYPE));
            previousEventIsSent = Boolean.valueOf(propertiesOut.getProperty(IS_SENT));
        } catch (IOException | NumberFormatException | NullPointerException ex) {
            logger.warn("Unable to load the previous event.", ex);
            previousEventPackageName = null;
            previousEventTimestamp = null;
            previousEventType = null;
            previousEventIsSent = false;
        }
    }

    private void sendUsageEvent(UsageEvents.Event event) {
        sendUsageEvent(event.getPackageName(), event.getTimeStamp(), event.getEventType());
    }
    
    private void sendUsageEvent(String packageName, long timeStamp, int eventType) {
        UsageEventType usageEventType;
        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            usageEventType = UsageEventType.FOREGROUND;
        } else if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
            usageEventType = UsageEventType.BACKGROUND;
        } else {
            usageEventType = UsageEventType.OTHER;
        }

        double timeReceived = System.currentTimeMillis() / 1000d;
        PhoneUsageEvent value = new PhoneUsageEvent(
                timeStamp / 1000d, timeReceived, packageName, "", usageEventType);
        send(usageEventTable, value);

        logger.info("Event: [{}] {}\n\t{}", eventType, packageName, new Date(timeStamp));
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
