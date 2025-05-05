package au.com.penattilabs.variowatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VarioService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private lateinit var userPreferences: UserPreferences
    private var currentPressure = 0f
    var currentAltitude = 0f  // Made public for testing

    companion object {
        const val ACTION_PRESSURE_UPDATE = "au.com.penattilabs.variowatch.PRESSURE_UPDATE"
        const val EXTRA_PRESSURE = "pressure"
        private const val TAG = "VarioService"
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        
        android.util.Log.d(TAG, "Pressure sensor available: ${pressureSensor != null}")
        if (pressureSensor != null) {
            android.util.Log.d(TAG, "Sensor name: ${pressureSensor?.name}, isWakeUpSensor: ${pressureSensor?.isWakeUpSensor}, " +
                "type: ${pressureSensor?.type}, vendor: ${pressureSensor?.vendor}, version: ${pressureSensor?.version}")
        }
        
        userPreferences = UserPreferences(this)
        createNotificationChannel()
        startForeground(Constants.SERVICE_NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pressureSensor?.let {
            // Try different sampling rates if the default one doesn't work
            val rates = listOf(
                Constants.SENSOR_SAMPLING_PERIOD_US,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
            
            var success = false
            for (rate in rates) {
                success = sensorManager.registerListener(this, it, rate)
                if (success) {
                    android.util.Log.d(TAG, "Sensor registration success with rate: $rate")
                    break
                }
            }
            
            if (!success) {
                android.util.Log.e(TAG, "Failed to register sensor with any rate")
            }
        } ?: android.util.Log.e(TAG, "No pressure sensor available")
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_PRESSURE) {
                val pressure = it.values[0]
                android.util.Log.d(TAG, "Received pressure: $pressure hPa")
                currentAltitude = AltitudeCalculator.calculateAltitude(pressure, Constants.ISA_PRESSURE_SEA_LEVEL)
                handlePressureReading(pressure)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification() = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
        .setContentTitle("VarioWatch")
        .setContentText("Reading barometer...")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setOngoing(true)
        .build()

    private fun handlePressureReading(pressureHpa: Float) {
        currentPressure = pressureHpa
        userPreferences.updateCurrentAltitude(pressureHpa)

        val intent = Intent(ACTION_PRESSURE_UPDATE).apply {
            putExtra(EXTRA_PRESSURE, pressureHpa)
        }
        sendBroadcast(intent)
        android.util.Log.d(TAG, "Broadcast pressure update: $pressureHpa hPa")
    }
}