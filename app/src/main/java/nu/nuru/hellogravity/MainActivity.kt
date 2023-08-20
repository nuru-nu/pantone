package nu.nuru.hellogravity

import android.bluetooth.*
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nu.nuru.hellogravity.lights.LightColor
import nu.nuru.hellogravity.lights.LightsInterface
import nu.nuru.hellogravity.lights.LightsOsc
import nu.nuru.hellogravity.ui.theme.HelloGravityTheme
import java.net.*
import java.util.*

const val TAG = "hellogravity"


class MainActivity : ComponentActivity(), SensorEventListener {

    private var backgroundColor: Color by mutableStateOf(Color.Cyan)
    private var dispX: Float by mutableStateOf(0.0F)
    private var dispY: Float by mutableStateOf(0.0F)
    private var dispZ: Float by mutableStateOf(0.0F)
    private var i = 0

//    private val lights: LightsInterface = LightsBluetooth(this)
    private val lights: LightsInterface = LightsOsc("/dmx/universe/0", "192.168.1.41")

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!lights.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        sensorManager.registerListener(this, sensor, 10000)

        Log.v(TAG, "MainActivity.onCreate()")
        setContent {
            HelloGravityTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    Greeting(backgroundColor, dispX, dispY, dispZ, lights.getI())
                }
            }
        }

    }

//    private fun mdnsInit() {
//        val jmdns = JmDNSImpl.create(InetAddress.getLocalHost())
//        val hostname = "dmxserver.local"
//        val address = jmdns.getServiceInfo("_http._tcp.local.", hostname)?.inetAddresses?.get(0)
//    }

    private val toColor = ToColor()
    override fun onSensorChanged(e: SensorEvent?) {


        val x = e!!.values[0]
        val y = e!!.values[1]
        val z = e!!.values[2]

        val (r, g, b) = toColor.zToRb(x, y, z);

        i++;

        lights.setColor(LightColor(r, g, b))

        if (i % 10 == 0) {
            dispX = x
            dispY = y
            dispZ = z
//            Log.v(TAG, "x=$x y=$y z=$z h=$h s=$s l=$l")
        }
        backgroundColor = Color(clip01(r), clip01(g), clip01(b))
    }

    override fun onAccuracyChanged(e: Sensor?, acc: Int) {
        Log.v(TAG, "onAccuracyChanged")
    }
}

@Composable
fun Greeting(backgroundColor: Color, x: Float, y: Float, z: Float, i: Int = 0, j: Int = 0) {
    var minX by remember { mutableStateOf(0.0F) }
    var maxX by remember { mutableStateOf(0.0F) }
    var minY by remember { mutableStateOf(0.0F) }
    var maxY by remember { mutableStateOf(0.0F) }
    var minZ by remember { mutableStateOf(0.0F) }
    var maxZ by remember { mutableStateOf(0.0F) }
    Surface(color = backgroundColor) {
        minX = Math.min(x, minX)
        maxX = Math.max(x, maxX)
        minY = Math.min(y, minY)
        maxY = Math.max(y, maxY)
        minZ = Math.min(z, minZ)
        maxZ = Math.max(z, maxZ)
        Text(
            text = """
                   x=%+.3f [%+.3f..%+.3f]
                   y=%+.3f [%+.3f..%+.3f]
                   z=%+.3f [%+.3f..%+.3f]
                   i=%d j=%d
                   """.trimIndent().format(
                x, minX, maxX,
                y, minY, maxY,
                z, minZ, maxZ,
                i, j),
            modifier = Modifier.padding(12.dp),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HelloGravityTheme {
        Greeting( Color.Red, 0.0F, 0.0F, 0.0F)
    }
}