package au.com.penattilabs.variowatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class VarioServiceTest {
    private lateinit var service: VarioService
    private lateinit var mockSensorManager: SensorManager
    private lateinit var mockPressureSensor: Sensor
    private lateinit var mockNotificationManager: NotificationManager
    
    @Before
    fun setup() {
        mockSensorManager = mockk(relaxed = true)
        mockPressureSensor = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns mockPressureSensor
        every { mockSensorManager.registerListener(any(), mockPressureSensor, Constants.SENSOR_SAMPLING_PERIOD_US) } returns true
        
        // Initialize service with Robolectric
        service = Robolectric.setupService(VarioService::class.java)
        
        // Replace system services with mocks
        val application = RuntimeEnvironment.getApplication()
        val appShadow = shadowOf(application)
        appShadow.setSystemService(Context.SENSOR_SERVICE, mockSensorManager)
        appShadow.setSystemService(Context.NOTIFICATION_SERVICE, mockNotificationManager)
        
        // Start service
        service.onCreate()
    }
    
    @Test
    fun `when service starts then register pressure sensor listener`() {
        service.onStartCommand(Intent(), 0, 0)
        
        verify { mockSensorManager.registerListener(
            service,
            mockPressureSensor,
            Constants.SENSOR_SAMPLING_PERIOD_US
        )}
    }
    
    @Test
    fun `when service starts then create notification channel`() {
        service.onStartCommand(Intent(), 0, 0)
        
        verify { mockNotificationManager.createNotificationChannel(match { 
            it.id == Constants.NOTIFICATION_CHANNEL_ID &&
            it.name.toString() == Constants.NOTIFICATION_CHANNEL_NAME
        })}
    }
    
    @Test
    fun `when pressure changes then calculate altitude`() {
        val pressure = 900f
        // First calculate QNH for a known altitude (1000m) with this pressure reading
        val qnh = AltitudeCalculator.calculateQnhFromAltitude(pressure, 1000f)
        // Then calculate expected altitude using that QNH
        val expectedAltitude = AltitudeCalculator.calculateAltitude(pressure, qnh)
        
        // Create mock sensor event
        val mockEvent = mockk<SensorEvent>()
        every { mockEvent.sensor } returns mockPressureSensor
        every { mockEvent.values } returns FloatArray(1).apply { this[0] = pressure }
        
        service.onSensorChanged(mockEvent)
        
        // Verify altitude calculation - should match our expected value within 0.1m
        assertEquals(expectedAltitude, service.currentAltitude, 0.1f)
    }
    
    @Test
    fun `when service destroyed then unregister sensor listener`() {
        service.onStartCommand(Intent(), 0, 0)
        service.onDestroy()
        
        verify { mockSensorManager.unregisterListener(service) }
    }
    
    @Test
    fun `when sensor registration fails then try different rates`() {
        // First two attempts fail
        every { mockSensorManager.registerListener(any(), mockPressureSensor, Constants.SENSOR_SAMPLING_PERIOD_US) } returns false
        every { mockSensorManager.registerListener(any(), mockPressureSensor, SensorManager.SENSOR_DELAY_NORMAL) } returns false
        // Third attempt succeeds
        every { mockSensorManager.registerListener(any(), mockPressureSensor, SensorManager.SENSOR_DELAY_UI) } returns true
        
        service.onStartCommand(Intent(), 0, 0)
        
        verifySequence { 
            mockSensorManager.registerListener(service, mockPressureSensor, Constants.SENSOR_SAMPLING_PERIOD_US)
            mockSensorManager.registerListener(service, mockPressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
            mockSensorManager.registerListener(service, mockPressureSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }
}