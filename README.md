# Basic phone sensors RADAR-pRMT

Application to be run on an Android 4.4 (or later) device. This collects data from the Phone light sensor, battery status and acceleration.



## Installation

First, add the plugin code to your application:

```gradle
repositories {
    maven { url  'http://dl.bintray.com/radar-cns/org.radarcns' }
}

dependencies {
    compile 'org.radarcns:radar-android-phone:0.1.1'
}
```

This plugin contains three services, to enable them add their provider to the `device_service_to_connect` property of the configuration:

- `PhoneSensorProvider` provides a service that monitors battery level, light and user interaction.
- `PhoneLogProvider` provides a service that periodically reads the phone logs of SMSes and calls made.
- `PhoneLocationProvider` provides a service that monitors current GPS and/or network location.
- `PhoneUsageProvider` provides a service that monitors application usage.

## Contributing

Code should be formatted using the [Google Java Code Style Guide](https://google.github.io/styleguide/javaguide.html), except using 4 spaces as indentation. Make a pull request once the code is working.
