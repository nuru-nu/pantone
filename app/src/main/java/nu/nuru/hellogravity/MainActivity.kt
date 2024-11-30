package nu.nuru.hellogravity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import nu.nuru.hellogravity.ui.theme.HelloGravityTheme


const val TAG = "hellogravity"

class MainActivity : ComponentActivity() { //, SensorEventListener {

    private var dispN: Int by mutableStateOf(1)  // triggers UI update
    private var serviceState = ServiceState(SensorData())

    fun startService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    fun stopService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        this.stopService(serviceIntent)
    }

    fun showSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")

        Log.v(TAG, "MainActivity.onCreate()")
        setContent {
            HelloGravityTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize()) {
                    UserInterface(this, dispN, serviceState)
                }
            }
        }

        (application as MyApplication).sharedViewModel.liveData.observe(this) { data ->
            serviceState = data
            dispN++
        }
    }

//    private fun mdnsInit() {
//        val jmdns = JmDNSImpl.create(InetAddress.getLocalHost())
//        val hostname = "dmxserver.local"
//        val address = jmdns.getServiceInfo("_http._tcp.local.", hostname)?.inetAddresses?.get(0)
//    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "onRestart")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop")
    }
}
