package au.com.penattilabs.variowatch

/**
 * Calculator for altitude-related calculations using the International Standard Atmosphere (ISA) model.
 * 
 * This class implements the barometric formula with temperature correction. It provides functions
 * for calculating altitude from pressure readings and QNH (atmospheric pressure adjusted to sea level),
 * as well as calculating QNH from a known altitude.
 *
 * The calculations use the following ISA standard values:
 * - Temperature at sea level: 288.15 K (15°C)
 * - Pressure at sea level: 1013.25 hPa
 * - Temperature lapse rate: 0.0065 K/m
 * - Gravitational acceleration: 9.80665 m/s²
 * - Gas constant for air: 8.31446 J/(mol·K)
 * - Molar mass of air: 0.0289644 kg/mol
 */
object AltitudeCalculator {
    private const val HPA_TO_PA = 100.0
    private const val PA_TO_HPA = 0.01
    
    /**
     * Calculates altitude using the temperature-corrected barometric formula.
     * 
     * The calculation is performed in two steps:
     * 1. Calculate a rough altitude estimate using standard sea level temperature
     * 2. Refine the calculation using the estimated temperature at that altitude
     *
     * @param pressure Current atmospheric pressure in hPa
     * @param qnh Reference pressure (QNH) in hPa
     * @return Calculated altitude in meters
     */
    fun calculateAltitude(pressure: Float, qnh: Float): Float {
        // Handle special case of equal pressures
        if (pressure == qnh) return 0f

        // Convert pressures to Pa
        val pressurePa = pressure * HPA_TO_PA
        val qnhPa = qnh * HPA_TO_PA
        
        // First calculate a rough altitude estimate using standard temperature
        var altitude = calculateBasicAltitude(pressurePa, qnhPa)
        
        // Refine the calculation using temperature at altitude
        val temperatureAtAltitude = calculateTemperatureAtAltitude(altitude)
        
        // Recalculate with the corrected temperature
        return calculateCorrectedAltitude(pressurePa, qnhPa, temperatureAtAltitude)
    }

    /**
     * Calculates QNH from a known altitude and pressure.
     * 
     * This function uses the inverse of the barometric formula to determine what the sea level
     * pressure (QNH) would be, given the current pressure reading and known altitude.
     *
     * @param pressure Current atmospheric pressure in hPa
     * @param knownAltitude Known altitude in meters
     * @return Calculated QNH (sea level pressure) in hPa
     */
    fun calculateQnhFromAltitude(pressure: Float, knownAltitude: Float): Float {
        // Handle special case of zero altitude
        if (knownAltitude == 0f) return pressure

        // Convert pressure from hPa to Pa
        val pressureAtAltitudePa = pressure * HPA_TO_PA
        val temperatureAtAltitude = calculateTemperatureAtAltitude(knownAltitude)
        
        // Hypsometric equation for QNH calculation
        val seaLevelPressurePa = calculateSeaLevelPressure(
            pressureAtAltitudePa,
            knownAltitude,
            temperatureAtAltitude
        )
        
        // Convert back to hPa
        return (seaLevelPressurePa * PA_TO_HPA).toFloat()
    }

    /**
     * Formats an altitude value according to the selected unit system.
     * 
     * @param altitude Altitude in meters
     * @param useMetric Whether to use metric (true) or imperial (false) units
     * @return Formatted string with appropriate unit (m or ft)
     */
    fun formatAltitude(altitude: Float, useMetric: Boolean): String = 
        if (useMetric) {
            String.format("%.0f m", altitude)
        } else {
            String.format("%.0f ft", altitude * Constants.METERS_TO_FEET)
        }

    /**
     * Calculates the initial altitude estimate using standard sea level temperature.
     */
    private fun calculateBasicAltitude(pressurePa: Double, qnhPa: Double): Float {
        return (-(Constants.ISA_GAS_CONSTANT_AIR * Constants.ISA_TEMPERATURE_SEA_LEVEL) *
            Math.log(pressurePa / qnhPa) /
            (Constants.ISA_GRAVITATIONAL_ACCELERATION * Constants.ISA_MOLAR_MASS_AIR)).toFloat()
    }

    /**
     * Calculates the temperature at a given altitude using the temperature lapse rate.
     */
    private fun calculateTemperatureAtAltitude(altitude: Float): Double {
        return Constants.ISA_TEMPERATURE_SEA_LEVEL.toDouble() -
            (Constants.ISA_TEMPERATURE_LAPSE_RATE * altitude)
    }

    /**
     * Calculates the corrected altitude using the temperature at the estimated altitude.
     */
    private fun calculateCorrectedAltitude(pressurePa: Double, qnhPa: Double, temperatureAtAltitude: Double): Float {
        return (-(Constants.ISA_GAS_CONSTANT_AIR * temperatureAtAltitude) *
            Math.log(pressurePa / qnhPa) /
            (Constants.ISA_GRAVITATIONAL_ACCELERATION * Constants.ISA_MOLAR_MASS_AIR)).toFloat()
    }

    /**
     * Calculates sea level pressure using the hypsometric formula.
     */
    private fun calculateSeaLevelPressure(
        pressureAtAltitudePa: Double,
        altitude: Float,
        temperatureAtAltitude: Double
    ): Double {
        return pressureAtAltitudePa * Math.exp(
            (Constants.ISA_GRAVITATIONAL_ACCELERATION * 
             Constants.ISA_MOLAR_MASS_AIR * altitude) / 
            (Constants.ISA_GAS_CONSTANT_AIR * temperatureAtAltitude)
        )
    }
}