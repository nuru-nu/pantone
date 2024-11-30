package nu.nuru.hellogravity

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nu.nuru.hellogravity.ui.theme.HelloGravityTheme

@Composable
fun UserInterface(
    context: MainActivity? = null,
    n: Int = 0,
    serviceState: ServiceState = ServiceState(),
) {
    Surface() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            val text = (
                "v=4\n\n" +
                "n=${n}\n" +
                serviceState.sensorData.serializeMultiline() + "\n\n" +
                serviceState.stats.toString() + "\n\n" +
                serviceState.connectionStatus
            )

            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Gray,
                )
                Button(
                    modifier = Modifier.padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Gray,
                    ),
                    onClick = {
                        Log.v(TAG, "clicked start")
                        context?.startService()
                    }
                ) {
                    Text(text = "start")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    modifier = Modifier.padding(16.dp),
                    colors = colors,
                    onClick = {
                        Log.v(TAG, "clicked stop")
                        context?.stopService()
                    }
                ) {
                    Text(text = "stop")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    modifier = Modifier.padding(16.dp),
                    colors = colors,
                    onClick = {
                        Log.v(TAG, "clicked settings")
                        context?.showSettings()
                    }
                ) {
                    Text(text = "settings")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HelloGravityTheme {
        UserInterface()
    }
}