package au.com.penattilabs.variowatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import java.util.concurrent.TimeUnit

class VarioService : Service() {
    companion object {
        const val ACTION_PRESSURE_UPDATE = "au.com.penattilabs.variowatch.PRESSURE_UPDATE"
        const val ACTION_STOP_SERVICE = "au.com.penattilabs.variowatch.STOP_SERVICE"
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
    private var startTimeMillis: Long = 0L
    private var notificationUpdateJob: Job? = null
    private lateinit var notificationManager: NotificationManager

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
        Log.d(TAG, "VarioService onCreate")
        userPreferences = (applicationContext as VarioWatchApplication).userPreferences
        pressureSensorManager = PressureSensorManager(applicationContext, userPreferences)
        startTimeMillis = System.currentTimeMillis()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        setupNotification()
        setupSensorCollection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VarioService onStartCommand, action: ${intent?.action}")
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Stopping service via notification action")
            stopSelf()
            return START_NOT_STICKY
        }
        pressureSensorManager.startSensor()
        startService(Intent(this, SoundSynthesizerService::class.java))
        Log.d(TAG, "SoundSynthesizerService started")
        startNotificationUpdater()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "VarioService onDestroy called")
        stopService(Intent(this, SoundSynthesizerService::class.java))
        Log.d(TAG, "SoundSynthesizerService stopped")
        pressureSensorManager.stopSensor()
        notificationUpdateJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
        Log.d(TAG, "VarioService fully destroyed")
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun setupNotification() {
        Log.d(TAG, "Setting up notification...")
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.vario_notification_channel_description)
        }

        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created.")

        val stopServiceIntent = Intent(this, VarioService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopServicePendingIntent: PendingIntent =
            PendingIntent.getService(this, 0, stopServiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.vario_notification_title))
            .setContentText(getString(R.string.vario_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop_service_action), stopServicePendingIntent)
            .build()

        try {
            Log.d(TAG, "Attempting to start foreground service...")
            startForeground(Constants.SERVICE_NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground called successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error calling startForeground: ${e.message}", e)
        }
    }

    private fun startNotificationUpdater() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                updateNotificationContent()
                delay(1000)
            }
        }
    }

    private fun updateNotificationContent() {
        val elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedTimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTimeMillis) % 60
        val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        val notificationText = getString(R.string.vario_notification_running_text, timeString)

        val stopServiceIntent = Intent(this, VarioService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopServicePendingIntent: PendingIntent =
            PendingIntent.getService(this, 0, stopServiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.vario_notification_title))
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop_service_action), stopServicePendingIntent)
            .build()

        notificationManager.notify(Constants.SERVICE_NOTIFICATION_ID, notification)
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
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}