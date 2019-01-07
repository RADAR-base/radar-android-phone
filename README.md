# Basic phone sensors RADAR-pRMT

A plugin for the RADAR pRMT app. The plugin can be used on an Android 5.0 (or later) device. This collects many types of data from an Android device.

## Installation

First, add the plugin code to your application:

```gradle
repositories {
    jcenter()
}

dependencies {
    runtimeOnly 'org.radarcns:radar-android-phone:0.2.0'
    runtimeOnly 'org.radarcns:radar-android-phone-usage:0.2.0'
    runtimeOnly 'org.radarcns:radar-android-phone-telephony:0.2.0'
}
```

## Configuration

This plugin contains six services, to enable them add their provider to the `device_services_to_connect` property of the configuration:

In `radar-android-phone`:
- `.phone.PhoneSensorProvider` provides a service that monitors Android hardware sensors.
- `.phone.PhoneLocationProvider` provides a service that monitors current GPS and/or network location. Location data is gathered in a relative manner, adding a random reference offset to all locations. The reference offset is not transmitted. Because the GPS sensor is generally battery-heavy, there are separate parameters for location update frequency for low battery levels and higher battery levels.
- `.phone.PhoneBluetoothProvider` provides a service that monitors bluetooth usage.
- `.phone.PhoneContactListProvider` provides a service that monitors contact list size. Phone contacts themselves are not transmitted.

In `radar-android-phone-usage`:
- `.phone.PhoneUsageProvider` provides a service that monitors application usage. Application usage events are only gathered for Android 5.1 and later.
This package requires a package usage stats permission. For possible future permission restrictions by Google Play, only include this package if usage stats are actually required.

In `radar-android-phone-telephony`:
- `.phone.PhoneLogProvider` provides a service that periodically reads the metadata of phone logs of SMSes and calls made. Phone numbers are irreversibly hashed before transmission.
This package requires call log and SMS permissions. To prevent [restrictions by Google Play](https://support.google.com/googleplay/android-developer/answer/9047303), only include this package if call and SMS logs are actually required.

### Sensors

The following Firebase parameters are available:

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| **PhoneSensorProvider** |||
| `phone_sensor_default_interval` | int (ms) | 200 | Default interval between phone sensor polls. Set to `0` to disable sensors by default. |
| `phone_sensor_gyroscope_interval` | int (ms) | 200 | Interval between phone gyroscope sensor polls. Set to `0` to disable. |
| `phone_sensor_magneticfield_interval` | int (ms) | 200 | Interval between phone magnetic field sensor polls. Set to `0` to disable.  |
| `phone_sensor_steps_interval` | int (ms) | 200 | Interval between phone step counter polls. Set to `0` to disable. |
| `phone_sensor_acceleration_interval` | int (ms) | 200 | Interval between phone acceleration sensor polls. Set to `0` to disable. |
| `phone_sensor_light_interval` | int (ms) | - | Set to `0` to disable. Note that the light sensor registers every change of illuminance and can't be set to record in a specific interval |
| `phone_sensor_battery_interval_seconds` | int (s) | 600 (= 10 minutes) | Interval between phone battery level polls. |
| **PhoneLocationProvider** |||
| `phone_location_gps_interval` | int (s) | 3600 (= 1 hour) | Interval for gathering location using the GPS sensor. Set this parameter and the next to `0` to disable GPS data gathering. | 
| `phone_location_gps_interval_reduced` | int (s) | 18000 (= 5 hours) | Interval for gathering location using the GPS sensor when the battery level is low. |
| `phone_location_network_interval` | int (s) | 600 (= 10 minutes) | Interval for gathering location using network triangulation. Set this parameter and the next to `0` to disable network location gathering. |
| `phone_location_network_interval_reduced` | int (s) | 3000 (= 50 minutes) | Interval for gathering location using network triangulation when the battery level is low. |
| `phone_location_battery_level_reduced` | float (0-1) | 0.3 (= 30%) | Battery level threshold, below which to use the reduced interval configuration. |
| `phone_location_battery_level_minimum` | float (0-1) | 0.15 (= 15%) | Battery level threshold, below which to stop gathering location data altogether. |
| **PhoneContactListProvider** |||
| `phone_contacts_list_interval_seconds` | int (s) | 86400 (= 1 day) | Interval for scanning contact list for changes. |
| **PhoneBluetoothProvider** |||
| `bluetooth_devices_scan_interval_seconds` | int (s) | 3600 (= 1 hour) | Interval for scanning Bluetooth devices. |
| **PhoneUsageProvider** |||
| `phone_usage_interval_seconds` | int (s) | 3600 (= 1 hour) | Interval for gathering Android usage stats. |
| **PhoneLogProvider** |||
| `call_sms_log_interval_seconds` | int (s) | 86400 (= 1 day) | Interval for gathering Android call/sms logs. |

This produces data to the following Kafka topics (all types are prefixed with the `org.radarcns.passive.phone` package).

| Topic | Type |
| ----- | ---- |
| **PhoneSensorProvider** ||
| `android_phone_gyroscope` | `PhoneGyroscope` |
| `android_phone_magnetic_field` | `PhoneMagneticField` |
| `android_phone_step_count` | `PhoneStepCount` |
| `android_phone_acceleration` | `PhoneAcceleration` |
| `android_phone_light` | `PhoneLight` |
| `android_phone_battery_level` | `PhoneBatteryLevel` |
| **PhoneLocationProvider** ||
| `android_phone_relative_location` | `PhoneRelativeLocation` |
| **PhoneContactListProvider** ||
| `android_phone_contacts` | `PhoneContactList` |
| **PhoneBluetoothProvider** ||
| `android_phone_bluetooth_devices` | `PhoneBluetoothDevices` |
| **PhoneUsageProvider** ||
| `android_phone_user_interaction` | `PhoneUserInteraction` |
| `android_phone_usage_event` | `PhoneUsageEvent` |
| **PhoneLogProvider** ||
| `android_phone_call` | `PhoneCall` |
| `android_phone_sms` | `PhoneSms` |
| `android_phone_sms_unread` | `PhoneSmsUnread` |

## Contributing

Code should be formatted using the [Google Java Code Style Guide](https://google.github.io/styleguide/javaguide.html), except using 4 spaces as indentation. Make a pull request once the code is working.
