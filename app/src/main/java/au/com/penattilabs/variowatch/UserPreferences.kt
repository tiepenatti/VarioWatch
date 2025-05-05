package au.com.penattilabs.variowatch

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "vario_prefs", Context.MODE_PRIVATE
    )

    var useMetricUnits: Boolean
        get() = sharedPreferences.getBoolean("use_metric_units", true)
        private set(value) = sharedPreferences.edit().putBoolean("use_metric_units", value).apply()

    var qnh: Float
        get() = sharedPreferences.getFloat("qnh", Constants.ISA_PRESSURE_SEA_LEVEL)
        private set(value) = sharedPreferences.edit().putFloat("qnh", value).apply()

    var currentAltitude: Float = 0f
        private set

    fun toggleUnitSystem() {
        useMetricUnits = !useMetricUnits
    }

    fun updateQnh(newQnh: Float) {
        qnh = newQnh
    }

    fun updateCurrentAltitude(pressure: Float) {
        currentAltitude = AltitudeCalculator.calculateAltitude(pressure, qnh)
    }

    fun adjustAltitude(increase: Boolean) {
        val step = if (useMetricUnits) {
            Constants.METRIC_ALTITUDE_STEP
        } else {
            Constants.IMPERIAL_ALTITUDE_STEP / Constants.METERS_TO_FEET // Convert feet to meters
        }
        
        currentAltitude += if (increase) step else -step
    }
}