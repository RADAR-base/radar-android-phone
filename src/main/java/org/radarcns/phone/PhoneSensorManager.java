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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.radarcns.android.data.DataCache;
import org.radarcns.android.data.TableDataHandler;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

import static android.content.Context.POWER_SERVICE;
import static android.os.BatteryManager.BATTERY_STATUS_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_DISCHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;

class PhoneSensorManager extends AbstractDeviceManager<PhoneSensorService, PhoneState> implements DeviceManager, SensorEventListener {
    private static final Logger logger = LoggerFactory.getLogger(PhoneSensorManager.class);

    // Sensors to register, together with the name of the sensor
    private static final int[] SENSOR_TYPES_TO_REGISTER = {
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_LIGHT,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_STEP_COUNTER
    };

    // Names of the sensor (for warning message if unable to register)
    private static final SparseArray<String> SENSOR_NAMES = new SparseArray<>(5);
    static {
        SENSOR_NAMES.append(Sensor.TYPE_ACCELEROMETER, Sensor.STRING_TYPE_ACCELEROMETER);
        SENSOR_NAMES.append(Sensor.TYPE_LIGHT, Sensor.STRING_TYPE_LIGHT);
        SENSOR_NAMES.append(Sensor.TYPE_MAGNETIC_FIELD, Sensor.STRING_TYPE_MAGNETIC_FIELD);
        SENSOR_NAMES.append(Sensor.TYPE_GYROSCOPE, Sensor.STRING_TYPE_GYROSCOPE);
        SENSOR_NAMES.append(Sensor.TYPE_STEP_COUNTER, Sensor.STRING_TYPE_STEP_COUNTER);
    }

    private static final int SENSOR_DELAY_DEFAULT = SensorManager.SENSOR_DELAY_NORMAL;

    private static final SparseArray<BatteryStatus> BATTERY_TYPES = new SparseArray<>(5);
    static {
        BATTERY_TYPES.append(BATTERY_STATUS_UNKNOWN, BatteryStatus.UNKNOWN);
        BATTERY_TYPES.append(BATTERY_STATUS_CHARGING, BatteryStatus.CHARGING);
        BATTERY_TYPES.append(BATTERY_STATUS_DISCHARGING, BatteryStatus.DISCHARGING);
        BATTERY_TYPES.append(BATTERY_STATUS_NOT_CHARGING, BatteryStatus.NOT_CHARGING);
        BATTERY_TYPES.append(BATTERY_STATUS_FULL, BatteryStatus.FULL);
    }

    private final DataCache<MeasurementKey, PhoneAcceleration> accelerationTable;
    private final DataCache<MeasurementKey, PhoneLight> lightTable;
    private final DataCache<MeasurementKey, PhoneStepCount> stepCountTable;
    private final DataCache<MeasurementKey, PhoneGyroscope> gyroscopeTable;
    private final DataCache<MeasurementKey, PhoneMagneticField> magneticFieldTable;
    private final AvroTopic<MeasurementKey, PhoneBatteryLevel> batteryTopic;
    private final SparseIntArray sensorDelays;

    private final SensorManager sensorManager;
    private int lastStepCount = -1;
    private PowerManager.WakeLock wakeLock;

