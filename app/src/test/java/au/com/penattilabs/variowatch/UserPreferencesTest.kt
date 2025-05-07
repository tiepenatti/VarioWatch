package au.com.penattilabs.variowatch

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
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
    }
    
    @Test
    fun `when toggling units then value is updated and saved`() {
        // Start with metric units (true)
        every { mockSharedPrefs.getBoolean("use_metric_units", true) } returns true
        
        userPreferences.toggleUnitSystem()
        
        // After toggle, should be false (imperial)
        verify { mockEditor.putBoolean("use_metric_units", false) }
        verify { mockEditor.apply() }
    }
    
    @Test
    fun `when updating QNH then value is updated and saved`() {
        val newQnh = 1020.0f
        // No need to mock getFloat for qnh here as we are testing the setter logic
        
        userPreferences.updateQnh(newQnh)
        
        // To verify the value was set, we'd ideally check the sharedPrefs mock
        // or if qnh had a public getter that reads from sharedPrefs directly.
        // For this test, we assume the internal field 'qnh' is updated by the setter.
        // A more robust test would involve mocking getFloat to return the newQnh
        // after the putFloat call if we were testing the getter.
        // However, the current implementation of qnh setter updates a field and then saves.
        // So we check the interaction with the editor.
        
        verify { mockEditor.putFloat("qnh", newQnh) }
        verify { mockEditor.apply() }
        
        // If we want to assert the value of userPreferences.qnh after update,
        // we need to make sure the getter is also mocked to return the new value.
        every { mockSharedPrefs.getFloat("qnh", any()) } returns newQnh 
        assertEquals(newQnh, userPreferences.qnh, 0.01f)
    }
}