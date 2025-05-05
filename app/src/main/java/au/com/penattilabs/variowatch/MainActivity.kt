package au.com.penattilabs.variowatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import au.com.penattilabs.variowatch.R

@Suppress("ktlint:standard:package-name")  // Suppress package name warning for R class import
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    object UI {
        const val DEFAULT_PRESSURE = 0f
        const val BUTTON_WIDTH_FRACTION = 0.8f
        const val HORIZONTAL_PADDING = 16
        const val VERTICAL_PADDING_SMALL = 8
        const val VERTICAL_PADDING_MEDIUM = 16
        const val VERTICAL_PADDING_LARGE = 24
    }

    private val _uiState = MutableStateFlow(MainActivityUiState())
    private val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()
    private lateinit var userPreferences: UserPreferences

    private data class MainActivityUiState(
        val isVarioRunning: Boolean = false,
        val currentPressure: Float = UI.DEFAULT_PRESSURE,
        val showSettings: Boolean = false
    )

    private val pressureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VarioService.ACTION_PRESSURE_UPDATE) {
                val pressure = intent.getFloatExtra(VarioService.EXTRA_PRESSURE, UI.DEFAULT_PRESSURE)
                android.util.Log.d(TAG, "Received broadcast pressure: $pressure hPa")
                _uiState.update { it.copy(currentPressure = pressure) }
                userPreferences.updateCurrentAltitude(pressure)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferences = (application as VarioWatchApplication).userPreferences
        
        ContextCompat.registerReceiver(
            this,
            pressureReceiver,
            IntentFilter(VarioService.ACTION_PRESSURE_UPDATE),
            ContextCompat.RECEIVER_EXPORTED
        )

        // Set up lifecycle-aware collection
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                setContent {
                    MaterialTheme {
                        val currentUiState by uiState.collectAsState()
                        val currentAltitude by remember { derivedStateOf { userPreferences.currentAltitude } }
                        val useMetricUnits by remember { derivedStateOf { userPreferences.useMetricUnits } }

                        if (currentUiState.showSettings) {
                            SettingsContent(
                                useMetricUnits = useMetricUnits,
                                onToggleUnits = { userPreferences.toggleUnitSystem() },
                                onBackClick = { toggleSettings(false) },
                                currentAltitude = currentAltitude,
                                onAdjustAltitude = { increase -> userPreferences.adjustAltitude(increase) }
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (!currentUiState.isVarioRunning) {
                                    Button(
                                        onClick = { startVarioService() },
                                        modifier = Modifier
                                            .padding(bottom = UI.VERTICAL_PADDING_SMALL.dp)
                                            .fillMaxWidth(UI.BUTTON_WIDTH_FRACTION),
                                        colors = ButtonDefaults.primaryButtonColors()
                                    ) {
                                        Text(text = stringResource(R.string.start_vario))
                                    }

                                    Button(
                                        onClick = { toggleSettings(true) },
                                        modifier = Modifier
                                            .padding(top = UI.VERTICAL_PADDING_SMALL.dp)
                                            .fillMaxWidth(UI.BUTTON_WIDTH_FRACTION),
                                        colors = ButtonDefaults.secondaryButtonColors()
                                    ) {
                                        Text(text = stringResource(R.string.settings))
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_SMALL.dp)
                                    ) {
                                        Text(
                                            text = AltitudeCalculator.formatAltitude(currentAltitude, useMetricUnits),
                                            modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_SMALL.dp)
                                        )

                                        Text(
                                            text = stringResource(R.string.pressure_format).format(currentUiState.currentPressure),
                                            modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_SMALL.dp)
                                        )
                                    }

                                    Button(
                                        onClick = { stopVarioService() },
                                        modifier = Modifier
                                            .padding(top = UI.VERTICAL_PADDING_SMALL.dp)
                                            .fillMaxWidth(UI.BUTTON_WIDTH_FRACTION),
                                        colors = ButtonDefaults.primaryButtonColors()
                                    ) {
                                        Text(text = stringResource(R.string.stop_vario))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pressureReceiver)
        super.onDestroy()
    }

    private fun startVarioService() {
        val serviceIntent = VarioService.createIntent(this)
        startService(serviceIntent)
        _uiState.update { it.copy(isVarioRunning = true) }
    }

    private fun stopVarioService() {
        stopService(Intent(this, VarioService::class.java))
        _uiState.update { it.copy(isVarioRunning = false) }
    }

    private fun toggleSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }
}

@Composable
private fun SettingsContent(
    useMetricUnits: Boolean,
    onToggleUnits: () -> Unit,
    onBackClick: () -> Unit,
    currentAltitude: Float,
    onAdjustAltitude: (increase: Boolean) -> Unit
) {
    BackHandler(onBack = onBackClick)
    
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MainActivity.UI.HORIZONTAL_PADDING.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
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
                    content = {
                        Button(
                            onClick = { onAdjustAltitude(false) },
                            modifier = Modifier.padding(4.dp),
                            content = { Text("−") }
                        )
                        
                        Button(
                            onClick = { onAdjustAltitude(true) },
                            modifier = Modifier.padding(4.dp),
                            content = { Text("+") }
                        )
                    }
                )
            }

            item {
                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(top = MainActivity.UI.VERTICAL_PADDING_LARGE.dp, bottom = MainActivity.UI.VERTICAL_PADDING_SMALL.dp)
                        .fillMaxWidth(),
                    content = { Text(text = stringResource(R.string.back)) }
                )
            }
        }
    )
}