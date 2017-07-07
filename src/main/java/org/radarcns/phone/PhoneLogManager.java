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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.SparseArray;

import org.radarcns.android.data.DataCache;
import org.radarcns.android.data.TableDataHandler;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.key.MeasurementKey;
import org.radarcns.util.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static android.content.Context.ALARM_SERVICE;

public class PhoneLogManager extends AbstractDeviceManager<PhoneLogService, BaseDeviceState> {
    private static final Logger logger = LoggerFactory.getLogger(PhoneLogManager.class);
    private final SharedPreferences preferences;

    private static final SparseArray<PhoneCallType> CALL_TYPES = new SparseArray<>(4);
    private static final SparseArray<PhoneSmsType> SMS_TYPES = new SparseArray<>(7);
    private static final String LAST_SMS_KEY = "last.sms.time";
    private static final String LAST_CALL_KEY = "last.call.time";
    private static final String HASH_KEY = "hash.key";
    private static final String ACTIVITY_LAUNCH_WAKE = "ACTIVITY_LAUNCH_WAKE";
    private static final int REQUEST_CODE_PENDING_INTENT = 1;
    private static final long CALL_SMS_LOG_INTERVAL_DEFAULT = 24 * 60 * 60; // seconds
    private static final long CALL_SMS_LOG_HISTORY_DEFAULT = 24 * 60 * 60; // seconds

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

    private final DataCache<MeasurementKey, PhoneCall> callTable;
    private final DataCache<MeasurementKey, PhoneSms> smsTable;
    private final DataCache<MeasurementKey, PhoneSmsUnread> smsUnreadTable;
    private final Mac sha256;
    private final byte[] hashBuffer = new byte[4];

    private Context context;

