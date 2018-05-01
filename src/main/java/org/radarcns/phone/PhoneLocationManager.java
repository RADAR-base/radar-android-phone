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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.util.BatteryLevelReceiver;
import org.radarcns.kafka.ObservationKey;
import org.radarcns.passive.phone.LocationProvider;
import org.radarcns.passive.phone.PhoneRelativeLocation;
import org.radarcns.topic.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

class PhoneLocationManager extends AbstractDeviceManager<PhoneLocationService, BaseDeviceState> implements LocationListener, BatteryLevelReceiver.BatteryLevelListener {
    private static final Logger logger = LoggerFactory.getLogger(PhoneLocationManager.class);

    private static final int FREQUENCY_OFF = 1;
    private static final int FREQUENCY_REDUCED = 2;
    private static final int FREQUENCY_NORMAL = 3;

    // storage with keys
    private static final String LATITUDE_REFERENCE = "latitude.reference";
    private static final String LONGITUDE_REFERENCE = "longitude.reference";
    private static final String ALTITUDE_REFERENCE = "altitude.reference";

    private static final Map<String, LocationProvider> PROVIDER_TYPES = new HashMap<>();

    static {
        PROVIDER_TYPES.put(LocationManager.GPS_PROVIDER, LocationProvider.GPS);
        PROVIDER_TYPES.put(LocationManager.NETWORK_PROVIDER, LocationProvider.NETWORK);
    }

    private final AvroTopic<ObservationKey, PhoneRelativeLocation> locationTopic;
    private final LocationManager locationManager;
    private final BatteryLevelReceiver batteryLevelReceiver;
    private BigDecimal latitudeReference;
    private BigDecimal longitudeReference;
    private double altitudeReference;
    private final HandlerThread handlerThread;
    private Handler handler;
    private int frequency;
    private float batteryLevelMinimum;
    private float batteryLevelReduced;
    private int gpsInterval;
    private int gpsIntervalReduced;
    private int networkInterval;
    private int networkIntervalReduced;
    private boolean isStarted;

    public PhoneLocationManager(PhoneLocationService context) {
        super(context);
        this.locationTopic = createTopic("android_phone_relative_location", PhoneRelativeLocation.class);

        locationManager = (LocationManager) getService().getSystemService(Context.LOCATION_SERVICE);
        this.handlerThread = new HandlerThread("PhoneLocation", Process.THREAD_PRIORITY_BACKGROUND);

        batteryLevelReceiver = new BatteryLevelReceiver(context, this);
        this.frequency = FREQUENCY_OFF;

        initializeReferences();

        isStarted = false;
        setName(String.format(context.getString(R.string.location_manager_name),
                android.os.Build.MODEL));
    }

    private SharedPreferences getPreferences() {
        return getService().getSharedPreferences(PhoneLocationService.class.getName(), Context.MODE_PRIVATE);
    }

    private void initializeReferences() {
        SharedPreferences preferences = getPreferences();
        String latitudeString = preferences.getString(LATITUDE_REFERENCE, null);
        latitudeReference = latitudeString != null ? new BigDecimal(latitudeString) : null;

        String longitudeString = preferences.getString(LONGITUDE_REFERENCE, null);
        longitudeReference = longitudeString != null ? new BigDecimal(longitudeString) : null;

        try {
            altitudeReference = Double.longBitsToDouble(preferences.getLong(ALTITUDE_REFERENCE, Double.doubleToLongBits(Double.NaN)));
        } catch (ClassCastException ex) {
            // to fix bug where this was stored as String
            altitudeReference = Double.valueOf(preferences.getString(ALTITUDE_REFERENCE, "-10000.0"));
            if (altitudeReference == -10000.0) {
                altitudeReference = Double.NaN;
            }
            preferences.edit()
                    .putLong(ALTITUDE_REFERENCE, Double.doubleToLongBits(altitudeReference))
                    .apply();
        }
    }

    @Override
    public void start(@NonNull Set<String> set) {
        this.handlerThread.start();
        this.handler = new Handler(this.handlerThread.getLooper());

        updateStatus(DeviceStatusListener.Status.READY);

        handler.post(new Runnable() {
            @Override
            public void run() {
                batteryLevelReceiver.register();
                updateStatus(DeviceStatusListener.Status.CONNECTED);
                isStarted = true;
            }
        });
    }

