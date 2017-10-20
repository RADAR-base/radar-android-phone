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

import org.radarcns.android.device.DeviceTopics;
import org.radarcns.kafka.ObservationKey;
import org.radarcns.passive.phone.PhoneUsageEvent;
import org.radarcns.passive.phone.PhoneUserInteraction;
import org.radarcns.topic.AvroTopic;

/** Topic manager for topics concerning the Phone sensors. */
public class PhoneUsageTopics extends DeviceTopics {
    private static final Object syncObject = new Object();
    private static PhoneUsageTopics instance = null;

    private final AvroTopic<ObservationKey, PhoneUserInteraction> interactionTopic;
    private final AvroTopic<ObservationKey, PhoneUsageEvent> usageEventTopic;

    public static PhoneUsageTopics getInstance() {
        synchronized (syncObject) {
            if (instance == null) {
                instance = new PhoneUsageTopics();
            }
            return instance;
        }
    }

    private PhoneUsageTopics() {
        interactionTopic = createTopic("android_phone_user_interaction", PhoneUserInteraction.class);
        usageEventTopic = createTopic("android_phone_usage_event", PhoneUsageEvent.class);
    }

    public AvroTopic<ObservationKey, PhoneUserInteraction> getUserInteractionTopic() {
        return interactionTopic;
    }

    public AvroTopic<ObservationKey, PhoneUsageEvent> getUsageEventTopic() {
        return usageEventTopic;
    }
}
