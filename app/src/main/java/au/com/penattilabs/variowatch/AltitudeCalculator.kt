package au.com.penattilabs.variowatch

object AltitudeCalculator {
    fun calculateAltitude(pressure: Float, qnh: Float): Float {
        // Use the international barometric formula
        return ((Constants.ISA_TEMPERATURE_SEA_LEVEL.toDouble() / Constants.ISA_TEMPERATURE_LAPSE_RATE.toDouble()) * 
            (1 - Math.pow((pressure / qnh).toDouble(), 
                (Constants.ISA_GAS_CONSTANT_AIR.toDouble() * Constants.ISA_TEMPERATURE_LAPSE_RATE.toDouble()) / 
                Constants.ISA_GRAVITATIONAL_ACCELERATION.toDouble()))).toFloat()
    }

    fun calculateQnhFromAltitude(pressure: Float, knownAltitude: Float): Float {
        // Handle special case of zero altitude
        if (knownAltitude == 0f) return pressure
        
        // Using standard pressure lapse rate of approximately -0.12 hPa/m
        // This gives us a total change of about 125 hPa over 1000m
        val pressureDifference = knownAltitude * 0.125f
        return pressure + pressureDifference
    }

    fun formatAltitude(altitude: Float, useMetric: Boolean): String = 
        if (useMetric) {
            String.format("%.0f m", altitude)
        } else {
            String.format("%.0f ft", altitude * Constants.METERS_TO_FEET)
        }
}