package au.com.penattilabs.variowatch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun VerticalSpeedIndicator(
    verticalSpeed: Float,
    useMetricUnits: Boolean,
    modifier: Modifier = Modifier,
    maxSpeedMetric: Float = 10.0f, // m/s (remains 10.0f)
    maxSpeedImperial: Float = 1968.504f, // ft/min (approx 10 m/s)
    strokeWidth: Float = 30.0f // remains 30.0f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val maxSpeed = if (useMetricUnits) maxSpeedMetric else maxSpeedImperial

        val currentSpeedClamped = verticalSpeed.coerceIn(-maxSpeed, maxSpeed)
        // Normalize speed from -1 (max sink) to +1 (max climb)
        val normalizedSpeed = currentSpeedClamped / maxSpeed

        // Background Arc:
        // Starts at 10.5 o'clock (-135 degrees from 3 o'clock, assuming positive sweep is CW for drawArc)
        // Sweeps 270 degrees clockwise to 7.5 o'clock (135 degrees from 3 o'clock)
        val backgroundStartAngle = -135f
        val backgroundSweepAngle = 270f
        drawArc(
            color = Color.DarkGray,
            startAngle = backgroundStartAngle,
            sweepAngle = backgroundSweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )

        // Indicator Arc:
        // Starts at 3 o'clock (0 degrees).
        // Positive speed (climb) sweeps anti-clockwise (negative sweepAngle for drawArc).
        // Negative speed (sink) sweeps clockwise (positive sweepAngle for drawArc).
        val indicatorStartAngle = 0f
        // Max sweep for climb is -135 deg (CCW), for sink is +135 deg (CW)
        // normalizedSpeed * -135f achieves this: 
        // e.g. climb (norm=1): 1 * -135 = -135 (CCW)
        // e.g. sink (norm=-1): -1 * -135 = 135 (CW)
        val actualSweepAngle = normalizedSpeed * -135.0f

        val indicatorColor = when {
            normalizedSpeed > 0.001f -> Color.Green
            normalizedSpeed < -0.001f -> Color.Red
            else -> Color.Transparent
        }

        if (indicatorColor != Color.Transparent) {
            drawArc(
                color = indicatorColor,
                startAngle = indicatorStartAngle,
                sweepAngle = actualSweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}