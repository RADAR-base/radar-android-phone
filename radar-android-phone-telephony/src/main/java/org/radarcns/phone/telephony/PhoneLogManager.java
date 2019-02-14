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

package org.radarcns.phone.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.util.SparseArray;

import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.util.HashGenerator;
import org.radarcns.android.util.OfflineProcessor;
import org.radarcns.kafka.ObservationKey;
import org.radarcns.passive.phone.PhoneCall;
import org.radarcns.passive.phone.PhoneCallType;
import org.radarcns.passive.phone.PhoneSms;
import org.radarcns.passive.phone.PhoneSmsType;
import org.radarcns.passive.phone.PhoneSmsUnread;
import org.radarcns.topic.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static android.provider.BaseColumns._ID;

public class PhoneLogManager extends AbstractDeviceManager<PhoneLogService, BaseDeviceState> {
    private static final Logger logger = LoggerFactory.getLogger(PhoneLogManager.class);

    private static final int SQLITE_LIMIT = 1000;

    private static final String[] ID_COLUMNS = {_ID};
    private static final String[] SMS_COLUMNS = {
            Telephony.Sms.PERSON, Telephony.Sms.ADDRESS, Telephony.Sms.TYPE, Telephony.Sms.BODY};
    private static final String[] CALL_COLUMNS = {
            CallLog.Calls.DATE, CallLog.Calls.CACHED_LOOKUP_URI, CallLog.Calls.NUMBER,
            CallLog.Calls.DURATION, CallLog.Calls.TYPE};

    // If from contact, then the ID of the sender is a non-zero integer
    private static final SparseArray<PhoneCallType> CALL_TYPES = new SparseArray<>(4);
    private static final SparseArray<PhoneSmsType> SMS_TYPES = new SparseArray<>(7);
    private static final String LAST_SMS_KEY = "last.sms.time";
    private static final String LAST_CALL_KEY = "last.call.time";
    private static final String ACTIVITY_LAUNCH_WAKE = "org.radarcns.phone.telephony.PhoneLogManager.ACTIVITY_LAUNCH_WAKE";
    private static final int REQUEST_CODE_PENDING_INTENT = 465363071;
    private static final Pattern IS_NUMBER = Pattern.compile("^[+-]?\\d+$");

