package au.com.penattilabs.variowatch

/**
 * Application-wide constants including sensor configuration, physical constants, and UI settings.
 */
object Constants {
    // Service constants
    const val SENSOR_SAMPLING_PERIOD_US_HIGH = 100000 // 100ms sampling period (10Hz) for better feedback
    const val SENSOR_SAMPLING_PERIOD_US_LOW = 333000 // 333ms sampling period (3Hz) for better battery life
    const val SENSOR_SAMPLING_PERIOD_US = SENSOR_SAMPLING_PERIOD_US_HIGH
    const val SENSOR_BATCH_LATENCY_US_HIGH = 1000000 // 1s batching for power efficiency
    const val SENSOR_BATCH_LATENCY_US_LOW = 100000 // .1s batching for performance
    const val SENSOR_BATCH_LATENCY_US = SENSOR_BATCH_LATENCY_US_LOW
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
    const val METERS_PER_SECOND_TO_FEET_PER_MINUTE = METERS_TO_FEET * 60 // Added constant

    // Altitude adjustment steps
    const val METRIC_ALTITUDE_STEP = 10f  // meters
    const val IMPERIAL_ALTITUDE_STEP = 25f  // feet

    // Battery optimization constants
    const val SENSOR_BATCH_SIZE = 5  // Number of samples to batch before processing
    const val SENSOR_WARMUP_TIME_MS = 500L  // Time to wait for sensor to stabilize
}

object SoundConstants {
    const val CLIMB_THRESHOLD_MS = 0.1f // m/s
    const val SINK_THRESHOLD_MS = -2.0f // m/s
    const val SINK_ALARM_THRESHOLD_MS = -10.0f // m/s, Example value, 0 means off
    const val BASE_FREQUENCY_HZ = 700
    const val FREQUENCY_INCREMENT_HZ_PER_TENTH_MS = 10
    const val SAMPLE_RATE = 44100 // Standard sample rate for audio
    const val VOLUME_LEVELS = 6 // 0 (off) to 6
    const val DEFAULT_VOLUME = 4 // Example default volume level

    // New constants for beeping behavior
    const val BEEP_DURATION_MS = 150L // Duration of a single beep in milliseconds
    const val SILENCE_DURATION_MS = 350L // Duration of silence between beeps in milliseconds
}