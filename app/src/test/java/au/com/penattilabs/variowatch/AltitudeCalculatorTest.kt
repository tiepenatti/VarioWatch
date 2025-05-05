package au.com.penattilabs.variowatch

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AltitudeCalculatorTest {
    companion object {
        private const val DELTA = 0.1f // Acceptable floating point difference
    }

    @Test
    fun `given standard pressure when calculating altitude then return zero`() {
        val altitude = AltitudeCalculator.calculateAltitude(Constants.ISA_PRESSURE_SEA_LEVEL, Constants.ISA_PRESSURE_SEA_LEVEL)
        assertEquals(0f, altitude, DELTA)
    }

    @Test
    fun `given lower pressure when calculating altitude then return positive altitude`() {
        val altitude = AltitudeCalculator.calculateAltitude(900f, Constants.ISA_PRESSURE_SEA_LEVEL)
        assertTrue(altitude > 0)
        assertEquals(1000f, altitude, 50f) // Should be around 1000m with 50m tolerance
    }

    @Test
    fun `given higher pressure when calculating altitude then return negative altitude`() {
        val altitude = AltitudeCalculator.calculateAltitude(1050f, Constants.ISA_PRESSURE_SEA_LEVEL)
        assertTrue(altitude < 0)
    }

    @Test
    fun `given known altitude when calculating QNH then return correct pressure`() {
        // At 1000m with 915 hPa, QNH should be around 1040 hPa
        // This follows the standard pressure lapse rate of roughly -1 hPa per 8.5m
        val qnh = AltitudeCalculator.calculateQnhFromAltitude(915f, 1000f)
        assertEquals(1040f, qnh, 5f) // Allow 5 hPa tolerance for different calculation methods
    }

    @Test
    fun `given altitude when formatting with metric units then return meters`() {
        val formatted = AltitudeCalculator.formatAltitude(1000f, true)
        assertEquals("1000 m", formatted)
    }

    @Test
    fun `given altitude when formatting with imperial units then return feet`() {
        val formatted = AltitudeCalculator.formatAltitude(1000f, false)
        assertEquals("3281 ft", formatted) // 1000m ≈ 3281ft
    }

    @Test
    fun `given zero altitude when calculating QNH then return same pressure`() {
        val qnh = AltitudeCalculator.calculateQnhFromAltitude(Constants.ISA_PRESSURE_SEA_LEVEL, 0f)
        assertEquals(Constants.ISA_PRESSURE_SEA_LEVEL, qnh, DELTA)
    }

    @Test
    fun `given pressure and QNH when calculating altitude then QNH calculation should be reversible`() {
        val pressure = 950f
        val originalAltitude = 500f
        
        // Calculate QNH for given altitude
        val qnh = AltitudeCalculator.calculateQnhFromAltitude(pressure, originalAltitude)
        
        // Use that QNH to calculate altitude back
        val calculatedAltitude = AltitudeCalculator.calculateAltitude(pressure, qnh)
        
        // Should get original altitude back
        assertEquals(originalAltitude, calculatedAltitude, 1f) // 1m tolerance
    }
}