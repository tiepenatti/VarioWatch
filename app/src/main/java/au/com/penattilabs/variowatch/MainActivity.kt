package au.com.penattilabs.variowatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var isVarioRunning by mutableStateOf(false)
    private var currentPressure by mutableStateOf(0f)
    private var showSettings by mutableStateOf(false)
    private lateinit var userPreferences: UserPreferences

    private val pressureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VarioService.ACTION_PRESSURE_UPDATE) {
                currentPressure = intent.getFloatExtra(VarioService.EXTRA_PRESSURE, 0f)
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
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            MaterialTheme {
                if (showSettings) {
                    SettingsScreen(
                        useMetricUnits = userPreferences.useMetricUnits,
                        onToggleUnits = { userPreferences.toggleUnitSystem() },
                        onBackClick = { showSettings = false },
                        qnh = userPreferences.qnh,
                        onQnhChange = { userPreferences.qnh = it },
                        currentAltitude = userPreferences.manualAltitude,
                        onSetCurrentAltitude = { userPreferences.manualAltitude = it }
                    )
                } else {
                    HomeContent(
                        isVarioRunning = isVarioRunning,
                        currentPressure = currentPressure,
                        onStartClick = { startVarioService() },
                        onStopClick = { stopVarioService() },
                        onSettingsClick = { showSettings = true }
                    )
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

    @Composable
    private fun HomeContent(
        isVarioRunning: Boolean,
        currentPressure: Float,
        onStartClick: () -> Unit,
        onStopClick: () -> Unit,
        onSettingsClick: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isVarioRunning) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth(0.8f),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Text(text = "Start Vario")
                }

                Button(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.8f),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text(text = "Settings")
                }
            } else {
                val altitude = userPreferences.manualAltitude ?: 
                    AltitudeCalculator.calculateAltitude(currentPressure, userPreferences.qnh)
                
                Text(
                    text = AltitudeCalculator.formatAltitude(altitude, userPreferences.useMetricUnits),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Pressure: %.2f hPa".format(currentPressure),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = onStopClick,
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

@Composable
fun SettingsScreen(
    useMetricUnits: Boolean,
    onToggleUnits: () -> Unit,
    onBackClick: () -> Unit,
    qnh: Float,
    onQnhChange: (Float) -> Unit,
    currentAltitude: Float?,
    onSetCurrentAltitude: (Float?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onToggleUnits,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = if (useMetricUnits) "Using Metric" else "Using Imperial")
        }

        Text(
            text = "QNH (hPa)",
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        
        Button(
            onClick = { onQnhChange(qnh + 0.25f) },
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(text = "▲")
        }
        
        Text(
            text = "%.2f".format(qnh),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Button(
            onClick = { onQnhChange(qnh - 0.25f) },
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(text = "▼")
        }

        if (currentAltitude != null) {
            Text(
                text = "Manual Altitude Set",
                modifier = Modifier.padding(top = 16.dp)
            )
            Button(
                onClick = { onSetCurrentAltitude(null) },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(text = "Clear Manual Altitude")
            }
        }

        Button(
            onClick = onBackClick,
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
        ) {
            Text(text = "Back")
        }
    }
}