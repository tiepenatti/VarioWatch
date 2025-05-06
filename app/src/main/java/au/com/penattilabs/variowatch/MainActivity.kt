package au.com.penattilabs.variowatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log // Add Log import
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Import sp for font size
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
        val VERTICAL_SPEED_FONT_SIZE = 36.sp // Define font size for vertical speed
    }

    private val _uiState = MutableStateFlow(MainActivityUiState())
    private val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()
    private lateinit var userPreferences: UserPreferences

    // Add StateFlows for QNH and Units
    private lateinit var _qnhState: MutableStateFlow<Float>
    private lateinit var qnhState: StateFlow<Float>
    private lateinit var _useMetricUnitsState: MutableStateFlow<Boolean>
    private lateinit var useMetricUnitsState: StateFlow<Boolean>

    private data class MainActivityUiState(
        val isVarioRunning: Boolean = false,
        val currentPressure: Float = UI.DEFAULT_PRESSURE,
        val currentAltitude: Float = Float.NaN, // Add altitude
        val verticalSpeed: Float = Float.NaN, // Add vertical speed
        val showSettings: Boolean = false
    )

    private val pressureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VarioService.ACTION_PRESSURE_UPDATE) {
                val pressure = intent.getFloatExtra(VarioService.EXTRA_PRESSURE, UI.DEFAULT_PRESSURE)
                val altitude = intent.getFloatExtra(VarioService.EXTRA_ALTITUDE, Float.NaN)
                val verticalSpeed = intent.getFloatExtra(VarioService.EXTRA_VERTICAL_SPEED, Float.NaN)
                android.util.Log.d(TAG, "Received broadcast: P=$pressure, Alt=$altitude, VS=$verticalSpeed")
                _uiState.update { it.copy(
                    currentPressure = pressure,
                    currentAltitude = altitude,
                    verticalSpeed = verticalSpeed
                ) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferences = (application as VarioWatchApplication).userPreferences

        // Initialize StateFlows
        _qnhState = MutableStateFlow(userPreferences.qnh)
        qnhState = _qnhState.asStateFlow()
        _useMetricUnitsState = MutableStateFlow(userPreferences.useMetricUnits)
        useMetricUnitsState = _useMetricUnitsState.asStateFlow()

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
                        // Collect states
                        val currentUseMetricUnits by useMetricUnitsState.collectAsState()

                        // Remove altitude calculation here as it's received from the broadcast
                        // val currentAltitude by remember(currentUiState.currentPressure, currentQnh) {
                        //     derivedStateOf {
                        //         AltitudeCalculator.calculateAltitude(currentUiState.currentPressure, currentQnh)
                        //     }
                        // }

                        if (currentUiState.showSettings) {
                            SettingsContent(
                                useMetricUnits = currentUseMetricUnits, // Use collected state
                                onToggleUnits = {
                                    userPreferences.toggleUnitSystem() // Update persistent storage
                                    _useMetricUnitsState.value = userPreferences.useMetricUnits // Update state flow
                                },
                                onBackClick = { toggleSettings(false) },
                                currentAltitude = currentUiState.currentAltitude, // Pass altitude from UI state
                                onAdjustAltitude = { increase ->
                                    adjustQnhBasedOnAltitudeAdjustment(increase)
                                }
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
                                        // Display Vertical Speed prominently
                                        Text(
                                            text = AltitudeCalculator.formatVerticalSpeed(currentUiState.verticalSpeed, currentUseMetricUnits),
                                            style = MaterialTheme.typography.display1, // Use a large style
                                            fontSize = UI.VERTICAL_SPEED_FONT_SIZE, // Apply custom large font size
                                            modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_MEDIUM.dp)
                                        )
                                        
                                        // Display Altitude
                                        Text(
                                            text = AltitudeCalculator.formatAltitude(currentUiState.currentAltitude, currentUseMetricUnits),
                                            modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_SMALL.dp)
                                        )

                                        // Display Pressure
                                        Text(
                                            text = stringResource(R.string.pressure_format).format(currentUiState.currentPressure),
                                            modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_SMALL.dp)
                                        )
                                        // Optional: Display QNH for debugging
                                        // Text(
                                        //     text = "QNH: %.2f".format(currentQnh),
                                        //     modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_SMALL.dp)
                                        // )
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
                                    
                                    // Add Settings button below Stop button
                                    Button(
                                        onClick = { toggleSettings(true) },
                                        modifier = Modifier
                                            .padding(top = UI.VERTICAL_PADDING_SMALL.dp)
                                            .fillMaxWidth(UI.BUTTON_WIDTH_FRACTION),
                                        colors = ButtonDefaults.secondaryButtonColors()
                                    ) {
                                        Text(text = stringResource(R.string.settings))
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
    
    // New function to handle QNH adjustment based on altitude steps
    private fun adjustQnhBasedOnAltitudeAdjustment(increase: Boolean) {
        val currentPressure = uiState.value.currentPressure
        val currentAltitude = uiState.value.currentAltitude // Use altitude from state

        if (currentPressure <= 0f) {
            Log.w(TAG, "Cannot adjust QNH without valid pressure reading.")
            return // Cannot adjust without a valid pressure reading
        }

        if (currentAltitude.isNaN()) {
             Log.w(TAG, "Cannot adjust QNH with invalid current altitude.")
            return // Cannot adjust if current altitude is invalid
        }

        // Use the collected state for unit-dependent step calculation
        val step = if (_useMetricUnitsState.value) 
            Constants.METRIC_ALTITUDE_STEP
        else
            Constants.IMPERIAL_ALTITUDE_STEP / Constants.METERS_TO_FEET // Convert feet step to meters

        val targetAltitude = currentAltitude + (if (increase) step else -step)
        val newQnh = AltitudeCalculator.calculateQnhFromAltitude(currentPressure, targetAltitude)

        // Update UserPreferences (persistent storage)
        userPreferences.updateQnh(newQnh)
        // Update the QNH StateFlow (triggers recomposition)
        _qnhState.value = newQnh

        // Log the adjustment
        Log.d(TAG, "Adjusted QNH based on altitude change. Current Pressure: $currentPressure, Target Altitude: $targetAltitude, New QNH: $newQnh")
    }
}

@Composable
private fun SettingsContent(
    useMetricUnits: Boolean,
    onToggleUnits: () -> Unit,
    onBackClick: () -> Unit,
    currentAltitude: Float, // Parameter remains the same
    onAdjustAltitude: (increase: Boolean) -> Unit // Parameter remains the same, implementation changed in MainActivity
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
                    text = AltitudeCalculator.formatAltitude(currentAltitude, useMetricUnits), // Use passed value
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