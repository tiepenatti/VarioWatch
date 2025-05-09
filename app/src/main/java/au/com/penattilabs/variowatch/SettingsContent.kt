package au.com.penattilabs.variowatch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn

@Composable
fun SettingsContent(
    useMetricUnits: Boolean,
    onToggleUnits: () -> Unit,
    currentVolumeLevel: Int, // Added to receive current volume level
    onVolumeChange: (Int) -> Unit, // Added to handle volume changes
    onBackClick: () -> Unit,
    currentAltitude: Float,
    onAdjustAltitude: (increase: Boolean) -> Unit,
    userPreferences: UserPreferences
) {
    BackHandler(onBack = onBackClick)

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MainActivity.UI.HORIZONTAL_PADDING.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = stringResource(R.string.settings),
                modifier = Modifier.padding(vertical = MainActivity.UI.VERTICAL_PADDING_MEDIUM.dp)
            )
        }

        item {
            Button(
                onClick = onToggleUnits,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MainActivity.UI.VERTICAL_PADDING_SMALL.dp)
            ) {
                Text(text = stringResource(
                    if (useMetricUnits) R.string.altitude_unit_metric
                    else R.string.altitude_unit_imperial
                ))
            }
        }

        item {
            Text(
                text = stringResource(R.string.calibrate_altitude),
                modifier = Modifier.padding(top = MainActivity.UI.VERTICAL_PADDING_LARGE.dp, bottom = MainActivity.UI.VERTICAL_PADDING_SMALL.dp)
            )
        }

        item {
            Text(
                text = AltitudeCalculator.formatAltitude(currentAltitude, useMetricUnits),
                modifier = Modifier.padding(vertical = MainActivity.UI.VERTICAL_PADDING_SMALL.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    onClick = { onAdjustAltitude(false) },
                    modifier = Modifier.padding(4.dp),
                ) { Text(stringResource(R.string.button_minus)) }

                Button(
                    onClick = { onAdjustAltitude(true) },
                    modifier = Modifier.padding(4.dp),
                ) { Text(stringResource(R.string.button_plus)) }
            }
        }

        item {
            Text(
                text = stringResource(R.string.volume_settings),
                modifier = Modifier.padding(top = MainActivity.UI.VERTICAL_PADDING_LARGE.dp, bottom = MainActivity.UI.VERTICAL_PADDING_SMALL.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onVolumeChange((currentVolumeLevel - 1).coerceAtLeast(0)) }, // Use onVolumeChange
                    modifier = Modifier.padding(4.dp),
                ) { Text("-") }

                Text(
                    text = when (currentVolumeLevel) { // Use currentVolumeLevel from parameter
                        0 -> stringResource(R.string.volume_off)
                        1 -> stringResource(R.string.volume_low)
                        2 -> stringResource(R.string.volume_medium)
                        3 -> stringResource(R.string.volume_high)
                        else -> "---"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Button(
                    onClick = { onVolumeChange((currentVolumeLevel + 1).coerceAtMost(3)) }, // Use onVolumeChange
                    modifier = Modifier.padding(4.dp),
                ) { Text("+") }
            }
        }

        item {
            Button(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(top = MainActivity.UI.VERTICAL_PADDING_LARGE.dp, bottom = MainActivity.UI.VERTICAL_PADDING_SMALL.dp)
                    .fillMaxWidth(),
            ) { Text(text = stringResource(R.string.back)) }
        }
    }
}