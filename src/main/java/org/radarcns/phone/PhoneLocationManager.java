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

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;

import org.radarcns.android.data.DataCache;
import org.radarcns.android.data.TableDataHandler;
import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.android.util.PersistentStorage;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class PhoneLocationManager extends AbstractDeviceManager<PhoneLocationService, BaseDeviceState> implements LocationListener {
    private static final Logger logger = LoggerFactory.getLogger(PhoneLocationManager.class);

    // storage with keys
    private static final PersistentStorage storage = new PersistentStorage(PhoneLocationManager.class);
    private static final String LATITUDE_REFERENCE = "latitude.reference";
    private static final String LONGITUDE_REFERENCE = "longitude.reference";
    private static final String ALTITUDE_REFERENCE = "altitude.reference";

    // update intervals
    private static final long LOCATION_GPS_INTERVAL_DEFAULT = 60*60; // seconds
    private static final long LOCATION_NETWORK_INTERVAL_DEFAULT = 10*60; // seconds

    private static final Map<String, LocationProvider> PROVIDER_TYPES = new HashMap<>();

    static {
        PROVIDER_TYPES.put(LocationManager.GPS_PROVIDER, LocationProvider.GPS);
        PROVIDER_TYPES.put(LocationManager.NETWORK_PROVIDER, LocationProvider.NETWORK);
    }

    private final DataCache<MeasurementKey, PhoneRelativeLocation> locationTable;
    private final LocationManager locationManager;
    private BigDecimal latitudeReference;
    private BigDecimal longitudeReference;
    private double altitudeReference = Double.NaN;
    private final HandlerThread handlerThread;
    private Handler handler;

    public PhoneLocationManager(PhoneLocationService context, TableDataHandler dataHandler, String groupId, String sourceId) {
        super(context, new BaseDeviceState(), dataHandler, groupId, sourceId);
        this.locationTable = dataHandler.getCache(PhoneLocationTopics.getInstance().getRelativeLocationTopic());

        locationManager = (LocationManager) getService().getSystemService(Context.LOCATION_SERVICE);
        this.handlerThread = new HandlerThread("PhoneLocation", Process.THREAD_PRIORITY_BACKGROUND);

        setName(android.os.Build.MODEL);
        updateStatus(DeviceStatusListener.Status.READY);
    }

    @Override
    public void start(@NonNull Set<String> set) {
        this.handlerThread.start();
        this.handler = new Handler(this.handlerThread.getLooper());

        // Location
        setLocationUpdateRate(LOCATION_GPS_INTERVAL_DEFAULT, LOCATION_NETWORK_INTERVAL_DEFAULT);
        updateStatus(DeviceStatusListener.Status.CONNECTED);
    }

    public void onLocationChanged(Location location) {
        if (location == null) {
            return;
        }

        double latitude;
        double longitude;
        float altitude;

        try {
            // Coordinates in degrees from a new (random) reference point
            latitude = getRelativeLatitude(location.getLatitude());
            longitude = getRelativeLongitude(location.getLongitude());
            altitude = location.hasAltitude() ? getRelativeAltitude(location.getAltitude()) : Float.NaN;
        } catch (IOException ex) {
            logger.error("Failed to process location {}: relative location could not be stored", location, ex);
            return;
        }

        double eventTimestamp = location.getTime() / 1000d;
        double timestamp = System.currentTimeMillis() / 1000d;

        LocationProvider provider = PROVIDER_TYPES.get(location.getProvider());
        if (provider == null) {
            provider = LocationProvider.OTHER;
        }

        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.NaN;
        float speed = location.hasSpeed() ? location.getSpeed() : Float.NaN;
        float bearing = location.hasBearing() ? location.getBearing() : Float.NaN;

        PhoneRelativeLocation value = new PhoneRelativeLocation(
                eventTimestamp, timestamp, provider,
                latitude, longitude,
                altitude, accuracy, speed, bearing);
        send(locationTable, value);

        logger.info("Location: {} {} {} {} {} {} {} {} {}", provider, eventTimestamp, latitude,
                longitude, accuracy, altitude, speed, bearing, timestamp);
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {}

    public void onProviderEnabled(String provider) {}

    public void onProviderDisabled(String provider) {}

    public final synchronized void setLocationUpdateRate(final long periodGPS, final long periodNetwork) {
        handler.post(new Runnable() {
             @Override
             public void run() {
                 // Remove updates, if any
                 locationManager.removeUpdates(PhoneLocationManager.this);

                 // Initialize with last known and start listening
                 if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                     onLocationChanged(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
                     locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, periodGPS * 1000, 0, PhoneLocationManager.this);
                     logger.info("Location GPS listener activated and set to a period of {}", periodGPS);
                 } else {
                     logger.warn("Location GPS listener not found");
                 }

                 if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                     onLocationChanged(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
                     locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, periodNetwork * 1000, 0, PhoneLocationManager.this);
                     logger.info("Location Network listener activated and set to a period of {}", periodNetwork);
                 } else {
                     logger.warn("Location Network listener not found");
                 }
             }
         });
    }

    private double getRelativeLatitude(double absoluteLatitude) throws IOException {
        if (Double.isNaN(absoluteLatitude)) {
            return Double.NaN;
        }
        BigDecimal latitude = BigDecimal.valueOf(absoluteLatitude);
        if (latitudeReference == null) {
            latitudeReference = new BigDecimal(
                    storage.getOrSet(LATITUDE_REFERENCE, latitude.toString()));
        }
        return latitude.subtract(latitudeReference).doubleValue();
    }

    private double getRelativeLongitude(double absoluteLongitude) throws IOException {
        if (Double.isNaN(absoluteLongitude)) {
            return Double.NaN;
        }
        BigDecimal longitude = BigDecimal.valueOf(absoluteLongitude);
        if (longitudeReference == null) {
            longitudeReference = new BigDecimal(
                    storage.getOrSet(LONGITUDE_REFERENCE, longitude.toString()));
        }
        return longitude.subtract(longitudeReference).doubleValue();
    }

    private float getRelativeAltitude(double absoluteAltitude) throws IOException {
        if (Double.isNaN(absoluteAltitude)) {
            return Float.NaN;
        }
        if (Double.isNaN(altitudeReference)) {
            altitudeReference = Double.parseDouble(storage.getOrSet(ALTITUDE_REFERENCE, Double.toString(absoluteAltitude)));
        }
        return (float)(absoluteAltitude - altitudeReference);
    }

    public void close() throws IOException {
        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    locationManager.removeUpdates(PhoneLocationManager.this);
                }
            });
            handler = null;
            handlerThread.quitSafely();
        }

        super.close();
    }
}
