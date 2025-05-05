package au.com.penattilabs.variowatch

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val PREFS_NAME = "vario_preferences"
private const val KEY_USE_METRIC_UNITS = "use_metric_units"
private const val KEY_QNH = "qnh"
private const val STEP_METERS = 10f
private const val STEP_FEET = 25f

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    var useMetricUnits: Boolean by mutableStateOf(true)
        private set

    private var _qnhState = mutableStateOf(1013.25f)
    internal val qnh get() = _qnhState.value
    
    private var _currentAltitude = mutableStateOf(0f)
    val currentAltitude get() = _currentAltitude.value
    
    private var _currentPressure = mutableStateOf(1013.25f)
    
    init {
        useMetricUnits = prefs.getBoolean(KEY_USE_METRIC_UNITS, true)
        _qnhState.value = prefs.getFloat(KEY_QNH, 1013.25f)
    }
    
    fun toggleUnitSystem() {
        useMetricUnits = !useMetricUnits
        prefs.edit().putBoolean(KEY_USE_METRIC_UNITS, useMetricUnits).apply()
    }
    
    internal fun updateQnh(value: Float) {
        _qnhState.value = value
        prefs.edit().putFloat(KEY_QNH, value).apply()
        // Recalculate altitude with new QNH
        updateCurrentAltitude(_currentPressure.value)
    }

    fun updateCurrentAltitude(pressureHpa: Float) {
        _currentPressure.value = pressureHpa
        _currentAltitude.value = AltitudeCalculator.calculateAltitude(pressureHpa, qnh)
    }
    
    fun adjustAltitude(increase: Boolean) {
        val stepSize = if (useMetricUnits) STEP_METERS else STEP_FEET
        val currentAltitudeMeters = if (useMetricUnits) currentAltitude else currentAltitude / 3.28084f
        val adjustment = if (increase) stepSize else -stepSize
        val newAltitudeMeters = currentAltitudeMeters + (if (useMetricUnits) adjustment else adjustment / 3.28084f)
        
        // Calculate new QNH based on desired altitude using current pressure
        val newQnh = AltitudeCalculator.calculateQnhFromAltitude(_currentPressure.value, newAltitudeMeters)
        updateQnh(newQnh)
    }
}