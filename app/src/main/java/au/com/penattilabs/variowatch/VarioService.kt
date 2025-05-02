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

    companion object {
        const val ACTION_PRESSURE_UPDATE = "au.com.penattilabs.variowatch.PRESSURE_UPDATE"
        const val EXTRA_PRESSURE = "pressure"
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        createNotificationChannel()
        startForeground(Constants.SERVICE_NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pressureSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                Constants.SENSOR_SAMPLING_PERIOD_US
            )
        }
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
                sendBroadcast(Intent(ACTION_PRESSURE_UPDATE).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_PRESSURE, pressure)
                })
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
}