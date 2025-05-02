# VarioWatch

A Wear OS application that turns your smartwatch into a variometer for paragliding and hang gliding.

## Overview

VarioWatch uses your Wear OS device's barometric sensor to detect changes in atmospheric pressure, converting these readings into audio feedback that indicates vertical speed (climb or sink rate). This makes it a valuable tool for free flight sports like paragliding and hang gliding.

## Features

- **Real-time Vertical Speed Detection**: Measures atmospheric pressure changes to calculate vertical speed
- **Audio Feedback**: 
  - Higher frequency beeps indicate climbing
  - Lower frequency tones indicate sinking
  - Faster beep intervals for stronger lift
  - Continuous tone variation based on vertical speed
- **Wear OS Optimization**: 
  - Designed for glance-able information during flight
  - Battery-efficient sensor usage
  - Clear visibility in outdoor conditions

## Technical Details

### Hardware Requirements

- Wear OS device with barometric pressure sensor
- Compatible with Wear OS 3.0 and above
- Minimum 1GB RAM recommended

### Key Components

- **Barometric Sensor**: Measures atmospheric pressure at regular intervals
- **Vertical Speed Calculator**: Converts pressure changes into vertical speed measurements
- **Audio Generator**: Creates variable frequency tones based on vertical speed
- **User Interface**: Displays current vertical speed, altitude, and basic statistics

## Usage

1. Install the app on your Wear OS device
2. Launch VarioWatch
3. Wait for sensor calibration (approximately 5 seconds)
4. The app will begin providing audio feedback:
   - Ascending: Higher pitched beeps
   - Descending: Lower pitched tones
   - Level flight: Silent

## Safety Notice

This app is designed as an auxiliary tool for free flight sports. Never rely solely on this app for critical flight decisions. Always use certified flight instruments for primary flight data.

## Development

Built with:
- Kotlin
- Wear OS SDK
- Android Jetpack libraries
- Wear Compose for UI

## License

[Your chosen license]

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.