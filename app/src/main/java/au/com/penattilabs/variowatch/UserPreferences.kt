package au.com.penattilabs.variowatch

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    var useMetricUnits: Boolean by mutableStateOf(true)
        private set
    
    var qnh: Float by mutableStateOf(1013.25f)
        private set
        
    var manualAltitude: Float? by mutableStateOf(null)
        private set
    
    init {
        useMetricUnits = prefs.getBoolean(KEY_USE_METRIC_UNITS, true)
        qnh = prefs.getFloat(KEY_QNH, 1013.25f)
        manualAltitude = prefs.getFloat(KEY_MANUAL_ALTITUDE, Float.NaN).let { if (it.isNaN()) null else it }
    }
    
    fun toggleUnitSystem() {
        useMetricUnits = !useMetricUnits
        prefs.edit().putBoolean(KEY_USE_METRIC_UNITS, useMetricUnits).apply()
    }
    
    fun setQnh(value: Float) {
        qnh = value
        prefs.edit().putFloat(KEY_QNH, value).apply()
    }
    
    fun setManualAltitude(value: Float?) {
        manualAltitude = value
        prefs.edit().putFloat(KEY_MANUAL_ALTITUDE, value ?: Float.NaN).apply()
    }
    
    companion object {
        private const val PREFS_NAME = "vario_preferences"
        private const val KEY_USE_METRIC_UNITS = "use_metric_units"
        private const val KEY_QNH = "qnh"
        private const val KEY_MANUAL_ALTITUDE = "manual_altitude"
    }
}