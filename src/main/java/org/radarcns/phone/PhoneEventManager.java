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

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.radarcns.android.data.DataCache;
import org.radarcns.android.data.TableDataHandler;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.key.MeasurementKey;
import org.radarcns.phoneUtil.PlayStoreParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

class PhoneEventManager extends AbstractDeviceManager<PhoneEventService, BaseDeviceState> {
    private static final Logger logger = LoggerFactory.getLogger(PhoneEventManager.class);

    private UsageStatsManager usageStatsManager;
    private UsageEvents.Event lastUsageEvent;
    private boolean lastUsageEventIsSent;

    private final DataCache<MeasurementKey, PhoneUserInteraction> userInteractionTable;
    private final DataCache<MeasurementKey, PhoneUsageEvent> usageEventTable;

    private static final long USAGE_EVENT_PERIOD_DEFAULT = 60; // seconds
    private ScheduledFuture<?> usageEventFuture;
    private final ScheduledExecutorService executor;

    public PhoneEventManager(PhoneEventService context, TableDataHandler dataHandler, String groupId, String sourceId) {
        super(context, new BaseDeviceState(), dataHandler, groupId, sourceId);
        PhoneEventTopics topics = PhoneEventTopics.getInstance();
        this.userInteractionTable = dataHandler.getCache(topics.getUserInteractionTopic());
        this.usageEventTable = dataHandler.getCache(topics.getUsageEventTopic());

        // Scheduler TODO: run executor with existing thread pool/factory
        executor = Executors.newSingleThreadScheduledExecutor();

        usageStatsManager = (UsageStatsManager) context.getSystemService("usagestats");

        setName(android.os.Build.MODEL);
        updateStatus(DeviceStatusListener.Status.READY);
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        setUsageEventUpdateRate(USAGE_EVENT_PERIOD_DEFAULT); // Every second
        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final synchronized void setUsageEventUpdateRate(final long period) {
        if (usageEventFuture != null) {
            usageEventFuture.cancel(false);
        }

        this.loadLastUsageEvent();
        usageEventFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    processUsageEvents();
                } catch (Exception ex) {
                    logger.error("Failed to read or write last call processed {}", ex.toString());
                }
            }
        }, 0, period, TimeUnit.SECONDS);

        logger.info("Usage Event listener activated and set to a period of {}", period);
    }

    private void processUsageEvents() {
        // Get events from last event to now
        final long queryStartTime = lastUsageEvent == null ? 0 : lastUsageEvent.getTimeStamp();
        final long queryEndTime = System.currentTimeMillis();
        UsageEvents usageEvents = usageStatsManager.queryEvents(queryStartTime, queryEndTime);

        // Loop through all events
        // Assume events are ordered on timestamp in ascending order (old to new)
        UsageEvents.Event usageEvent = new UsageEvents.Event();
        while (usageEvents.getNextEvent(usageEvent)) {
            // Ignore config changes
            if (usageEvent.getEventType() == UsageEvents.Event.CONFIGURATION_CHANGE) {
                continue;
            }

            if (isNewUsageEvent(usageEvent)) {
                // New event, so last event was a closing of the previous event (?)
                // Send this closing event
                if (lastUsageEvent != null && !lastUsageEventIsSent)
                    sendUsageEvent(lastUsageEvent);
                // Send the opening of new event
                sendUsageEvent(usageEvent);
                lastUsageEventIsSent = true;
            } else {
                lastUsageEventIsSent = false;  // if old event that has already been sent, then this is not true
            }

            // If not already processed, save
            updateLastUsageEvent(lastUsageEvent);

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
        lastUsageEvent = null;
    }

    private void storeLastUsageEvent() {
        // TODO: store on internal storage
    }

    private void updateLastUsageEvent(UsageEvents.Event event) {
        // Update if this event newer than recorded lastUsageEvent
        if (lastUsageEvent == null || event.getTimeStamp() >= lastUsageEvent.getTimeStamp()) {
            lastUsageEvent = event;
            storeLastUsageEvent();
        }
    }

    private void sendUsageEvent(UsageEvents.Event event) {
        Date timeStamp = new Date(event.getTimeStamp());

        String out = String.format(
                "[%3$d] %1$s\n" +
                        "\t\t %2$s (%4$d)\n" +
                        "\t\t %5$s\n",
                event.getPackageName(),
                timeStamp.toString(),
                event.getEventType(),
                event.getTimeStamp(),
                event.getClassName()
        );
        logger.info(out);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
