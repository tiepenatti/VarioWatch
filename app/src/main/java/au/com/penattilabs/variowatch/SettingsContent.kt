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
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    onClick = { userPreferences.updateVolumeLevel(0) },
                    modifier = Modifier.padding(4.dp),
                ) { Text(stringResource(R.string.volume_off)) }

                Button(
                    onClick = { userPreferences.updateVolumeLevel(1) },
                    modifier = Modifier.padding(4.dp),
                ) { Text(stringResource(R.string.volume_low)) }

                Button(
                    onClick = { userPreferences.updateVolumeLevel(2) },
                    modifier = Modifier.padding(4.dp),
                ) { Text(stringResource(R.string.volume_medium)) }

                Button(
                    onClick = { userPreferences.updateVolumeLevel(3) },
                    modifier = Modifier.padding(4.dp),
                ) { Text(stringResource(R.string.volume_high)) }
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