    public PhoneSensorManager(PhoneSensorService context, TableDataHandler dataHandler, String groupId, String sourceId) {
        super(context, new PhoneState(), dataHandler, groupId, sourceId);
        PhoneSensorTopics topics = PhoneSensorTopics.getInstance();
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.lightTable = dataHandler.getCache(topics.getLightTopic());
        this.stepCountTable = dataHandler.getCache(topics.getStepCountTopic());
        this.gyroscopeTable = dataHandler.getCache(topics.getGyroscopeTopic());
        this.magneticFieldTable = dataHandler.getCache(topics.getMagneticFieldTopic());
        this.sensorDelays = new SparseIntArray();
        this.batteryTopic = topics.getBatteryLevelTopic();

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        setName(android.os.Build.MODEL);
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        PowerManager powerManager = (PowerManager) getService().getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneSensorManager");
        wakeLock.acquire();

        registerSensors();

        // Battery
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        processBatteryStatus(getService().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                    processBatteryStatus(intent);
                }
            }
        }, batteryFilter));

        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    /**
     * Register all sensors supplied in SENSOR_TYPES_TO_REGISTER constant.
     */

     private void registerSensors() {
        // At time of writing this is: Accelerometer, Light, Gyroscope, Magnetic Field and Step Counter
        for (int sensorType : SENSOR_TYPES_TO_REGISTER) {
            if (sensorManager.getDefaultSensor(sensorType) != null) {
                Sensor sensor = sensorManager.getDefaultSensor(sensorType);
                int delay = sensorDelays.get(sensorType, SENSOR_DELAY_DEFAULT);
                sensorManager.registerListener(this, sensor, delay);
            } else {
                logger.warn("The sensor '{}' could not be found", SENSOR_NAMES.get(sensorType,"unknown"));
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                processAcceleration(event);
                break;
            case Sensor.TYPE_LIGHT:
                processLight(event);
                break;
            case Sensor.TYPE_GYROSCOPE:
                processGyroscope(event);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                processMagneticField(event);
                break;
            case Sensor.TYPE_STEP_COUNTER:
                processStep(event);
                break;
            default:
                logger.debug("Phone registered unknown sensor change: '{}'", event.sensor.getType());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no action
    }

    /**
     * Convert event timestamp to seconds UTC
     * Event timestamp is given in nanoseconds uptime
     * First calculates the seconds passed since the event, by taking difference between current
     * uptime and uptime at the moment of the event.
     * Then this is substracted from the current UTC time.
     * @param eventTimestampNanos nanoseconds uptime at event
     * @return timestamp in seconds UTC
     */
    private static double eventTimestampToSecondsUTC(long eventTimestampNanos) {
        double currentSeconds = System.currentTimeMillis() / 1_000d;
        double secondsSinceEvent = (System.nanoTime() - eventTimestampNanos) / 1_000_000_000d;

        return currentSeconds - secondsSinceEvent;
    }

    private void processAcceleration(SensorEvent event) {
        // x,y,z are in m/s2
        float x = event.values[0] / SensorManager.GRAVITY_EARTH;
        float y = event.values[1] / SensorManager.GRAVITY_EARTH;
        float z = event.values[2] / SensorManager.GRAVITY_EARTH;
        getState().setAcceleration(x, y, z);
        
        double timeReceived = System.currentTimeMillis() / 1_000d;

        // nanoseconds uptime to seconds utc
        double timestamp = eventTimestampToSecondsUTC(event.timestamp);

        send(accelerationTable, new PhoneAcceleration(timestamp, timeReceived, x, y, z));
    }

    private void processLight(SensorEvent event) {
        float lightValue = event.values[0];
        getState().setLight(lightValue);
        
        double timeReceived = System.currentTimeMillis() / 1_000d;
        
        // nanoseconds uptime to seconds utc
        double timestamp = eventTimestampToSecondsUTC(event.timestamp);

        send(lightTable, new PhoneLight(timestamp, timeReceived, lightValue));
    }

    private void processGyroscope(SensorEvent event) {
        // Not normalized axis of rotation in rad/s
        float axisX = event.values[0];
        float axisY = event.values[1];
        float axisZ = event.values[2];

        double timeReceived = System.currentTimeMillis() / 1_000d;

        // nanoseconds uptime to seconds utc
        double timestamp = eventTimestampToSecondsUTC(event.timestamp);

        send(gyroscopeTable, new PhoneGyroscope(timestamp, timeReceived, axisX, axisY, axisZ));
    }

    private void processMagneticField(SensorEvent event) {
        // Magnetic field in microTesla
        float axisX = event.values[0];
        float axisY = event.values[1];
        float axisZ = event.values[2];

        double timeReceived = System.currentTimeMillis() / 1_000d;

        // nanoseconds uptime to seconds utc
        double timestamp = eventTimestampToSecondsUTC(event.timestamp);
        ;
        send(magneticFieldTable, new PhoneMagneticField(timestamp, timeReceived, axisX, axisY, axisZ));
    }

    private void processStep(SensorEvent event) {
        // Number of step since listening or since reboot
        int stepCount = (int) event.values[0];

        double timeReceived = System.currentTimeMillis() / 1_000d;

        // nanoseconds uptime to seconds utc
        double timestamp = eventTimestampToSecondsUTC(event.timestamp);

        // Send how many steps have been taken since the last time this function was triggered
        // Note: normally processStep() is called for every new step and the stepsSinceLastUpdate is 1
        int stepsSinceLastUpdate;
        if (lastStepCount == -1 || lastStepCount > stepCount) {
            stepsSinceLastUpdate = 1;
        } else {
            stepsSinceLastUpdate = stepCount - lastStepCount;
        }
        lastStepCount = stepCount;
        send(stepCountTable, new PhoneStepCount(timestamp, timeReceived, stepsSinceLastUpdate));

        logger.info("Steps taken: {}", stepsSinceLastUpdate);
    }

    private void processBatteryStatus(Intent intent) {
        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float)scale;

        boolean isPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BATTERY_STATUS_UNKNOWN);
        BatteryStatus batteryStatus = BATTERY_TYPES.get(status, BatteryStatus.UNKNOWN);

        getState().setBatteryLevel(batteryPct);

        double time = System.currentTimeMillis() / 1000d;
        trySend(batteryTopic, 0L, new PhoneBatteryLevel(
                time, time, batteryPct, isPlugged, batteryStatus));
    }

    @Override
    public void close() throws IOException {
        sensorManager.unregisterListener(this);
        wakeLock.release();
        super.close();
    }

    public void setSensorDelays(SparseIntArray sensorDelays) {
        this.sensorDelays.clear();
        for (int i = 0; i < sensorDelays.size(); i++) {
            this.sensorDelays.put(sensorDelays.keyAt(i), sensorDelays.valueAt(i));
        }
        if (getState().getStatus() == DeviceStatusListener.Status.CONNECTED) {
            sensorManager.unregisterListener(this);
            registerSensors();
        }
    }
}
