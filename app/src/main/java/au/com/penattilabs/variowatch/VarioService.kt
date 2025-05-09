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

    private val pressureReadings = mutableListOf<Float>() // Buffer for moving average
    private val maxBufferSize = 10 // Buffer size for 10 readings (1 second at 10Hz)

    // Add fields to store last altitude and timestamp for VS calculation in VarioService
    private var serviceLastAltitude: Float = Float.NaN
    private var serviceLastTimestampNanos: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VarioService onCreate")
        userPreferences = (applicationContext as VarioWatchApplication).userPreferences
        pressureSensorManager = PressureSensorManager(applicationContext, userPreferences)
        startTimeMillis = System.currentTimeMillis()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Initialize/reset last known values for VS calculation
        serviceLastAltitude = Float.NaN
        serviceLastTimestampNanos = 0L

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
        // Reset last known values when sensor starts, to avoid stale VS calculations
        serviceLastAltitude = Float.NaN
        serviceLastTimestampNanos = 0L
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

    private fun calculateMovingAverage(newReading: Float): Float {
        if (pressureReadings.size >= maxBufferSize) {
            pressureReadings.removeAt(0)
        }
        pressureReadings.add(newReading)
        return pressureReadings.average().toFloat()
    }

    private fun setupSensorCollection() {
        serviceScope.launch {
            pressureSensorManager.sensorState
                .onEach { stateFromManager -> // Renamed to avoid confusion
                    stateFromManager.error?.let {
                        Log.e(TAG, "Sensor error: $it")
                        // Potentially reset serviceLastAltitude/Timestamp if sensor error is critical
                    }
                    if (stateFromManager.currentPressure > 0f && stateFromManager.eventTimestampNanos > 0L) {
                        val smoothedPressure = calculateMovingAverage(stateFromManager.currentPressure)
                        
                        val newAltitude = AltitudeCalculator.calculateAltitude(smoothedPressure, userPreferences.qnh)
                        var newVerticalSpeed = Float.NaN

                        if (serviceLastTimestampNanos > 0L && !serviceLastAltitude.isNaN() && !newAltitude.isNaN()) {
                            // Use eventTimestampNanos from the state, which corresponds to the current batch of readings
                            val timeDiffSeconds = (stateFromManager.eventTimestampNanos - serviceLastTimestampNanos) / 1_000_000_000.0f
                            if (timeDiffSeconds > 0.01f) { // Min time diff to avoid erratic VS
                                newVerticalSpeed = (newAltitude - serviceLastAltitude) / timeDiffSeconds
                            } else {
                                // If time diff is too small, newVerticalSpeed will remain NaN.
                                // This is acceptable as it indicates insufficient data for a new reliable VS.
                                Log.v(TAG, "VarioService: Time difference for VS calc too small ($timeDiffSeconds s). VS will be NaN or stale if not updated.")
                            }
                        }

                        serviceLastAltitude = newAltitude
                        serviceLastTimestampNanos = stateFromManager.eventTimestampNanos

                        // Create the state to broadcast with values derived from smoothedPressure
                        val broadcastState = PressureSensorManager.PressureSensorState(
                            currentPressure = smoothedPressure,
                            currentAltitude = newAltitude,
                            verticalSpeed = newVerticalSpeed,
                            eventTimestampNanos = stateFromManager.eventTimestampNanos, // Pass along if needed by receivers
                            isRegistered = stateFromManager.isRegistered,
                            error = stateFromManager.error
                        )
                        sendSensorStateUpdate(broadcastState)
                    } else if (stateFromManager.currentPressure <= 0f) {
                        // If pressure is invalid, reset altitude and VS to NaN for broadcast
                         val broadcastState = PressureSensorManager.PressureSensorState(
                            currentPressure = stateFromManager.currentPressure, // or 0f
                            currentAltitude = Float.NaN,
                            verticalSpeed = Float.NaN,
                            eventTimestampNanos = stateFromManager.eventTimestampNanos,
                            isRegistered = stateFromManager.isRegistered,
                            error = stateFromManager.error // keep existing error or set new one
                        )
                        sendSensorStateUpdate(broadcastState)
                        // Also reset internal VS calculation state for VarioService
                        serviceLastAltitude = Float.NaN
                        serviceLastTimestampNanos = 0L // Reset to ensure fresh VS calc when pressure is valid again
                    }
                }
                .catch { error ->
                    Log.e(TAG, "Error collecting sensor state: ${error.message}", error)
                     // Reset internal VS calculation state on error
                    serviceLastAltitude = Float.NaN
                    serviceLastTimestampNanos = 0L
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