# Basic phone sensors RADAR-pRMT

Application to be run on an Android 4.4 (or later) device. This collects data from the Phone light sensor, battery status and acceleration.



## Installation

First, add the plugin code to your application:

```gradle
repositories {
    maven { url  'http://dl.bintray.com/radar-cns/org.radarcns' }
}

dependencies {
    compile 'org.radarcns:radar-android-phone:0.1.2'
}
```

## Configuration

This plugin contains six services, to enable them add their provider to the `device_service_to_connect` property of the configuration:

- `PhoneSensorProvider` provides a service that monitors battery level, light and user interaction.
- `PhoneLogProvider` provides a service that periodically reads the phone logs of SMSes and calls made.
- `PhoneLocationProvider` provides a service that monitors current GPS and/or network location.
- `PhoneUsageProvider` provides a service that monitors application usage.
- `PhoneBluetoothProvider` provides a service that monitors bluetooth usage.
- `PhoneContactListProvider` provides a service that monitors contact list size.

### Sensors

The following Firebase parameters are available:

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `phone_sensor_default_interval` | int (ms) | 200 | Default interval between phone sensor polls. Set to `0` to disable sensors by default. |
| `phone_sensor_gyroscope_interval` | int (ms) | 200 | Interval between phone gyroscope sensor polls. Set to `0` to disable. |
| `phone_sensor_magneticfield_interval` | int (ms) | 200 | Interval between phone magnetic field sensor polls. Set to `0` to disable.  |
| `phone_sensor_steps_interval` | int (ms) | 200 | Interval between phone step counter polls. Set to `0` to disable. |
| `phone_sensor_acceleration_interval` | int (ms) | 200 | Interval between phone acceleration sensor polls. Set to `0` to disable. |
| `phone_sensor_light_interval` | int (ms) | 200 | Interval between phone light sensor polls. Set to `0` to disable. |

This produces data to the following Kafka topics (all types are prefixed with the `org.radarcns.passive.phone` package).

| Topic | Type |
| ----- | ---- |
| `android_phone_gyroscope` | `PhoneGyroscope` |
| `android_phone_magnetic_field` | `PhoneMagneticField` |
| `android_phone_step_count` | `PhoneStepCount` |
| `android_phone_acceleration` | `PhoneAcceleration` |
| `android_phone_light` | `PhoneLight` |
| `android_phone_battery_level` | `PhoneBatteryLevel` |

### Call/SMS metadata logging

The following Firebase parameters are available:

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `call_sms_log_interval_seconds` | int (s) | 86400 (= 1 day) | Interval for gathering Android call/sms logs. |

This produces data to the following Kafka topics (all types are prefixed with the `org.radarcns.passive.phone` package).

| Topic | Type |
| ----- | ---- |
| `android_phone_call` | `PhoneCall` |
| `android_phone_sms` | `PhoneSms` |
| `android_phone_sms_unread` | `PhoneSmsUnread` |

### Location

Location data is gathered in a relative manner, adding a random reference offset to all locations. The reference offset is not transmitted. Because the GPS sensor is generally battery-heavy, there are separate parameters for location update frequency for low battery levels and higher battery levels.

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `phone_location_gps_interval` | int (s) | 3600 (= 1 hour) | Interval for gathering location using the GPS sensor. | 
| `phone_location_gps_interval_reduced` | int (s) | 18000 (= 5 hours) | Interval for gathering location using the GPS sensor when the battery level is low. |
| `phone_location_network_interval` | int (s) | 600 (= 10 minutes) | Interval for gathering location using network triangulation. |
| `phone_location_network_interval_reduced` | int (s) | 3000 (= 50 minutes) | Interval for gathering location using network triangulation when the battery level is low. |
| `phone_location_battery_level_reduced` | float (0-1) | 0.3 (= 30%) | Battery level threshold, below which to use the reduced interval configuration. |
| `phone_location_battery_level_minimum` | float (0-1) | 0.15 (= 15%) | Battery level threshold, below which to stop gathering location data altogether. |

This produces data to the following Kafka topics (all types are prefixed with the `org.radarcns.passive.phone` package).

| Topic | Type |
| ----- | ---- |
| `android_phone_relative_location` | `PhoneRelativeLocation` |

### Phone usage

The following Firebase parameters are available:

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `phone_usage_interval_seconds` | int (s) | 3600 (= 1 hour) | Interval for gathering Android usage stats. |


This produces data to the following Kafka topics (all types are prefixed with the `org.radarcns.passive.phone` package).

| Topic | Type |
| ----- | ---- |
| `android_phone_user_interaction` | `PhoneUserInteraction` |
| `android_phone_usage_event` | `PhoneUsageEvent` |

Note that usage events are only gathered for Android 5.1 and later.

### Bluetooth

The following Firebase parameters are available:

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `bluetooth_devices_scan_interval_seconds` | int (s) | 3600 (= 1 hour) | Interval for scanning Bluetooth devices. |

This produces data to the following Kafka topics (all types are prefixed with the `org.radarcns.passive.phone` package).

| Topic | Type |
| ----- | ---- |
| `android_phone_bluetooth_devices` | `PhoneBluetoothDevices` |


### Contact list

The following Firebase parameters are available:

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `phone_contacts_list_interval_seconds` | int (s) | 86400 (= 1 day) | Interval for scanning contact list for changes. |

This produces data to the following Kafka topics (all types are prefixed with the `org.radarcns.passive.phone` package).

| Topic | Type |
| ----- | ---- |
| `android_phone_contacts` | `PhoneContactList` |

## Contributing

Code should be formatted using the [Google Java Code Style Guide](https://google.github.io/styleguide/javaguide.html), except using 4 spaces as indentation. Make a pull request once the code is working.