    static {
        CALL_TYPES.append(CallLog.Calls.INCOMING_TYPE, PhoneCallType.INCOMING);
        CALL_TYPES.append(CallLog.Calls.OUTGOING_TYPE, PhoneCallType.OUTGOING);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            CALL_TYPES.append(CallLog.Calls.VOICEMAIL_TYPE, PhoneCallType.VOICEMAIL);
        }
        CALL_TYPES.append(CallLog.Calls.MISSED_TYPE, PhoneCallType.MISSED);

        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_ALL, PhoneSmsType.OTHER);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_INBOX, PhoneSmsType.INCOMING);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_SENT, PhoneSmsType.OUTGOING);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_DRAFT, PhoneSmsType.OTHER);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_OUTBOX, PhoneSmsType.OUTGOING);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_FAILED, PhoneSmsType.OTHER);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_QUEUED, PhoneSmsType.OTHER);
    }

    private final AvroTopic<ObservationKey, PhoneCall> callTopic;
    private final AvroTopic<ObservationKey, PhoneSms> smsTopic;
    private final AvroTopic<ObservationKey, PhoneSmsUnread> smsUnreadTopic;
    private final HashGenerator hashGenerator;
    private final SharedPreferences preferences;
    private final ContentResolver db;
    private final OfflineProcessor logProcessor;
    private volatile long lastSmsTimestamp;
    private volatile long lastCallTimestamp;

    public PhoneLogManager(PhoneLogService context) {
        super(context);
        callTopic = createTopic("android_phone_call", PhoneCall.class);
        smsTopic = createTopic("android_phone_sms", PhoneSms.class);
        smsUnreadTopic = createTopic("android_phone_sms_unread", PhoneSmsUnread.class);

        preferences = context.getSharedPreferences(PhoneLogService.class.getName(), Context.MODE_PRIVATE);
        lastCallTimestamp = preferences.getLong(LAST_CALL_KEY, System.currentTimeMillis());
        lastSmsTimestamp = preferences.getLong(LAST_SMS_KEY, System.currentTimeMillis());
        db = getService().getContentResolver();

        hashGenerator = new HashGenerator(preferences);
        logProcessor = new OfflineProcessor.Builder(context)
                .addProcess(this::processCallLog)
                .addProcess(this::processSmsLog)
                .addProcess(this::processNumberUnreadSms)
                .requestIdentifier(REQUEST_CODE_PENDING_INTENT, ACTIVITY_LAUNCH_WAKE)
                .interval(PhoneLogService.CALL_SMS_LOG_INTERVAL_DEFAULT, TimeUnit.SECONDS)
                .wake(false)
                .build();

        setName(String.format(context.getString(R.string.call_log_service_name), android.os.Build.MODEL));
    }

    public void start(@NonNull Set<String> acceptableIds) {
        updateStatus(DeviceStatusListener.Status.READY);

        // Calls and sms, in and outgoing and number of unread sms
        logProcessor.start();

        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final void setCallAndSmsLogUpdateRate(long period, TimeUnit unit) {
        // Create pending intent, which cancels currently active pending intent
        logProcessor.setInterval(period, unit);
        logger.info("Call and SMS log: listener activated and set to a period of {} {}", period, unit);
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void processSmsLog() {
        lastSmsTimestamp = processDb(Telephony.Sms.CONTENT_URI, SMS_COLUMNS, Telephony.Sms.DATE,
                lastSmsTimestamp,
                record -> {
                    long date = record.getLong(record.getColumnIndex(Telephony.Sms.DATE));

                    // If from contact, then the ID of the sender is a non-zero integer
                    boolean isAContact = record.getInt(record.getColumnIndex(Telephony.Sms.PERSON)) > 0;
                    sendPhoneSms(date / 1000d,
                            record.getString(record.getColumnIndex(Telephony.Sms.ADDRESS)),
                            record.getInt(record.getColumnIndex(Telephony.Sms.TYPE)),
                            record.getString(record.getColumnIndex(Telephony.Sms.BODY)),
                            isAContact
                    );

                    return date;
                });

        preferences.edit()
                .putLong(LAST_SMS_KEY, lastSmsTimestamp)
                .apply();
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void processCallLog() {
        lastCallTimestamp = processDb(CallLog.Calls.CONTENT_URI, CALL_COLUMNS, CallLog.Calls.DATE,
                lastCallTimestamp,
                record -> {
                    long date = record.getLong(record.getColumnIndex(CallLog.Calls.DATE));

                    // If contact, then the contact lookup uri is given
                    boolean targetIsAContact = record.getString(record.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)) != null;

                    sendPhoneCall(date / 1000d,
                            record.getString(record.getColumnIndex(CallLog.Calls.NUMBER)),
                            record.getFloat(record.getColumnIndex(CallLog.Calls.DURATION)),
                            record.getInt(record.getColumnIndex(CallLog.Calls.TYPE)),
                            targetIsAContact
                    );

                    return date;
                });

        preferences.edit()
                .putLong(LAST_CALL_KEY, lastCallTimestamp)
                .apply();
    }

    private long processDb(Uri contentUri, String[] columns, String dateColumn, long previousTimestamp, RecordProcessor processor) {
        String where = dateColumn + " > ?";
        String orderBy = dateColumn + " ASC LIMIT " + SQLITE_LIMIT;

        int numUpdates;
        long lastTimestamp = previousTimestamp;

        do {
            String[] whereArgs = new String[] {Long.toString(lastTimestamp)};
            numUpdates = 0;
            // Query all sms with a date later than the last date seen and orderBy by date
            try (Cursor c = db.query(contentUri, columns, where, whereArgs, orderBy)) {
                if (c == null) {
                    return lastTimestamp;
                }

                while (c.moveToNext() && !logProcessor.isDone()) {
                    numUpdates++;
                    lastTimestamp = processor.processRecord(c);
                }
            } catch (Exception ex) {
                logger.error("Error in processing the sms log", ex);
            }
        } while (numUpdates == SQLITE_LIMIT && !logProcessor.isDone());

        return lastTimestamp;
    }

    private void processNumberUnreadSms() {
        String where = Telephony.Sms.READ + " = 0";
        try (Cursor c = db.query(Telephony.Sms.CONTENT_URI, ID_COLUMNS, where, null, null)) {
            if (c == null) {
                return;
            }
            sendNumberUnreadSms(c.getCount());
        } catch (Exception ex) {
            logger.error("Error in processing the sms log", ex);
        }
    }

    private void sendPhoneCall(double eventTimestamp, String target, float duration, int typeCode, boolean targetIsContact) {
        Long phoneNumber = getNumericPhoneNumber(target);
        ByteBuffer targetKey = createTargetHashKey(target, phoneNumber);

        PhoneCallType type = CALL_TYPES.get(typeCode, PhoneCallType.UNKNOWN);

        double timestamp = System.currentTimeMillis() / 1000d;
        send(callTopic,
                new PhoneCall(
                        eventTimestamp,
                        timestamp,
                        duration,
                        targetKey,
                        type,
                        targetIsContact,
                        phoneNumber == null,
                        target.length()
                )
        );

        logger.info("Call log: {}, {}, {}, {}, {}, {}, contact? {}", target, targetKey, duration, type, eventTimestamp, timestamp, targetIsContact);
    }

    private void sendPhoneSms(double eventTimestamp, String target, int typeCode, String message, boolean targetIsContact) {
        Long phoneNumber = getNumericPhoneNumber(target);
        ByteBuffer targetKey = createTargetHashKey(target, phoneNumber);

        PhoneSmsType type = SMS_TYPES.get(typeCode, PhoneSmsType.UNKNOWN);
        int length = message.length();

        // Only incoming messages are associated with a contact. For outgoing we don't know
        Boolean sendFromContact = null;
        if (type == PhoneSmsType.INCOMING) {
            sendFromContact = targetIsContact;
        }

        double timestamp = System.currentTimeMillis() / 1000d;
        send(smsTopic,
                new PhoneSms(
                        eventTimestamp,
                        timestamp,
                        targetKey,
                        type,
                        length,
                        sendFromContact,
                        phoneNumber == null,
                        target.length()
                )
        );

        logger.info("SMS log: {}, {}, {}, {}, {}, {} chars, contact? {}, length? {}", target, targetKey, type, eventTimestamp, timestamp, length, sendFromContact, target.length());
    }

    private void sendNumberUnreadSms(int numberUnread) {
        double timestamp = System.currentTimeMillis() / 1000d;
        send(smsUnreadTopic, new PhoneSmsUnread(timestamp, timestamp, numberUnread));

        logger.info("SMS unread: {} {}", timestamp, numberUnread);
    }

    /**
     * Returns true if target is numeric (e.g. not 'Dropbox' or 'Google')
     * @param target sms/phone target
     * @return boolean
     */
    private Long getNumericPhoneNumber(String target) {
        if (IS_NUMBER.matcher(target).matches()) {
            return Long.parseLong(target);
        } else {
            return null;
        }
    }

    /**
     * Extracts last 9 characters and hashes the result with a salt.
     * For phone numbers this means that the area code is removed
     * E.g.: +31232014111 becomes 232014111 and 0612345678 becomes 612345678 (before hashing)
     * If target is a name instead of a number (e.g. when sms), then hash this name
     * @param target String
     * @param phoneNumber phone number, null if it is not a number
     * @return MAC-SHA256 encoding of target or null if the target is anonymous
     */
    private ByteBuffer createTargetHashKey(String target, Long phoneNumber) {
        // If non-numerical, then hash the target directly
        if (phoneNumber == null) {
            return hashGenerator.createHashByteBuffer(target);
        } else if (phoneNumber < 0) {
            return null;
        } else {
            // remove international prefixes if present, since that would
            // give inconsistent results -> 0612345678 vs +31612345678
            int phoneNumberSuffix = (int) (phoneNumber % 1_000_000_000L);
            return hashGenerator.createHashByteBuffer(phoneNumberSuffix);
        }
    }

    @Override
    public void close() throws IOException {
        logProcessor.close();
        super.close();
    }
}
