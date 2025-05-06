package au.com.penattilabs.variowatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

class VarioService : Service() {
    companion object {
        const val ACTION_PRESSURE_UPDATE = "au.com.penattilabs.variowatch.PRESSURE_UPDATE"
        const val EXTRA_PRESSURE = "pressure"
        private const val TAG = "VarioService"

        fun createIntent(context: Context): Intent {
            return Intent(context, VarioService::class.java)
        }
    }

    private lateinit var pressureSensorManager: PressureSensorManager
    private lateinit var userPreferences: UserPreferences
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(
        Dispatchers.Default + serviceJob + 
        CoroutineExceptionHandler { _, error -> 
            Log.e(TAG, "Coroutine error: ${error.message}", error)
            error.printStackTrace()
        }
    )

    override fun onCreate() {
        super.onCreate()
        pressureSensorManager = PressureSensorManager(applicationContext)
        userPreferences = (applicationContext as VarioWatchApplication).userPreferences
        
        setupNotification()
        setupSensorCollection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pressureSensorManager.startSensor()
        return START_STICKY
    }

    override fun onDestroy() {
        pressureSensorManager.stopSensor()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun setupNotification() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(au.com.penattilabs.variowatch.R.string.vario_notification_channel_description)
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(au.com.penattilabs.variowatch.R.string.vario_notification_title))
            .setContentText(getString(au.com.penattilabs.variowatch.R.string.vario_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(Constants.SERVICE_NOTIFICATION_ID, notification)
    }

    private fun setupSensorCollection() {
        serviceScope.launch {
            pressureSensorManager.sensorState
                .onEach { state -> 
                    state.error?.let { error ->
                        Log.e(TAG, "Sensor error: $error")
                    }
                    if (state.currentPressure > 0f) {
                        handlePressureReading(state.currentPressure)
                    }
                }
                .catch { error ->
                    Log.e(TAG, "Error collecting sensor state: ${error.message}", error)
                }
                .collect()
        }
    }

    private fun handlePressureReading(pressureHpa: Float) {
        if (pressureHpa == 0f) return

        val intent = Intent(ACTION_PRESSURE_UPDATE).apply {
            putExtra(EXTRA_PRESSURE, pressureHpa)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast pressure update: $pressureHpa hPa")
    }
}