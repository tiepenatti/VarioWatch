package au.com.penattilabs.variowatch

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import au.com.penattilabs.variowatch.SettingsContent
import au.com.penattilabs.variowatch.VerticalSpeedIndicator

@Suppress("ktlint:standard:package-name")
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    object UI {
        const val DEFAULT_PRESSURE = 0f
        const val BUTTON_WIDTH_FRACTION = 0.6f
        const val HORIZONTAL_PADDING = 16
        const val VERTICAL_PADDING_ZERO = 0
        const val VERTICAL_PADDING_TINY = 3
        const val VERTICAL_PADDING_SMALL = 8
        const val VERTICAL_PADDING_MEDIUM = 16
        const val VERTICAL_PADDING_LARGE = 24
        val VERTICAL_SPEED_FONT_SIZE = 36.sp
    }

    private val _uiState = MutableStateFlow(MainActivityUiState())
    private val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()
    private lateinit var userPreferences: UserPreferences

    private lateinit var _qnhState: MutableStateFlow<Float>
    private lateinit var qnhState: StateFlow<Float>
    private lateinit var _useMetricUnitsState: MutableStateFlow<Boolean>
    private lateinit var useMetricUnitsState: StateFlow<Boolean>

    private data class MainActivityUiState(
        val isVarioRunning: Boolean = false,
        val currentPressure: Float = UI.DEFAULT_PRESSURE,
        val currentAltitude: Float = Float.NaN,
        val verticalSpeed: Float = Float.NaN,
        val showSettings: Boolean = false
    )

    private val pressureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VarioService.ACTION_PRESSURE_UPDATE) {
                val pressure = intent.getFloatExtra(VarioService.EXTRA_PRESSURE, UI.DEFAULT_PRESSURE)
                val altitude = intent.getFloatExtra(VarioService.EXTRA_ALTITUDE, Float.NaN)
                val verticalSpeed = intent.getFloatExtra(VarioService.EXTRA_VERTICAL_SPEED, Float.NaN)
                Log.d(TAG, getString(R.string.log_received_broadcast_pressure_update, pressure, altitude, verticalSpeed))
                _uiState.update { it.copy(
                    currentPressure = pressure,
                    currentAltitude = altitude,
                    verticalSpeed = verticalSpeed
                ) }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, getString(R.string.log_permission_granted_post_notifications))
            // Permission is granted. You can now start the service if it wasn't started.
            // Or, if you have a specific flow that depends on this, trigger it here.
        } else {
            Log.w(TAG, getString(R.string.log_permission_denied_post_notifications))
            // Explain to the user that the feature is unavailable because the
            // features requires a permission that the user has denied. At the
            // same time, respect the user's decision. Don't link to system
            // settings in an effort to convince the user to change their
            // decision.
        }
    }

    private fun askNotificationPermission() {
        // This is only required on API level 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, getString(R.string.log_permission_already_granted_post_notifications))
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // note: we could consider adding a message explaining why we need to show notifications but keep simple for now
                Log.i(TAG, getString(R.string.log_showing_rationale_post_notifications))
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Directly ask for the permission
                Log.d(TAG, getString(R.string.log_requesting_post_notifications))
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferences = (application as VarioWatchApplication).userPreferences

        askNotificationPermission()

        // Initialize StateFlows
        _qnhState = MutableStateFlow(userPreferences.qnh)
        qnhState = _qnhState.asStateFlow()
        _useMetricUnitsState = MutableStateFlow(userPreferences.useMetricUnits)
        useMetricUnitsState = _useMetricUnitsState.asStateFlow()

        // Use LocalBroadcastManager to register the receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            pressureReceiver,
            IntentFilter(VarioService.ACTION_PRESSURE_UPDATE)
        )
        Log.d(TAG, getString(R.string.log_pressure_receiver_registered))

        // Set up lifecycle-aware collection
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                setContent {
                    MaterialTheme {
                        val currentUiState by uiState.collectAsState()
                        // Collect states
                        val currentUseMetricUnits by useMetricUnitsState.collectAsState()

                        if (currentUiState.showSettings) {
                            SettingsContent(
                                useMetricUnits = currentUseMetricUnits,
                                onToggleUnits = {
                                    userPreferences.toggleUnitSystem()
                                    _useMetricUnitsState.value = userPreferences.useMetricUnits
                                },
                                onBackClick = { toggleSettings(false) },
                                currentAltitude = currentUiState.currentAltitude,
                                onAdjustAltitude = { increase ->
                                    adjustQnhBasedOnAltitudeAdjustment(increase)
                                },
                                userPreferences = userPreferences
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    // Main content (buttons or text+buttons) is drawn first
                                    if (!currentUiState.isVarioRunning) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(UI.VERTICAL_PADDING_SMALL.dp)
                                        ) {
                                            Button(
                                                onClick = { startVarioService() },
                                                modifier = Modifier
                                                    .fillMaxWidth(UI.BUTTON_WIDTH_FRACTION),
                                                colors = ButtonDefaults.primaryButtonColors()
                                            ) {
                                                Text(text = stringResource(R.string.start_vario))
                                            }

                                            Button(
                                                onClick = { toggleSettings(true) },
                                                modifier = Modifier
                                                    .fillMaxWidth(UI.BUTTON_WIDTH_FRACTION),
                                                colors = ButtonDefaults.secondaryButtonColors()
                                            ) {
                                                Text(text = stringResource(R.string.settings))
                                            }
                                        }
                                    } else { // This is when currentUiState.isVarioRunning is true
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_ZERO.dp)
                                            ) {
                                                Text(
                                                    text = AltitudeCalculator.formatAltitude(currentUiState.currentAltitude, currentUseMetricUnits)
                                                )

                                                Text(
                                                    text = AltitudeCalculator.formatVerticalSpeed(currentUiState.verticalSpeed, currentUseMetricUnits),
                                                    style = MaterialTheme.typography.display1,
                                                    fontSize = UI.VERTICAL_SPEED_FONT_SIZE
                                                )
                                            
                                                // Display Pressure and QNH (not in use for now)
                                                /*
                                                Text(
                                                    text = stringResource(R.string.pressure_format).format(currentUiState.currentPressure),
                                                )
                                                Text(
                                                     text = "QNH: %.2f".format(currentQnh),
                                                     modifier = Modifier.padding(bottom = UI.VERTICAL_PADDING_SMALL.dp)
                                                 )*/
                                            }

                                            Button(
                                                onClick = { stopVarioService() },
                                                modifier = Modifier
                                                    .padding(top = UI.VERTICAL_PADDING_ZERO.dp)
                                                    .fillMaxWidth(UI.BUTTON_WIDTH_FRACTION),
                                                colors = ButtonDefaults.primaryButtonColors()
                                            ) {
                                                Text(text = stringResource(R.string.stop_vario))
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
                                        }
                                    }

                                    // Vertical Speed Indicator - Drawn last to be in the foreground
                                    if (currentUiState.isVarioRunning) {
                                        VerticalSpeedIndicator(
                                            verticalSpeed = currentUiState.verticalSpeed,
                                            useMetricUnits = currentUseMetricUnits
                                        )
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
        // Use LocalBroadcastManager to unregister the receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pressureReceiver)
        Log.d(TAG, getString(R.string.log_pressure_receiver_unregistered))
        super.onDestroy()
    }

    private fun startVarioService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, getString(R.string.log_cannot_start_service_permission_denied))
            askNotificationPermission() // Request permission if not granted
            // Optionally, inform the user that the service cannot start without the permission.
            // You might want to prevent the service from starting or update UI accordingly.
            return // Prevent starting if permission is critical and not granted
        }

        val serviceIntent = VarioService.createIntent(this)
        ContextCompat.startForegroundService(this, serviceIntent)
        _uiState.update { it.copy(isVarioRunning = true) }
    }

    private fun stopVarioService() {
        stopService(Intent(this, VarioService::class.java))
        _uiState.update { it.copy(isVarioRunning = false) }
    }

    private fun toggleSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }
    
    private fun adjustQnhBasedOnAltitudeAdjustment(increase: Boolean) {
        val currentPressure = uiState.value.currentPressure
        val currentAltitude = uiState.value.currentAltitude // Use altitude from state

        if (currentPressure <= 0f) {
            Log.w(TAG, getString(R.string.log_adjust_qnh_invalid_pressure))
            return // Cannot adjust without a valid pressure reading
        }

        if (currentAltitude.isNaN()) {
             Log.w(TAG, getString(R.string.log_adjust_qnh_invalid_altitude))
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
        Log.d(TAG, getString(R.string.log_qnh_adjusted, currentPressure, targetAltitude, newQnh))
    }
}
