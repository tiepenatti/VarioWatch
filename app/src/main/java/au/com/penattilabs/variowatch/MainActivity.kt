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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope

class MainActivity : ComponentActivity() {
    private var isVarioRunning by mutableStateOf(false)
    private var currentPressure by mutableStateOf(0f)
    private var showSettings by mutableStateOf(false)
    private lateinit var userPreferences: UserPreferences

    companion object {
        private const val TAG = "MainActivity"
    }

    private val pressureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VarioService.ACTION_PRESSURE_UPDATE) {
                val pressure = intent.getFloatExtra(VarioService.EXTRA_PRESSURE, 0f)
                android.util.Log.d(TAG, "Received broadcast pressure: $pressure hPa")
                currentPressure = pressure
                userPreferences.updateCurrentAltitude(pressure)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferences = UserPreferences(this)
        
        ContextCompat.registerReceiver(
            this,
            pressureReceiver,
            IntentFilter(VarioService.ACTION_PRESSURE_UPDATE),
            ContextCompat.RECEIVER_EXPORTED
        )

        setContent {
            MaterialTheme {
                val currentAltitude by remember { derivedStateOf { userPreferences.currentAltitude } }
                val useMetricUnits by remember { derivedStateOf { userPreferences.useMetricUnits } }

                if (showSettings) {
                    SettingsContent(
                        useMetricUnits = useMetricUnits,
                        onToggleUnits = { userPreferences.toggleUnitSystem() },
                        onBackClick = { showSettings = false },
                        currentAltitude = currentAltitude,
                        onAdjustAltitude = { increase -> userPreferences.adjustAltitude(increase) }
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!isVarioRunning) {
                            Button(
                                onClick = { startVarioService() },
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth(0.8f),
                                colors = ButtonDefaults.primaryButtonColors()
                            ) {
                                Text(text = "Start Vario")
                            }

                            Button(
                                onClick = { showSettings = true },
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth(0.8f),
                                colors = ButtonDefaults.secondaryButtonColors()
                            ) {
                                Text(text = "Settings")
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = AltitudeCalculator.formatAltitude(currentAltitude, useMetricUnits),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Text(
                                    text = "%.1f hPa".format(currentPressure),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Button(
                                onClick = { stopVarioService() },
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth(0.8f),
                                colors = ButtonDefaults.primaryButtonColors()
                            ) {
                                Text(text = "Stop")
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
        startService(Intent(this, VarioService::class.java))
        isVarioRunning = true
    }

    private fun stopVarioService() {
        stopService(Intent(this, VarioService::class.java))
        isVarioRunning = false
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
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            item {
                Text(
                    text = "Settings",
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            item {
                Button(
                    onClick = onToggleUnits,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(text = if (useMetricUnits) "Altitude: m" else "Altitude: ft")
                }
            }

            item {
                Text(
                    text = "Calibrate Altitude",
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }
            
            item {
                Text(
                    text = AltitudeCalculator.formatAltitude(currentAltitude, useMetricUnits),
                    modifier = Modifier.padding(vertical = 8.dp)
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
                        .padding(top = 24.dp, bottom = 8.dp)
                        .fillMaxWidth(),
                    content = { Text(text = "Back") }
                )
            }
        }
    )
}