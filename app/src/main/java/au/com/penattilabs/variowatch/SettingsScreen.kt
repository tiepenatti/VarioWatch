package au.com.penattilabs.variowatch

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*

@Composable
fun SettingsScreen(
    useMetricUnits: Boolean,
    onToggleUnits: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Settings",
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Button(
            onClick = onToggleUnits,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth(0.8f),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Text(text = if (useMetricUnits) "Alt: meter" else "Alt: feet")
        }
        
        Button(
            onClick = onBackClick,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth(0.8f),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text(text = "Back")
        }
    }
}