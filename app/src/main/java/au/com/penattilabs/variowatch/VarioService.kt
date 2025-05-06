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
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class VarioService : Service() {
    companion object {
        const val ACTION_PRESSURE_UPDATE = "au.com.penattilabs.variowatch.PRESSURE_UPDATE"
        const val EXTRA_PRESSURE = "pressure"
        const val EXTRA_ALTITUDE = "altitude"
        const val EXTRA_VERTICAL_SPEED = "vertical_speed"
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
        userPreferences = (applicationContext as VarioWatchApplication).userPreferences
        pressureSensorManager = PressureSensorManager(applicationContext, userPreferences)
        
        setupNotification()
        setupSensorCollection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pressureSensorManager.startSensor()
        // Start the sound service
        startService(Intent(this, SoundSynthesizerService::class.java))
        Log.d(TAG, "SoundSynthesizerService started")
        return START_STICKY
    }

    override fun onDestroy() {
        // Stop the sound service
        stopService(Intent(this, SoundSynthesizerService::class.java))
        Log.d(TAG, "SoundSynthesizerService stopped")
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
                    state.error?.let {
                        Log.e(TAG, "Sensor error: $it")
                    }
                    if (state.currentPressure > 0f) {
                        sendSensorStateUpdate(state)
                    }
                }
                .catch { error ->
                    Log.e(TAG, "Error collecting sensor state: ${error.message}", error)
                }
                .collect()
        }
    }

    private fun sendSensorStateUpdate(state: PressureSensorManager.PressureSensorState) {
        val intent = Intent(ACTION_PRESSURE_UPDATE).apply {
            putExtra(EXTRA_PRESSURE, state.currentPressure)
            putExtra(EXTRA_ALTITUDE, state.currentAltitude)
            putExtra(EXTRA_VERTICAL_SPEED, state.verticalSpeed)
        }
        // Use LocalBroadcastManager to send the broadcast within the app
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "Broadcast sensor state update (local): P=${state.currentPressure}, Alt=${state.currentAltitude}, VS=${state.verticalSpeed}")
    }
}