    public void onLocationChanged(Location location) {
        if (location == null) {
            return;
        }

        double eventTimestamp = location.getTime() / 1000d;
        double timestamp = System.currentTimeMillis() / 1000d;

        LocationProvider provider = PROVIDER_TYPES.get(location.getProvider());
        if (provider == null) {
            provider = LocationProvider.OTHER;
        }

        // Coordinates in degrees from the first coordinate registered
        Double latitude = normalizeFloating(getRelativeLatitude(location.getLatitude()));
        Double longitude = normalizeFloating(getRelativeLongitude(location.getLongitude()));
        Float altitude = normalizeFloating(location.hasAltitude() ? getRelativeAltitude(location.getAltitude()) : Float.NaN);
        Float accuracy = normalizeFloating(location.hasAccuracy() ? location.getAccuracy() : Float.NaN);
        Float speed = normalizeFloating(location.hasSpeed() ? location.getSpeed() : Float.NaN);
        Float bearing = normalizeFloating(location.hasBearing() ? location.getBearing() : Float.NaN);

        PhoneRelativeLocation value = new PhoneRelativeLocation(
                eventTimestamp, timestamp, provider,
                latitude, longitude,
                altitude, accuracy, speed, bearing);
        send(locationTopic, value);

        logger.info("Location: {} {} {} {} {} {} {} {} {}", provider, eventTimestamp, latitude,
                longitude, accuracy, altitude, speed, bearing, timestamp);
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {}

    public void onProviderEnabled(String provider) {}

    public void onProviderDisabled(String provider) {}

    public synchronized void setLocationUpdateRate(final long periodGPS, final long periodNetwork) {
        handler.post(new Runnable() {
             @SuppressLint("MissingPermission")
             @Override
             public void run() {
                 if (!isStarted) {
                     return;
                 }

                 // Remove updates, if any
                 locationManager.removeUpdates(PhoneLocationManager.this);

                 // Initialize with last known and start listening
                 if (periodGPS <= 0) {
                     logger.info("Location GPS gathering disabled in settings");
                 } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                     onLocationChanged(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
                     locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, periodGPS * 1000, 0, PhoneLocationManager.this);
                     logger.info("Location GPS listener activated and set to a period of {}", periodGPS);
                 } else {
                     logger.warn("Location GPS listener not found");
                 }

                 if (periodNetwork <= 0) {
                     logger.info("Location network gathering disabled in settings");
                 } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                     onLocationChanged(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
                     locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, periodNetwork * 1000, 0, PhoneLocationManager.this);
                     logger.info("Location Network listener activated and set to a period of {}", periodNetwork);
                 } else {
                     logger.warn("Location Network listener not found");
                 }
             }
         });
    }

    /** Replace special float values with regular numbers. */
    @Nullable
    private static Double normalizeFloating(double orig) {
        if (Double.isNaN(orig)) {
            return null;
        } else if (orig == Double.NEGATIVE_INFINITY) {
            return -1e308;
        } else if (orig == Double.POSITIVE_INFINITY) {
            return 1e308;
        } else {
            return orig;
        }
    }

    /** Replace special float values with regular numbers. */
    @Nullable
    private static Float normalizeFloating(float orig) {
        if (Float.isNaN(orig)) {
            return null;
        } else if (orig == Float.NEGATIVE_INFINITY) {
            return -3e38f;
        } else if (orig == Float.POSITIVE_INFINITY) {
            return 3e38f;
        } else {
            return orig;
        }
    }

    private double getRelativeLatitude(double absoluteLatitude) {
        if (Double.isNaN(absoluteLatitude)) {
            return Double.NaN;
        }

        BigDecimal latitude = BigDecimal.valueOf(absoluteLatitude);
        if (latitudeReference == null) {
            // Create reference within 8 degrees of actual latitude
            // corresponds mildly with the UTM zones used to make flat coordinates estimations.
            double reference = ThreadLocalRandom.current().nextDouble(-4, 4); // interval [-4,4)
            latitudeReference = BigDecimal.valueOf(reference);

            getPreferences().edit()
                    .putString(LATITUDE_REFERENCE, latitudeReference.toString())
                    .apply();
        }

        return latitude.subtract(latitudeReference).doubleValue();
    }

    private double getRelativeLongitude(double absoluteLongitude) {
        if (Double.isNaN(absoluteLongitude)) {
            return Double.NaN;
        }
        BigDecimal longitude = BigDecimal.valueOf(absoluteLongitude);
        if (longitudeReference == null) {
            longitudeReference = longitude;

            getPreferences().edit()
                    .putString(LONGITUDE_REFERENCE, longitudeReference.toString())
                    .apply();
        }

        double relativeLongitude = longitude.subtract(longitudeReference).doubleValue();

        // Wraparound if relative longitude outside range of valid values [-180,180]
        // assumption: relative longitude in interval [-540,540]
        if (relativeLongitude > 180d) {
            return relativeLongitude - 360d;
        } else if (relativeLongitude < -180d) {
            return relativeLongitude + 360d;
        }

        return relativeLongitude;
    }

    private float getRelativeAltitude(double absoluteAltitude) {
        if (Double.isNaN(absoluteAltitude)) {
            return Float.NaN;
        }
        if (Double.isNaN(altitudeReference)) {
            altitudeReference = absoluteAltitude;

            getPreferences().edit()
                    .putLong(ALTITUDE_REFERENCE, Double.doubleToLongBits(altitudeReference))
                    .apply();
        }
        return (float)(absoluteAltitude - altitudeReference);
    }

    @Override
    public void onBatteryLevelChanged(float level, boolean isPlugged) {
        if (handler == null) {
            return;
        }

        long useGpsInterval;
        long useNetworkInterval;
        int newFrequency;

        synchronized (this) {
            if (isPlugged || level >= batteryLevelReduced) {
                newFrequency = FREQUENCY_NORMAL;
            } else if (level >= batteryLevelMinimum) {
                newFrequency = FREQUENCY_REDUCED;
            } else {
                newFrequency = FREQUENCY_OFF;
            }

            if (frequency == newFrequency) {
                return;
            }
            frequency = newFrequency;

            if (frequency == FREQUENCY_NORMAL) {
                useGpsInterval = gpsInterval;
                useNetworkInterval = networkInterval;
            } else {
                useGpsInterval = gpsIntervalReduced;
                useNetworkInterval = networkIntervalReduced;
            }
        }

        if (frequency == FREQUENCY_OFF) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    locationManager.removeUpdates(PhoneLocationManager.this);
                }
            });
        } else {
            setLocationUpdateRate(useGpsInterval, useNetworkInterval);
        }
    }

    @Override
    public void close() throws IOException {
        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    batteryLevelReceiver.unregister();
                    locationManager.removeUpdates(PhoneLocationManager.this);
                }
            });
            handler = null;
            handlerThread.quitSafely();
        }

        super.close();
    }

    public synchronized void setBatteryLevels(float batteryLevelMinimum, float batteryLevelReduced) {
        if (this.batteryLevelMinimum == batteryLevelMinimum
                && this.batteryLevelReduced == batteryLevelReduced) {
            return;
        }
        this.batteryLevelMinimum = batteryLevelMinimum;
        this.batteryLevelReduced = batteryLevelReduced;
        this.onBatteryLevelChanged(batteryLevelReceiver.getLevel(), batteryLevelReceiver.isPlugged());
    }

    public synchronized void setIntervals(int gpsInterval, int gpsIntervalReduced, int networkInterval, int networkIntervalReduced) {
        if (this.gpsInterval == gpsInterval
                && this.gpsIntervalReduced == gpsIntervalReduced
                && this.networkInterval == networkInterval
                && this.networkIntervalReduced == networkIntervalReduced) {
            return;
        }

        this.gpsInterval = gpsInterval;
        this.gpsIntervalReduced = gpsIntervalReduced;
        this.networkInterval = networkInterval;
        this.networkIntervalReduced = networkIntervalReduced;

        // reset intervals
        this.frequency = -1;
        this.onBatteryLevelChanged(batteryLevelReceiver.getLevel(), batteryLevelReceiver.isPlugged());
    }
}
