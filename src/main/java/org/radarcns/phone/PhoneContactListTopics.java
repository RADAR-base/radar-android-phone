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
import org.radarcns.passive.phone.PhoneContactList;
import org.radarcns.topic.AvroTopic;

public class PhoneContactListTopics extends DeviceTopics {
    private static volatile PhoneContactListTopics instance;
    private final AvroTopic<ObservationKey, PhoneContactList> contactListTopic;

    public static PhoneContactListTopics getInstance() {
        if (instance == null) {
            synchronized (PhoneContactListTopics.class) {
                if (instance == null) {
                    instance = new PhoneContactListTopics();
                }
            }
        }
        return instance;
    }

    private PhoneContactListTopics() {
        contactListTopic = createTopic("android_phone_contacts", PhoneContactList.class);
    }

    public AvroTopic<ObservationKey, PhoneContactList> getContactListTopic() {
        return contactListTopic;
    }
}
