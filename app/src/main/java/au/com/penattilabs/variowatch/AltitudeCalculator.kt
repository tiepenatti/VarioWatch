package au.com.penattilabs.variowatch

object AltitudeCalculator {
    // International Standard Atmosphere constants
    private const val T0 = 288.15    // Standard temperature at sea level (K)
    private const val P0 = 1013.25   // Standard pressure at sea level (hPa)
    private const val L = 0.0065     // Temperature lapse rate (K/m)
    private const val R = 287.053    // Gas constant for air (J/(kg·K))
    private const val g = 9.80665    // Gravitational acceleration (m/s²)

    fun calculateAltitude(pressure: Float, qnh: Float): Float {
        // Use the international barometric formula
        return ((T0 / L) * (1 - Math.pow((pressure / qnh).toDouble(), (R * L) / g))).toFloat()
    }

    fun calculateQnhFromAltitude(pressure: Float, knownAltitude: Float): Float {
        // Rearranged barometric formula to solve for QNH
        return pressure / Math.pow(1 - (knownAltitude * L / T0), g / (R * L)).toFloat()
    }

    fun formatAltitude(altitude: Float, useMetric: Boolean): String {
        return if (useMetric) {
            "%.0f m".format(altitude)
        } else {
            "%.0f ft".format(altitude * 3.28084)
        }
    }
}