    public PhoneLogManager(PhoneLogService phoneLogService, TableDataHandler dataHandler, String userId, String sourceId) {
        super(phoneLogService, new BaseDeviceState(), dataHandler, userId, sourceId);
        callTable = getCache(phoneLogService.getTopics().getCallTopic());
        smsTable = getCache(phoneLogService.getTopics().getSmsTopic());
        smsUnreadTable = getCache(phoneLogService.getTopics().getSmsUnreadTopic());

        context = phoneLogService.getApplicationContext();
        preferences = context.getSharedPreferences(PhoneLogService.class.getName(), Context.MODE_PRIVATE);

        try {
            this.sha256 = Mac.getInstance("HmacSHA256");
            sha256.init(new SecretKeySpec(loadHashKey(), "HmacSHA256"));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot retrieve hashing algorithm", ex);
        } catch (InvalidKeyException ex) {
            throw new IllegalStateException("Encoding is invalid", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load hashing key", ex);
        }

        setName(android.os.Build.MODEL);
    }

    public void start(@NonNull Set<String> acceptableIds) {
        // Calls and sms, in and outgoing and number of unread sms
        setCallAndSmsLogUpdateRate(CALL_SMS_LOG_INTERVAL_DEFAULT);

        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final synchronized void setCallAndSmsLogUpdateRate(final long period) {
        // Create pending intent, which cancels currently active pending intent
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this.context, REQUEST_CODE_PENDING_INTENT,
                new Intent(ACTIVITY_LAUNCH_WAKE), PendingIntent.FLAG_CANCEL_CURRENT);

        // Get alarm manager and schedule it to run every period (seconds)
        AlarmManager alarmManager = (AlarmManager) this.context.getSystemService(ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), period * 1000, pendingIntent);

        // Activity to perform when alarm is triggered
        this.context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                processCallLog();
                processSmsLog();
                processNumberUnreadSms();
            }
        }, new IntentFilter(ACTIVITY_LAUNCH_WAKE));

        logger.info("Call and SMS log: listener activated and set to a period of {}", period);
    }

    private synchronized void processCallLog() throws SecurityException {
        final long initialDateRead = Long.parseLong(preferences.getString(LAST_CALL_KEY, "0"));

        // If this is the first call (initialDateRead is default),
        // then the lastDateRead is a set time before current time.
        long lastDateRead = initialDateRead;
        if (initialDateRead == 0) {
            lastDateRead = System.currentTimeMillis() - CALL_SMS_LOG_HISTORY_DEFAULT * 1000;
        }

        try (Cursor c = getService().getContentResolver().query(CallLog.Calls.CONTENT_URI, null, CallLog.Calls.DATE + " > " + lastDateRead, null, CallLog.Calls.DATE + " ASC")) {
            if (c == null) {
                return;
            }

            while (c.moveToNext()) {
                long date = c.getLong(c.getColumnIndex(CallLog.Calls.DATE));

                sendPhoneCall(date / 1000d,
                        c.getString(c.getColumnIndex(CallLog.Calls.NUMBER)),
                        c.getFloat(c.getColumnIndex(CallLog.Calls.DURATION)),
                        c.getInt(c.getColumnIndex(CallLog.Calls.TYPE))
                );
                lastDateRead = date;
            }
        } catch (Throwable t) {
            logger.warn("Error in processing the call log: {}", t.getMessage());
            t.printStackTrace();
        } finally {
            if (lastDateRead != initialDateRead) {
                preferences.edit().putString(LAST_CALL_KEY, Long.toString(lastDateRead)).apply();
            }
        }
    }

    private synchronized void processSmsLog() {
        final long initialDateRead = Long.parseLong(preferences.getString(LAST_SMS_KEY, "0"));

        // If this is the first call (initialDateRead is default),
        // then the lastDateRead is a set time before current time.
        long lastDateRead = initialDateRead;
        if (initialDateRead == 0) {
            lastDateRead = System.currentTimeMillis() - CALL_SMS_LOG_HISTORY_DEFAULT * 1000;
        }

        // Query all sms with a date later than the last date seen and sort by date
        int numberUnread = 0;
        try (Cursor c = getService().getContentResolver().query(Telephony.Sms.CONTENT_URI, null, Telephony.Sms.DATE + " > " + lastDateRead, null, Telephony.Sms.DATE + " ASC")) {
            if (c == null) {
                return;
            }

            while (c.moveToNext()) {
                long date = c.getLong(c.getColumnIndex(Telephony.Sms.DATE));

                if (c.getInt(c.getColumnIndex(Telephony.Sms.READ)) == 0) {
                    numberUnread++;
                }

                sendPhoneSms(date / 1000d,
                        c.getString(c.getColumnIndex(Telephony.Sms.ADDRESS)),
                        c.getInt(c.getColumnIndex(Telephony.Sms.TYPE)),
                        c.getString(c.getColumnIndex(Telephony.Sms.BODY))
                );
                lastDateRead = date;
            }
        } catch (Exception ex) {
            logger.error("Error in processing the sms log", ex);
        } finally {
            if (lastDateRead != initialDateRead) {
                preferences.edit().putString(LAST_SMS_KEY, Long.toString(lastDateRead)).apply();
            }
        }
    }

    private void processNumberUnreadSms() {
        try (Cursor c = getService().getContentResolver().query(Telephony.Sms.CONTENT_URI, null, Telephony.Sms.READ + " = 0", null, null)) {
            if (c == null) {
                return;
            }
            sendNumberUnreadSms(c.getCount());
        } catch (Exception ex) {
            logger.error("Error in processing the sms log", ex);
        }
    }

    private void sendPhoneCall(double eventTimestamp, String target, float duration, int typeCode) {
        ByteBuffer targetKey = null;
        byte[] targetKeyByte = createTargetHashKey(target);
        if (targetKeyByte != null) {
            targetKey = ByteBuffer.wrap(targetKeyByte);
        }

        PhoneCallType type = CALL_TYPES.get(typeCode, PhoneCallType.UNKNOWN);

        double timestamp = System.currentTimeMillis() / 1000d;
        send(callTable,
                new PhoneCall(eventTimestamp, timestamp, duration, targetKey, type)
        );

        logger.info("Call log: {}, {}, {}, {}, {}, {}", target, targetKey, duration, type, eventTimestamp, timestamp);
    }

    private void sendPhoneSms(double eventTimestamp, String target, int typeCode, String message) {
        byte[] targetKey = createTargetHashKey(target);
        // TODO: recognise 'automated' sms like verification and notification sms
        // e.g. 4 digit phone numbers, or target without phone number (just string)

        PhoneSmsType type = SMS_TYPES.get(typeCode, PhoneSmsType.UNKNOWN);
        int length = message.length();

        double timestamp = System.currentTimeMillis() / 1000d;
        send(smsTable,
                new PhoneSms(eventTimestamp, timestamp, ByteBuffer.wrap(targetKey), type, length)
        );

        logger.info("SMS log: {}, {}, {}, {}, {}, {} chars", target, targetKey, type, eventTimestamp, timestamp, length);
    }

    private void sendNumberUnreadSms(int numberUnread) {
        double timestamp = System.currentTimeMillis() / 1000d;
        send(smsUnreadTable,
                new PhoneSmsUnread(timestamp, timestamp, numberUnread)
        );

        logger.info("SMS unread: {} {}", timestamp, numberUnread);
    }

    /**
     * Extracts last 9 characters and hashes the result with a salt.
     * For phone numbers this means that the area code is removed
     * E.g.: +31232014111 becomes 232014111 and 0612345678 becomes 612345678 (before hashing)
     * If target is a name instead of a number (e.g. when sms), then hash this name
     * @param target String
     * @return String
     */
    public byte[] createTargetHashKey(String target) {
        // If non-numerical, then hash the target directly
        try {
            Long targetLong = Long.parseLong(target);

            // Anonymous calls have target -1 or -2, do not hash them
            if (targetLong < 0) {
                return null;
            }
        } catch (NumberFormatException ex) {
            return sha256.doFinal(target.getBytes());
        }

        int length = target.length();
        if (length > 9) {
            target = target.substring(length - 9, length);
            // remove all non-numeric characters
            target = target.replaceAll("[^0-9]", "");
            // for example, a
            if (target.isEmpty()) {
                return null;
            }
        }

        Serialization.intToBytes(Integer.valueOf(target), hashBuffer, 0);
        return sha256.doFinal(hashBuffer);
    }

    private byte[] loadHashKey() throws IOException {
        String b64Salt = preferences.getString(HASH_KEY, null);
        if (b64Salt == null) {
            byte[] byteSalt = new byte[16];
            new SecureRandom().nextBytes(byteSalt);

            b64Salt = Base64.encodeToString(byteSalt, Base64.NO_WRAP);
            preferences.edit().putString(HASH_KEY, b64Salt).apply();
            return byteSalt;
        } else {
            return Base64.decode(b64Salt, Base64.NO_WRAP);
        }
    }
}
