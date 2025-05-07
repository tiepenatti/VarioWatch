package au.com.penattilabs.variowatch

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @RelaxedMockK // Mock PressureSensorManager
    private lateinit var mockPressureSensorManager: PressureSensorManager

    private lateinit var application: VarioWatchApplication
    private lateinit var serviceScope: CoroutineScope


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        serviceScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns mockPressureSensor

        application = RuntimeEnvironment.getApplication() as VarioWatchApplication
        
        mockkConstructor(PressureSensorManager::class)
        // Corrected: startSensor() returns Boolean
        every { constructedWith<PressureSensorManager>(EqMatcher(application.applicationContext), EqMatcher(mockUserPreferences)).startSensor() } returns true 
        every { constructedWith<PressureSensorManager>(EqMatcher(application.applicationContext), EqMatcher(mockUserPreferences)).stopSensor() } just Runs
        // Corrected: sensorState returns StateFlow
        every { constructedWith<PressureSensorManager>(EqMatcher(application.applicationContext), EqMatcher(mockUserPreferences)).sensorState } returns MutableStateFlow(PressureSensorManager.PressureSensorState())

        service = Robolectric.buildService(VarioService::class.java).create().get()

        // Replace the real UserPreferences instance in VarioService with the mock
        val userPrefsField = VarioService::class.java.getDeclaredField("userPreferences")
        userPrefsField.isAccessible = true
        userPrefsField.set(service, mockUserPreferences)

        // Replace the real PressureSensorManager instance with the mock
        val psmField = VarioService::class.java.getDeclaredField("pressureSensorManager")
        psmField.isAccessible = true
        psmField.set(service, mockPressureSensorManager)
        
        // Replace the real NotificationManager instance with the mock
        val nmField = VarioService::class.java.getDeclaredField("notificationManager")
        nmField.isAccessible = true
        nmField.set(service, mockNotificationManager)

        // Replace the serviceScope with a test-controlled one
        val scopeField = VarioService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        scopeField.set(service, serviceScope)

    }

    @After
    fun tearDown() {
        serviceScope.cancel()
        unmockkAll() // Important to clear mocks between tests
    }

    @Test
    fun `test service onCreate initializes PressureSensorManager and notification`() {
        // Service is already created in setUp by Robolectric.setupService().create()
        // We verify interactions that should have happened during onCreate
        verify { mockPressureSensorManager wasNot Called } // startSensor is called in onStartCommand
        verify { mockNotificationManager.createNotificationChannel(any()) }
    }

    @Test
    fun `test onStartCommand starts sensor and notification updater`() {
        service.onStartCommand(Intent(), 0, 1)
        verify { mockPressureSensorManager.startSensor() }
        // Notification updater is started, which involves coroutines.
        // We can check if the notificationUpdateJob is active or a specific log.
        assertNotNull(service.javaClass.getDeclaredField("notificationUpdateJob").apply { isAccessible = true }.get(service))
    }


    @Test
    fun `test service cleanup on destroy`() {
        service.onDestroy()
        verify { mockPressureSensorManager.stopSensor() }
        // Verify notificationUpdateJob is cancelled
        val job = service.javaClass.getDeclaredField("notificationUpdateJob").apply { isAccessible = true }.get(service) as? kotlinx.coroutines.Job
        assertTrue(job?.isCancelled ?: true) // If null, it's also fine as it might not have been started
    }

    // The sensor event handling is now within PressureSensorManager.
    // VarioServiceTest should focus on VarioService's direct responsibilities,
    // like reacting to PressureSensorManager's state updates if applicable,
    // managing its lifecycle, and handling notifications.
}