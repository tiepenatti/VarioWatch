package au.com.penattilabs.variowatch

object AltitudeCalculator {
    fun calculateAltitude(pressure: Float, qnh: Float): Float {
        // Handle special case of equal pressures
        if (pressure == qnh) return 0f

        // Convert pressures to Pa
        val pressurePa = pressure * 100.0
        val qnhPa = qnh * 100.0
        
        // First calculate a rough altitude estimate using standard temperature
        // h = -(R * T0) / (g * M) * ln(P/P0)
        var altitude = (-(Constants.ISA_GAS_CONSTANT_AIR.toDouble() * Constants.ISA_TEMPERATURE_SEA_LEVEL.toDouble()) *
            Math.log(pressurePa / qnhPa) /
            (Constants.ISA_GRAVITATIONAL_ACCELERATION.toDouble() * Constants.ISA_MOLAR_MASS_AIR.toDouble())).toFloat()
        
        // Refine the calculation using temperature at altitude
        val temperatureAtAltitude = Constants.ISA_TEMPERATURE_SEA_LEVEL.toDouble() -
            (Constants.ISA_TEMPERATURE_LAPSE_RATE.toDouble() * altitude)
        
        // Recalculate with the corrected temperature
        return (-(Constants.ISA_GAS_CONSTANT_AIR.toDouble() * temperatureAtAltitude) *
            Math.log(pressurePa / qnhPa) /
            (Constants.ISA_GRAVITATIONAL_ACCELERATION.toDouble() * Constants.ISA_MOLAR_MASS_AIR.toDouble())).toFloat()
    }

    fun calculateQnhFromAltitude(pressure: Float, knownAltitude: Float): Float {
        // Handle special case of zero altitude
        if (knownAltitude == 0f) return pressure

        // Convert pressure from hPa to Pa and all values to Double for calculation
        val pressureAtAltitudePa = pressure * 100.0
        val temperatureAtAltitude = Constants.ISA_TEMPERATURE_SEA_LEVEL.toDouble() - 
            (Constants.ISA_TEMPERATURE_LAPSE_RATE.toDouble() * knownAltitude.toDouble())
        
        // Hypsometric equation for QNH calculation
        val seaLevelPressurePa = pressureAtAltitudePa * Math.exp(
            (Constants.ISA_GRAVITATIONAL_ACCELERATION.toDouble() * 
             Constants.ISA_MOLAR_MASS_AIR.toDouble() * knownAltitude.toDouble()) / 
            (Constants.ISA_GAS_CONSTANT_AIR.toDouble() * temperatureAtAltitude)
        )
        
        // Convert back to hPa
        return (seaLevelPressurePa / 100.0).toFloat()
    }

    fun formatAltitude(altitude: Float, useMetric: Boolean): String = 
        if (useMetric) {
            String.format("%.0f m", altitude)
        } else {
            String.format("%.0f ft", altitude * Constants.METERS_TO_FEET)
        }
}