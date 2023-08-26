package nu.nuru.hellogravity

import android.bluetooth.*
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.time.Instant
import java.util.*

const val TAG = "hellogravity"


class MainActivity : ComponentActivity(), SensorEventListener {

    private var backgroundColor: Color by mutableStateOf(Color.Cyan)
    private var i = 0
    private var dispN: Int by mutableStateOf(1)


    //    private val lights: LightsInterface = LightsBluetooth(this)
    private val lights: LightsInterface = LightsOsc("/dmx/universe/0", "192.168.1.41")

    private var valuesLogger: ValuesLogger? = null
    private val handler = Handler(Looper.getMainLooper())

    private val runnable = object : Runnable {
        override fun run() {
            valuesLogger!!.write()
            handler.postDelayed(this, 10000)
        }
    }


    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!lights.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), 10000)
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 10000)
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 10000)

        Log.v(TAG, "MainActivity.onCreate()")
        setContent {
            HelloGravityTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    Greeting(dispN, backgroundColor, sensorData, lights.getI())
                }
            }
        }

        valuesLogger = ValuesLogger(
            getExternalFilesDir(null) ?: this.filesDir,
            listOf("t") + SensorData.getColumns()
        )
        handler.postDelayed(runnable, 1000)

    }

//    private fun mdnsInit() {
//        val jmdns = JmDNSImpl.create(InetAddress.getLocalHost())
//        val hostname = "dmxserver.local"
//        val address = jmdns.getServiceInfo("_http._tcp.local.", hostname)?.inetAddresses?.get(0)
//    }

    private val toColor = ToColor()

    val sensorData = SensorData()
    val t0 = System.currentTimeMillis()
    override fun onSensorChanged(e: SensorEvent?) {

        if (e == null) return
        sensorData.update(e)
        if (e.sensor.type != Sensor.TYPE_GRAVITY) return

        val t = (System.currentTimeMillis() - t0) / 1e3f
        valuesLogger!!.log(floatArrayOf(t) + sensorData.getValues())

        val (r, g, b) = toColor.zToRb(sensorData);

        i++;

        lights.setColor(LightColor(r, g, b))

        if (i % 10 == 0) {
            dispN++
        }
        backgroundColor = Color(clip01(r), clip01(g), clip01(b))
    }

    override fun onAccuracyChanged(e: Sensor?, acc: Int) {
        Log.v(TAG, "onAccuracyChanged")
    }
}

@Composable
fun Greeting(n: Int = 0, backgroundColor: Color = Color.White, data: SensorData = SensorData(), i: Int = 0, j: Int = 0) {
    Surface(color = backgroundColor) {
        Text(
            text = data.serializeMultiline() + "\ni=$i j=$j\nn=$n",
            modifier = Modifier.padding(12.dp),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HelloGravityTheme {
        Greeting()
    }
}