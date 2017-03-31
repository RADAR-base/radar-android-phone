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
import android.support.annotation.NonNull;
import android.util.SparseArray;

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

import static android.os.BatteryManager.BATTERY_STATUS_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_DISCHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;

/** Manages Phone sensors */
class PhoneSensorManager extends AbstractDeviceManager<PhoneSensorService, PhoneState> implements DeviceManager, SensorEventListener {
    private static final Logger logger = LoggerFactory.getLogger(PhoneSensorManager.class);

    private static final float EARTH_GRAVITATIONAL_ACCELERATION = 9.80665f;
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
    private final AvroTopic<MeasurementKey, PhoneBatteryLevel> batteryTopic;
    private final DataCache<MeasurementKey, PhoneUserInteraction> userInteractionTable;

    private SensorManager sensorManager;

    public PhoneSensorManager(PhoneSensorService context, TableDataHandler dataHandler, String groupId, String sourceId) {
        super(context, new PhoneState(), dataHandler, groupId, sourceId);
        PhoneSensorTopics topics = PhoneSensorTopics.getInstance();
        this.accelerationTable = dataHandler.getCache(topics.getAccelerationTopic());
        this.lightTable = dataHandler.getCache(topics.getLightTopic());
        this.userInteractionTable = dataHandler.getCache(topics.getUserInteractionTopic());
        this.batteryTopic = topics.getBatteryLevelTopic();

        sensorManager = null;
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.

        setName(android.os.Build.MODEL);
    }

    @Override
    public void start(@NonNull final Set<String> acceptableIds) {
        sensorManager = (SensorManager) getService().getSystemService(Context.SENSOR_SERVICE);

        // Accelerometer
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            logger.warn("Phone Accelerometer not found");
        }

        // Light
        if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) {
            Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            logger.warn("Phone Light sensor not found");
        }

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

        // Screen active
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_USER_PRESENT);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        getService().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_USER_PRESENT) ||
                    intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    processInteractionState(intent);
                }
            }
        }, screenStateFilter);

        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if ( event.sensor.getType() == Sensor.TYPE_ACCELEROMETER ) {
            processAcceleration(event);
        } else if ( event.sensor.getType() == Sensor.TYPE_LIGHT ) {
            processLight(event);
        } else {
            logger.info("Phone registered other sensor change: '{}'", event.sensor.getType());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no action
    }

    public void processAcceleration(SensorEvent event) {
        // x,y,z are in m/s2
        float x = event.values[0] / EARTH_GRAVITATIONAL_ACCELERATION;
        float y = event.values[1] / EARTH_GRAVITATIONAL_ACCELERATION;
        float z = event.values[2] / EARTH_GRAVITATIONAL_ACCELERATION;
        getState().setAcceleration(x, y, z);
        
        double timeReceived = System.currentTimeMillis() / 1_000d;
        
        // nanoseconds uptime to seconds utc by calculating
        // current timestamp minus difference between current uptime and uptime at sensor event.
        // this accounts for the event happing slightly before processing it here
        double time = ( timeReceived - (System.nanoTime() - event.timestamp) / 1_000_000_000d );    

        send(accelerationTable, new PhoneAcceleration(time, timeReceived, x, y, z));
    }

    public void processLight(SensorEvent event) {
        float lightValue = event.values[0];
        getState().setLight(lightValue);
        
        double timeReceived = System.currentTimeMillis() / 1000d;
        
        // nanoseconds uptime to seconds utc
        double time = ( timeReceived - (System.nanoTime() - event.timestamp) / 1_000_000_000d );      

        send(lightTable, new PhoneLight(time, timeReceived, lightValue));
    }

    public void processBatteryStatus(Intent intent) {
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

    public void processInteractionState(Intent intent) {
        PhoneLockState state;

        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            state = PhoneLockState.STANDBY;
        } else {
            state = PhoneLockState.UNLOCKED;
        }

        double timestamp = System.currentTimeMillis() / 1000d;
        PhoneUserInteraction value = new PhoneUserInteraction(
                timestamp, timestamp, state);
        send(userInteractionTable, value);

        logger.info("Interaction State: {} {}", timestamp, state);
    }

    @Override
    public void close() throws IOException {
        sensorManager.unregisterListener(this);
        super.close();
    }
}
