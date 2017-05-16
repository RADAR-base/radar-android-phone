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
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static android.content.Context.ALARM_SERVICE;

class PhoneUsageManager extends AbstractDeviceManager<PhoneUsageService, BaseDeviceState> {
    private static final Logger logger = LoggerFactory.getLogger(PhoneUsageManager.class);

    private UsageStatsManager usageStatsManager;
    private UsageEvents.Event lastUsageEvent;
    private boolean lastUsageEventIsSent;

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
        this.loadLastUsageEvent();

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
        // Get events from last event to now
        // TODO: do not get events earlier than RADAR-CNS app install
        final long queryStartTime = lastUsageEvent == null ? 0 : lastUsageEvent.getTimeStamp();
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
                // New event, so last event was a closing of the previous event (?)
                // Send this closing event
                if (lastUsageEvent != null && !lastUsageEventIsSent)
                    sendUsageEvent(lastUsageEvent);

                // Send the opening of new event
                sendUsageEvent(usageEvent);
                isSent = true;
            }

            // If not already processed, save
            updateLastUsageEvent(usageEvent, isSent);

            usageEvent = new UsageEvents.Event();
        }
    }

    private boolean isNewUsageEvent(UsageEvents.Event event) {
        if (lastUsageEvent == null) {
            return true;
        }
        boolean isDifferentPackage = ! event.getPackageName().equals(lastUsageEvent.getPackageName());
        boolean isNotProcessed = event.getTimeStamp() >= lastUsageEvent.getTimeStamp();

        return isDifferentPackage && isNotProcessed;
    }

    private void loadLastUsageEvent() {
        // TODO: load from internal storage
        lastUsageEvent = null;
    }

    private void storeLastUsageEvent() {
        // TODO: store on internal storage
    }

    private void updateLastUsageEvent(UsageEvents.Event event, boolean isSent) {
        // Update if this event newer than recorded lastUsageEvent
        if (lastUsageEvent == null || event.getTimeStamp() >= lastUsageEvent.getTimeStamp()) {
            lastUsageEvent = event;
            lastUsageEventIsSent = isSent;
            storeLastUsageEvent();
        }
    }

    private void sendUsageEvent(UsageEvents.Event event) {
        Date timeStamp = new Date(event.getTimeStamp());

        String out = String.format(
                "[%3$d] %1$s\n" +
                        "\t\t %2$s (%4$d)\n" +
                        "\t\t %5$s\n" +
                        "\t\t %6$s\n",
                event.getPackageName(),
                timeStamp.toString(),
                event.getEventType(),
                event.getTimeStamp(),
                event.getClassName(),
                "" //PlayStoreParser.fetchCategory(event.getPackageName())
        );
        logger.info(out);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
