package au.com.penattilabs.variowatch

object Constants {
    // Service constants
    const val SENSOR_SAMPLING_PERIOD_US = 100000 // 100ms sampling period (10Hz)
    const val SERVICE_NOTIFICATION_ID = 1
    const val NOTIFICATION_CHANNEL_ID = "vario_service_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Vario Service"

    // International Standard Atmosphere constants
    const val ISA_TEMPERATURE_SEA_LEVEL = 288.15f    // Standard temperature at sea level (K)
    const val ISA_PRESSURE_SEA_LEVEL = 1013.25f   // Standard pressure at sea level (hPa)
    const val ISA_TEMPERATURE_LAPSE_RATE = 0.0065f     // Temperature lapse rate (K/m)
    const val ISA_GAS_CONSTANT_AIR = 287.053f    // Gas constant for air (J/(kg·K))
    const val ISA_GRAVITATIONAL_ACCELERATION = 9.80665f    // Gravitational acceleration (m/s²)
    
    // Conversion constants
    const val METERS_TO_FEET = 3.28084f

    // Altitude adjustment steps
    const val METRIC_ALTITUDE_STEP = 10f  // meters
    const val IMPERIAL_ALTITUDE_STEP = 25f  // feet
}