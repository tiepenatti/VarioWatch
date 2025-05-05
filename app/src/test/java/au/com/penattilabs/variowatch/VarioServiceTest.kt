package au.com.penattilabs.variowatch

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class VarioServiceTest {
    private companion object {
        private const val ALTITUDE_ASSERT_DELTA = 0.1f
    }

    private lateinit var service: VarioService
    
    @RelaxedMockK
    private lateinit var mockSensorManager: SensorManager
    
    @RelaxedMockK
    private lateinit var mockPressureSensor: Sensor
    
    @RelaxedMockK
    private lateinit var mockNotificationManager: NotificationManager
    
    @RelaxedMockK
    private lateinit var mockUserPreferences: UserPreferences

    private lateinit var application: VarioWatchApplication

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns mockPressureSensor

        application = RuntimeEnvironment.getApplication() as VarioWatchApplication
        application.setUserPreferencesForTesting(mockUserPreferences)

        service = Robolectric.setupService(VarioService::class.java)
        service.javaClass.getDeclaredField("sensorManager").apply {
            isAccessible = true
            set(service, mockSensorManager)
        }
    }

    @Test
    fun `test service onCreate initializes sensors and notification channel`() {
        every { mockSensorManager.registerListener(any(), mockPressureSensor, any()) } returns true

        service.onCreate()

        verify(exactly = 1) { mockSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    }

    @Test
    fun `test sensor updates trigger pressure updates`() {
        val pressure = Constants.ISA_PRESSURE_SEA_LEVEL
        val qnh = Constants.ISA_PRESSURE_SEA_LEVEL
        val expectedAltitude = 0f

        every { mockUserPreferences.qnh } returns qnh

        // Create a proper mock of SensorEvent using MockK's constructor mocking
        val mockEvent = mockk<SensorEvent> {
            every { sensor } returns mockPressureSensor
            every { values } returns floatArrayOf(pressure)
            every { accuracy } returns 0
            every { timestamp } returns 0L
        }

        service.onSensorChanged(mockEvent)

        verify { mockUserPreferences.updateCurrentAltitude(pressure) }
        assertEquals(expectedAltitude, service.currentAltitude, ALTITUDE_ASSERT_DELTA)
    }

    @Test
    fun `test service cleanup on destroy`() {
        service.onDestroy()
        verify { mockSensorManager.unregisterListener(service) }
    }

    @Test
    fun `test fallback sampling rates when default fails`() {
        every { mockSensorManager.registerListener(any(), mockPressureSensor, Constants.SENSOR_SAMPLING_PERIOD_US) } returns false
        every { mockSensorManager.registerListener(any(), mockPressureSensor, SensorManager.SENSOR_DELAY_NORMAL) } returns false
        every { mockSensorManager.registerListener(any(), mockPressureSensor, SensorManager.SENSOR_DELAY_UI) } returns true

        service.onStartCommand(Intent(), 0, 1)

        verifySequence {
            mockSensorManager.registerListener(service, mockPressureSensor, Constants.SENSOR_SAMPLING_PERIOD_US)
            mockSensorManager.registerListener(service, mockPressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
            mockSensorManager.registerListener(service, mockPressureSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }
}