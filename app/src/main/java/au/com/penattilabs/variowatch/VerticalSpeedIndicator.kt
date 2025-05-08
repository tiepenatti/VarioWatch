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
    maxSpeedMetric: Float = 5.0f, // m/s
    maxSpeedImperial: Float = 984.252f, // ft/min (approx 5 m/s)
    strokeWidth: Float = 30.0f // Increased width
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val maxSpeed = if (useMetricUnits) maxSpeedMetric else maxSpeedImperial
        val sweepAngleRange = 90f // 90 degrees for positive (3 o'clock to 12 o'clock), 90 for negative (3 o'clock to 6 o'clock)

        val currentSpeedClamped = verticalSpeed.coerceIn(-maxSpeed, maxSpeed)
        val normalizedSpeed = currentSpeedClamped / maxSpeed // -1 to 1

        val sweepAngle = normalizedSpeed * sweepAngleRange

        // Draw the background arc
        drawArc(
            color = Color.DarkGray,
            startAngle = -90f, // Start at 12 o'clock
            sweepAngle = 180f, // Full range from 6 o'clock to 12 o'clock
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )

        // Draw the indicator arc
        val indicatorColor = when {
            normalizedSpeed > 0 -> Color.Green
            normalizedSpeed < 0 -> Color.Red
            else -> Color.Transparent // No indicator if speed is zero
        }

        if (normalizedSpeed != 0f) {
            drawArc(
                color = indicatorColor,
                startAngle = if (normalizedSpeed > 0) -90f else 90f, // Adjust start for positive to draw upwards
                sweepAngle = if (normalizedSpeed > 0) sweepAngle else -sweepAngle, // Negative sweep for sinking
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}