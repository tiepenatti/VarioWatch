package au.com.penattilabs.variowatch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PressureSensorManager(context: Context, private val userPreferences: UserPreferences) : SensorEventListener {
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    
    private val _sensorState = MutableStateFlow(PressureSensorState())
    val sensorState: StateFlow<PressureSensorState> = _sensorState.asStateFlow()

    private val pressureReadings = mutableListOf<Float>()
    private var warmupStartTime = 0L
    private var lastTimestamp: Long = 0L
    private var lastAltitude: Float = Float.NaN

    sealed class SensorError {
        object NoSensor : SensorError()
        object RegistrationFailed : SensorError()
        data class AccuracyError(val accuracy: Int) : SensorError()
    }

    data class PressureSensorState(
        val currentPressure: Float = 0f,
        val currentAltitude: Float = Float.NaN,
        val verticalSpeed: Float = Float.NaN,
        val isRegistered: Boolean = false,
        val error: SensorError? = null
    )

    init {
        logSensorInfo()
    }

    private fun logSensorInfo() {
        pressureSensor?.let { sensor ->
            Log.d(TAG, buildString {
                append("Pressure sensor details:\n")
                append("Name: ${sensor.name}\n")
                append("Vendor: ${sensor.vendor}\n")
                append("Version: ${sensor.version}\n")
                append("Power: ${sensor.power} mA\n")
                append("Resolution: ${sensor.resolution} hPa\n")
                append("Maximum range: ${sensor.maximumRange} hPa\n")
                append("Minimum delay: ${sensor.minDelay} microseconds\n")
                append("Maximum delay: ${sensor.maxDelay} microseconds\n")
                append("Wake-up sensor: ${sensor.isWakeUpSensor}")
            })
        } ?: Log.e(TAG, "No pressure sensor available on this device")
    }

    fun startSensor(): Boolean {
        if (_sensorState.value.isRegistered) {
            Log.d(TAG, "Sensor already registered")
            return true
        }

        pressureReadings.clear()
        warmupStartTime = System.currentTimeMillis()
        lastTimestamp = 0L
        lastAltitude = Float.NaN

        pressureSensor?.let { sensor ->
            val rates = listOf(
                Constants.SENSOR_SAMPLING_PERIOD_US,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
            
            for (rate in rates) {
                if (sensorManager.registerListener(
                    this, 
                    sensor, 
                    rate, 
                    Constants.SENSOR_BATCH_LATENCY_US
                )) {
                    _sensorState.update { 
                        it.copy(
                            isRegistered = true,
                            error = null
                        )
                    }
                    Log.d(TAG, "Sensor registration success with rate: $rate, batch latency: ${Constants.SENSOR_BATCH_LATENCY_US}us")
                    return true
                }
            }
            
            _sensorState.update { 
                it.copy(
                    isRegistered = false,
                    error = SensorError.RegistrationFailed
                )
            }
            Log.e(TAG, "Failed to register sensor with any rate")
            return false
        } ?: run {
            _sensorState.update { 
                it.copy(
                    isRegistered = false,
                    error = SensorError.NoSensor
                )
            }
            Log.e(TAG, "No pressure sensor available")
            return false
        }
    }

    fun stopSensor() {
        if (_sensorState.value.isRegistered) {
            sensorManager.unregisterListener(this)
            _sensorState.update { 
                it.copy(
                    isRegistered = false,
                    currentPressure = 0f,
                    currentAltitude = Float.NaN,
                    verticalSpeed = Float.NaN,
                    error = null
                )
            }
            Log.d(TAG, "Sensor unregistered")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_PRESSURE) {
            Log.d(TAG, "Pressure sensor accuracy changed to: $accuracy")
            val currentError = if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) {
                 SensorError.AccuracyError(accuracy)
            } else {
                 null
            }
            _sensorState.update {
                it.copy(error = currentError)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_PRESSURE) {
                val currentTimestamp = it.timestamp
                val pressure = it.values[0]
                
                if (System.currentTimeMillis() - warmupStartTime < Constants.SENSOR_WARMUP_TIME_MS) {
                    Log.v(TAG, "Sensor warming up, skipping reading.")
                    return
                }

                pressureReadings.add(pressure)
                
                if (pressureReadings.size >= Constants.SENSOR_BATCH_SIZE) {
                    val medianPressure = pressureReadings.sorted()[pressureReadings.size / 2]
                    pressureReadings.clear()

                    val currentAltitude = AltitudeCalculator.calculateAltitude(medianPressure, userPreferences.qnh)
                    var verticalSpeed = Float.NaN

                    if (lastTimestamp > 0L && !lastAltitude.isNaN() && !currentAltitude.isNaN()) {
                        val timeDiffSeconds = (currentTimestamp - lastTimestamp) / 1_000_000_000.0f
                        if (timeDiffSeconds > 0.05f) {
                            verticalSpeed = (currentAltitude - lastAltitude) / timeDiffSeconds
                        } else {
                            verticalSpeed = _sensorState.value.verticalSpeed 
                            Log.v(TAG, "Time difference too small ($timeDiffSeconds s), reusing last VS.")
                        }
                    } else {
                         Log.v(TAG, "Not enough data to calculate vertical speed yet.")
                    }
                    
                    _sensorState.update { state ->
                        state.copy(
                            currentPressure = medianPressure,
                            currentAltitude = currentAltitude,
                            verticalSpeed = verticalSpeed,
                            error = state.error
                        )
                    }

                    lastTimestamp = currentTimestamp
                    lastAltitude = currentAltitude
                }
            }
        }
    }

    companion object {
        private const val TAG = "PressureSensorManager"
    }
}