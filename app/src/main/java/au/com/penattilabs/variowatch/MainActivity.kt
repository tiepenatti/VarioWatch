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

    private val pressureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VarioService.ACTION_PRESSURE_UPDATE) {
                currentPressure = intent.getFloatExtra(VarioService.EXTRA_PRESSURE, 0f)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            pressureReceiver,
            IntentFilter(VarioService.ACTION_PRESSURE_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            HomeScreen(
                isVarioRunning = isVarioRunning,
                currentPressure = currentPressure,
                onStartClick = { startVarioService() },
                onStopClick = { stopVarioService() },
                onSettingsClick = { /* TODO: Navigate to settings */ }
            )
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
fun HomeScreen(
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
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(text = "Start Vario")
            }

            Button(
                onClick = onSettingsClick,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Settings")
            }
        } else {
            Text(
                text = "Pressure: %.2f hPa".format(currentPressure),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = onStopClick,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Stop")
            }
        }
    }
}