package au.com.penattilabs.variowatch

/**
 * Application-wide constants including sensor configuration, physical constants, and UI settings.
 */
object Constants {
    // Service constants
    const val SENSOR_SAMPLING_PERIOD_US = 200000 // 200ms sampling period (5Hz) for better battery life
    const val SENSOR_BATCH_LATENCY_US = 1000000 // 1s batching for power efficiency
    const val SERVICE_NOTIFICATION_ID = 1
    const val NOTIFICATION_CHANNEL_ID = "vario_service_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Vario Service"

    // International Standard Atmosphere constants
    const val ISA_TEMPERATURE_SEA_LEVEL = 288.15f    // Standard temperature at sea level (K)
    const val ISA_PRESSURE_SEA_LEVEL = 1013.25f   // Standard pressure at sea level (hPa)
    const val ISA_TEMPERATURE_LAPSE_RATE = 0.0065f     // Temperature lapse rate (K/m)
    const val ISA_GAS_CONSTANT_AIR = 8.31446f    // Universal gas constant (J/(mol·K))
    const val ISA_GRAVITATIONAL_ACCELERATION = 9.80665f    // Gravitational acceleration (m/s²)
    const val ISA_MOLAR_MASS_AIR = 0.0289644f    // Molar mass of air (kg/mol)
    
    // Conversion constants
    const val METERS_TO_FEET = 3.28084f

    // Altitude adjustment steps
    const val METRIC_ALTITUDE_STEP = 10f  // meters
    const val IMPERIAL_ALTITUDE_STEP = 25f  // feet

    // Battery optimization constants
    const val SENSOR_BATCH_SIZE = 5  // Number of samples to batch before processing
    const val SENSOR_WARMUP_TIME_MS = 500L  // Time to wait for sensor to stabilize
}