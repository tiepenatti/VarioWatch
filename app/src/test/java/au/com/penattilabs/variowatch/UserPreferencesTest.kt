package au.com.penattilabs.variowatch

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserPreferencesTest {
    private lateinit var mockContext: Context
    private lateinit var mockSharedPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var userPreferences: UserPreferences
    
    @Before
    fun setup() {
        mockContext = mockk()
        mockSharedPrefs = mockk()
        mockEditor = mockk()
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPrefs
        every { mockSharedPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putFloat(any(), any()) } returns mockEditor
        every { mockEditor.apply() } just Runs
        
        // Default values
        every { mockSharedPrefs.getBoolean("use_metric_units", true) } returns true
        every { mockSharedPrefs.getFloat("qnh", Constants.ISA_PRESSURE_SEA_LEVEL) } returns Constants.ISA_PRESSURE_SEA_LEVEL
        
        userPreferences = UserPreferences(mockContext)
    }
    
    @Test
    fun `given new instance when initialized then use default values`() {
        assertTrue(userPreferences.useMetricUnits)
        assertEquals(Constants.ISA_PRESSURE_SEA_LEVEL, userPreferences.qnh, 0.01f)
        assertEquals(0f, userPreferences.currentAltitude, 0.01f)
    }
    
    @Test
    fun `when toggling units then value is updated and saved`() {
        every { mockSharedPrefs.getBoolean("use_metric_units", true) } returns false
        
        userPreferences.toggleUnitSystem()
        
        assertFalse(userPreferences.useMetricUnits)
        verify { mockEditor.putBoolean("use_metric_units", false) }
        verify { mockEditor.apply() }
    }
    
    @Test
    fun `when updating QNH then value is updated and saved`() {
        val newQnh = 1020.0f
        every { mockSharedPrefs.getFloat("qnh", any()) } returns newQnh
        
        userPreferences.updateQnh(newQnh)
        
        assertEquals(newQnh, userPreferences.qnh, 0.01f)
        verify { mockEditor.putFloat("qnh", newQnh) }
        verify { mockEditor.apply() }
    }
    
    @Test
    fun `when updating current altitude then calculate from pressure and QNH`() {
        val pressure = 950.0f
        userPreferences.updateCurrentAltitude(pressure)
        
        val expectedAltitude = AltitudeCalculator.calculateAltitude(pressure, Constants.ISA_PRESSURE_SEA_LEVEL)
        assertEquals(expectedAltitude, userPreferences.currentAltitude, 0.1f)
    }
    
    @Test
    fun `when adjusting altitude up in metric then increase by metric step`() {
        val initialPressure = 1000f
        userPreferences.updateCurrentAltitude(initialPressure)
        val initialAltitude = userPreferences.currentAltitude
        
        userPreferences.adjustAltitude(true)
        
        assertEquals(initialAltitude + Constants.METRIC_ALTITUDE_STEP, userPreferences.currentAltitude, 0.1f)
    }
    
    @Test
    fun `when adjusting altitude down in imperial then decrease by imperial step`() {
        every { mockSharedPrefs.getBoolean("use_metric_units", true) } returns false
        
        val initialPressure = 1000f
        userPreferences.updateCurrentAltitude(initialPressure)
        val initialAltitude = userPreferences.currentAltitude
        
        userPreferences.adjustAltitude(false)
        
        val expectedDecrease = Constants.IMPERIAL_ALTITUDE_STEP / Constants.METERS_TO_FEET
        assertEquals(initialAltitude - expectedDecrease, userPreferences.currentAltitude, 0.1f)
    }
}