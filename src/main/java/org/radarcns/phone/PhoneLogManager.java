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
import org.radarcns.android.util.PersistentStorage;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class PhoneLogManager extends AbstractDeviceManager<PhoneLogService, BaseDeviceState> {
    private static final Logger logger = LoggerFactory.getLogger(PhoneLogManager.class);
    private static final PersistentStorage storage = new PersistentStorage(PhoneSensorManager.class);

    private static final SparseArray<PhoneCallType> CALL_TYPES = new SparseArray<>(4);
    private static final SparseArray<PhoneSmsType> SMS_TYPES = new SparseArray<>(7);
    private static final String LAST_SMS_KEY = "last.sms.time";
    private static final String LAST_CALL_KEY = "last.call.time";
    private static final String HASH_KEY = "hash.key";
    private static final long CALL_SMS_LOG_INTERVAL_DEFAULT = 24*60*60; // seconds

    static {
        CALL_TYPES.append(CallLog.Calls.INCOMING_TYPE, PhoneCallType.INCOMING);
        CALL_TYPES.append(CallLog.Calls.OUTGOING_TYPE, PhoneCallType.OUTGOING);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            CALL_TYPES.append(CallLog.Calls.VOICEMAIL_TYPE, PhoneCallType.VOICEMAIL);
        }
        CALL_TYPES.append(CallLog.Calls.MISSED_TYPE, PhoneCallType.MISSED);

        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_ALL, PhoneSmsType.OTHER);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_INBOX, PhoneSmsType.INCOMING);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_SENT, PhoneSmsType.OTHER);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_DRAFT, PhoneSmsType.OTHER);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_OUTBOX, PhoneSmsType.OUTGOING);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_FAILED, PhoneSmsType.OTHER);
        SMS_TYPES.append(Telephony.Sms.MESSAGE_TYPE_QUEUED, PhoneSmsType.OTHER);
    }

    private final DataCache<MeasurementKey, PhoneCall> callTable;
    private final DataCache<MeasurementKey, PhoneSms> smsTable;
    private final Mac sha256;
    private final byte[] hashBuffer = new byte[4];

    private ScheduledFuture<?> callLogReadFuture;
    private ScheduledFuture<?> smsLogReadFuture;
    private final ScheduledExecutorService executor;



    public PhoneLogManager(PhoneLogService phoneLogService, TableDataHandler dataHandler, String userId, String sourceId) {
        super(phoneLogService, new BaseDeviceState(), dataHandler, userId, sourceId);
        callTable = getCache(phoneLogService.getTopics().getCallTopic());
        smsTable = getCache(phoneLogService.getTopics().getSmsTopic());

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

        // Scheduler TODO: run executor with existing thread pool/factory
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void start(@NonNull Set<String> acceptableIds) {
        // Calls and sms, in and outgoing
        setCallLogUpdateRate(CALL_SMS_LOG_INTERVAL_DEFAULT);
        setSmsLogUpdateRate(CALL_SMS_LOG_INTERVAL_DEFAULT);

        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public final synchronized void setCallLogUpdateRate(final long period) {
        if (callLogReadFuture != null) {
            callLogReadFuture.cancel(false);
        }

        callLogReadFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {

                try {
                    final long initialDateRead = Long.parseLong(storage.getOrSet(LAST_CALL_KEY, "0"));
                    long lastDateRead = initialDateRead;
                    long threshold = System.currentTimeMillis() / 1000L - period;
                    if (lastDateRead < threshold) {
                        lastDateRead = threshold;
                    }

                    try (Cursor c = getService().getContentResolver().query(CallLog.Calls.CONTENT_URI, null, CallLog.Calls.DATE + " > " + lastDateRead, null, CallLog.Calls.DATE + " ASC")) {
                        if (c == null) {
                            return;
                        }

                        while (c.moveToNext()) {
                            long date = c.getLong(c.getColumnIndex(CallLog.Calls.DATE));

                            processCall(date / 1000d,
                                    c.getString(c.getColumnIndex(CallLog.Calls.NUMBER)),
                                    c.getFloat(c.getColumnIndex(CallLog.Calls.DURATION)),
                                    c.getInt(c.getColumnIndex(CallLog.Calls.TYPE)));
                            lastDateRead = date;
                        }
                    } catch (Throwable t) {
                        logger.warn("Error in processing the call log: {}", t.getMessage());
                        t.printStackTrace();
                    } finally {
                        if (lastDateRead != initialDateRead) {
                            storage.put(LAST_CALL_KEY, Long.toString(lastDateRead));
                        }
                    }
                } catch (IOException ex) {
                    logger.error("Failed to read or write last call processed.", ex);
                }
            }
        }, 0, period, TimeUnit.SECONDS);

        logger.info("Call log: listener activated and set to a period of {}", period);
    }

    public final synchronized void setSmsLogUpdateRate(final long period) {
        if (smsLogReadFuture != null) {
            smsLogReadFuture.cancel(false);
        }

        smsLogReadFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    final long initialDateRead = Long.parseLong(storage.getOrSet(LAST_SMS_KEY, "0"));
                    long lastDateRead = initialDateRead;
                    long threshold = System.currentTimeMillis() / 1000L - period;
                    if (lastDateRead < threshold) {
                        lastDateRead = threshold;
                    }

                    try (Cursor c = getService().getContentResolver().query(Telephony.Sms.CONTENT_URI, null, Telephony.Sms.DATE + " > " + lastDateRead, null, Telephony.Sms.DATE + " ASC")) {
                        if (c == null) {
                            return;
                        }

                        while (c.moveToNext()) {
                            long date = c.getLong(c.getColumnIndex(CallLog.Calls.DATE));

                            processSMS(date / 1000d,
                                    c.getString(c.getColumnIndex(Telephony.Sms.ADDRESS)),
                                    c.getInt(c.getColumnIndex(Telephony.Sms.TYPE)),
                                    c.getString(c.getColumnIndex(Telephony.Sms.BODY)));
                            lastDateRead = date;
                        }
                    } catch (Exception ex) {
                        logger.error("Error in processing the sms log", ex);
                    } finally {
                        if (lastDateRead != initialDateRead) {
                            storage.put(LAST_SMS_KEY, Long.toString(lastDateRead));
                        }
                    }
                } catch (IOException ex) {
                    logger.error("Failed to read or write last sms processed.", ex);
                }
            }
        }, 0, period, TimeUnit.SECONDS);

        logger.info("SMS log: listener activated and set to a period of {}", period);
    }

    public void processCall(double eventTimestamp, String target, float duration, int typeCode) {
        // Check whether a newer call has already been stored
        byte[] targetKey = createTargetHashKey(target);

        PhoneCallType type = CALL_TYPES.get(typeCode, PhoneCallType.UNKNOWN);

        double timestamp = System.currentTimeMillis() / 1000d;
        send(callTable, new PhoneCall(
                eventTimestamp, timestamp, duration, ByteBuffer.wrap(targetKey), type));

        logger.info("Call log: {}, {}, {}, {}, {}, {}", target, targetKey, duration, type, eventTimestamp, timestamp);
    }

    public void processSMS(double eventTimestamp, String target, int typeCode, String message) {
        byte[] targetKey = createTargetHashKey(target);

        PhoneSmsType type = SMS_TYPES.get(typeCode, PhoneSmsType.UNKNOWN);
        int length = message.length();

        double timestamp = System.currentTimeMillis() / 1000d;
        send(smsTable, new PhoneSms(
                eventTimestamp, timestamp, ByteBuffer.wrap(targetKey), type, length));

        logger.info("SMS log: {}, {}, {}, {}, {}, {} chars", target, targetKey, type, eventTimestamp, timestamp, length);
    }

    /**
     * Extracts last 9 characters and hashes the result with a salt.
     * For phone numbers this means that the area code is removed
     * E.g.:+31232014111 becomes 232014111 and 0612345678 becomes 612345678 (before hashing)
     * @param target String
     * @return String
     */
    public byte[] createTargetHashKey(String target) {
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

    private static byte[] loadHashKey() throws IOException {
        String b64Salt = storage.get(HASH_KEY);
        if (b64Salt == null) {
            byte[] byteSalt = new byte[16];
            new SecureRandom().nextBytes(byteSalt);

            b64Salt = Base64.encodeToString(byteSalt, Base64.NO_WRAP);
            storage.put(HASH_KEY, b64Salt);
            return byteSalt;
        } else {
            return Base64.decode(b64Salt, Base64.NO_WRAP);
        }
    }
}
