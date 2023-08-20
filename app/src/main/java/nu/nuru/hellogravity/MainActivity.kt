package nu.nuru.hellogravity

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nu.nuru.hellogravity.ui.theme.HelloGravityTheme
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock

const val TAG = "hellogravity"


class MainActivity : ComponentActivity(), SensorEventListener {

    private var backgroundColor: Color by mutableStateOf(Color.Cyan)
    private var dispX: Float by mutableStateOf(0.0F)
    private var dispY: Float by mutableStateOf(0.0F)
    private var dispZ: Float by mutableStateOf(0.0F)
    private var dispN: Int by mutableStateOf(0)
    private var i = 0
    private val BT_SCAN_REQUEST_CODE = 1
    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    private var bluetoothGatts: MutableMap<String, BluetoothGatt> = mutableMapOf()
//    var characteristicsMap: Map<PhlbleEnum, BluetoothGattCharacteristic?>? = null
    val characteristicsMaps: MutableMap<String, Map<PhlbleEnum, BluetoothGattCharacteristic?>> = mutableMapOf()
    val reentrantLock = ReentrantReadWriteLock()

    // https://github.com/npaun/philble/blob/master/philble/client.py
    enum class PhlbleEnum(val code: Int) {
        SERVICE(0), ONOFF(2), BRIGHTNESS(3), COLOR(5);
        val uuid: UUID
            get() = UUID.fromString("932c32bd-%04d-47a2-835a-a8d455b859dd".format(code))
        fun value(r: Float, g: Float, b: Float): ByteArray {
            // "formula of dubious accuracy"
            // https://github.com/npaun/philble/blob/c70396ca6d550c2010f0be781ed997cc8c93beaf/philble/client.py#L87
            assert(code == COLOR.code)
            val ints = arrayOf(/* RGB -> RBG */ r, b, g).map{ (clip01(it) * 255).toInt().coerceAtLeast(1) }
            val adjusted = ints.map{ (255 * it / ints.sum()).toInt() }
            return arrayOf(1).plus(adjusted).map{ it.toUByte().toByte() }.toByteArray()
        }
        fun value(onoff: Boolean): ByteArray {
            assert(code == ONOFF.code)
            return byteArrayOf(if (onoff) 1 else 0)
        }
        fun value(brightness: Float): ByteArray {
            assert(code == BRIGHTNESS.code)
            val level = 1 + (clip01(brightness) * 253).toInt()
            return byteArrayOf(level.toUByte().toByte())
        }
    }

    @SuppressLint("MissingPermission")
    fun doScan() {
        val bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.v(TAG, "bluetooth onConnectionStateChange($status, $newState)")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.v(TAG, "bluetooth onConnectionStateChange($status, newState=STATE_CONNECTED)")
                    gatt.discoverServices()
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.v(TAG, "bluetooth onServicesDiscovered($status)")
                // https://www.reddit.com/r/Hue/comments/eq0y3y/philips_hue_bluetooth_developer_documentation/
                // Read and write data to characteristics
                val service = gatt.getService(PhlbleEnum.SERVICE.uuid)
                val lock = reentrantLock.writeLock()
                lock.lock()
                try {
                    characteristicsMaps[gatt.device.address] = mapOf(
                        PhlbleEnum.ONOFF to service?.getCharacteristic(PhlbleEnum.ONOFF.uuid),
                        PhlbleEnum.COLOR to service?.getCharacteristic(PhlbleEnum.COLOR.uuid),
                        PhlbleEnum.BRIGHTNESS to service?.getCharacteristic(PhlbleEnum.BRIGHTNESS.uuid)
                    )
                } finally {
                    lock.unlock()
                }
//                val characteristic: BluetoothGattCharacteristic? =
//                    gatt.getService(PhlbleEnum.SERVICE.uuid)?.getCharacteristic(PhlbleEnum.COLOR.uuid)
////                gatt.getService(PhlbleEnum.SERVICE.uuid)?.getCharacteristic(PhlbleEnum.ONOFF.uuid)
//                if (characteristic != null) {
//                    Log.v(TAG, "bluetooth writing uuid=" + PhlbleEnum.COLOR.uuid + " value=" + PhlbleEnum.COLOR.value(0F, 1F, 0F).contentToString())
//                    @Suppress("DEPRECATION")
//                    characteristic.value = PhlbleEnum.COLOR.value(0F, 1F, 0F)
//                    @Suppress("DEPRECATION")
//                    gatt.writeCharacteristic(characteristic)
////                    gatt.writeCharacteristic(
////                        characteristic,
////                        byteArrayOf(0),
////                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
////                    )
//                } else {
//                    Log.v(TAG, "bluetooth getCharacteristic returned NULL!")
//                }
            }
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
//                Log.v(TAG, "bluetooth onCharacteristicRead($characteristic, $status)")
                // Handle read operation
            }
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
//                Log.v(TAG, "bluetooth onCharacteristicWrite($characteristic, $status)")
                // Handle write operation
            }
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
//                Log.v(TAG, "bluetooth onCharacteristicChanged($characteristic)")
                // Handle notification/indication
            }
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
//                Log.v(TAG, "bluetooth onDescriptorWrite($descriptor, $status)")
                // Handle descriptor write operation
            }
        }

        val scanFilters = listOf<ScanFilter>(
            ScanFilter.Builder().setDeviceName("Hue color lamp").build()
        )
        val scanSettings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        Log.v(TAG, "bluetooth start scan")
        bluetoothAdapter.bluetoothLeScanner.startScan(
            scanFilters,
            scanSettings,
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.device?.let { device ->
                        val name = device.name
                        val address = device.address
                        if (device.name == "Hue color lamp" && !bluetoothGatts.contains(address)) {
                            Log.v(TAG, "bluetooth adding device.name='$name' device.address='$address'")
                            val bluetoothGatt = device.connectGatt(this@MainActivity, true, gattCallback)
                            bluetoothGatts[address] = bluetoothGatt
                            dispN = bluetoothGatts.size
                        }
                    }
                }
            })
    }

    fun showDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        builder.setOnCancelListener {
            Log.w(TAG, "Dialog '$title' cancelled")
        }
        val dialog = builder.create()
        dialog.show()
    }

    fun bluetoothInit() {
        val bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            showDialog("Bluetooth disabled", "Cannot scan for lights without Bluetooth")
            return
        }
        if (permissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            doScan()
        } else {
            ActivityCompat.requestPermissions(this, permissions, BT_SCAN_REQUEST_CODE)
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            BT_SCAN_REQUEST_CODE -> {
                Log.v(TAG, "request permissions: permissions=$permissions")
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    doScan()
                } else {
                    showDialog("Missing permissions", "Cannot scan for lights without Bluetooth & fine location permissions")
                }
                return
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
                    Greeting(backgroundColor, dispX, dispY, dispZ, dispN)
                }
            }
        }

//        bluetoothInit()
//        mdnsInit()
    }

//    private fun mdnsInit() {
//        val jmdns = JmDNSImpl.create(InetAddress.getLocalHost())
//        val hostname = "dmxserver.local"
//        val address = jmdns.getServiceInfo("_http._tcp.local.", hostname)?.inetAddresses?.get(0)
//    }

    private val logTimers = HashMap<String, Long>()
    private fun logDebounced(message: String, debounceSecs: Float = 10.0f) {
        val currentTime = System.currentTimeMillis()
        val lastLogTime = logTimers[message] ?: 0
        if (currentTime - lastLogTime >= debounceSecs * 1000) {
            logTimers[message] = currentTime
            Log.v(TAG, message)
        }
    }

    @SuppressLint("MissingPermission")
    fun lightsOnoff(value: Boolean) {
//        CoroutineScope(Dispatchers.IO).launch {
//            val onoff = characteristicsMaps?.get(PhlbleEnum.ONOFF)
//            if (onoff != null) {
//                Log.v(TAG, "i=$i going to switch onoff=$value")
//                @Suppress("DEPRECATION")
//                onoff.value = PhlbleEnum.ONOFF.value(value)
//                @Suppress("DEPRECATION")
//                for (bluetoothGatt in bluetoothGatts.values) {
//                    bluetoothGatt.writeCharacteristic(onoff)
//                }
//            } else {
//                logDebounced("onoff characteristic missing")
//            }
//        }
    }

    @SuppressLint("MissingPermission")
    fun lightsBrightness(value: Float) {
//        CoroutineScope(Dispatchers.IO).launch {
//            val brigthness = characteristicsMap?.get(PhlbleEnum.BRIGHTNESS)
//            if (brigthness != null) {
////                Log.v(TAG, "i=$i going to set brigthness=$value")
//                @Suppress("DEPRECATION")
//                brigthness.value = PhlbleEnum.BRIGHTNESS.value(value)
//                @Suppress("DEPRECATION")
//                for (bluetoothGatt in bluetoothGatts.values) {
//                    bluetoothGatt.writeCharacteristic(brigthness)
//                }
//            } else {
////                logDebounced("brigthness characteristic missing")
//            }
//        }
    }

    private val hystStates: MutableMap<String, Boolean> = mutableMapOf()
    private val hystMutex = Mutex()
    suspend fun logHyst(device: String, x: Float) {
        hystMutex.withLock {
            if (!hystStates.contains(device)) {
                hystStates[device] = false
            }
            if (hystStates[device] == true && x < 0.8) {
                hystStates[device] = false
            }
            if (hystStates[device] == true && x > 0.9) {
                Log.v(TAG, "logHyst: $device OFF->ON")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun bluetoothColor(r: Float, g: Float, b: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            val lock = reentrantLock.readLock()
            lock.lock()
            try {
                for ((address, characteristicsMap) in characteristicsMaps) {
                    val color = characteristicsMap.get(PhlbleEnum.COLOR)
                    if (color != null) {
                        @Suppress("DEPRECATION")
                        color.value = PhlbleEnum.COLOR.value(r, g, b)
                        @Suppress("DEPRECATION")
                        bluetoothGatts.get(address)!!.writeCharacteristic(color)
                        logHyst(address, r);
                    } else {
                        logDebounced("color characteristic missing on $address")
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    private fun writeString(s: String, n: Int = 4): ByteArray {
        require(s is String) { "s must be a String" }
        val b = s.toByteArray(Charsets.UTF_8)
        val diff = n - (b.size % n)
        val padding = ByteArray(diff) { 0 }
        return b + padding
    }

    private fun writeBlob(x: ByteArray, n: Int = 4): ByteArray {
        require(x is ByteArray) { "x must be a ByteArray" }
        var sz = 4 + x.size
        if (sz % n != 0) sz += n - (sz % n)
        val b = ByteBuffer.allocate(sz).order(ByteOrder.BIG_ENDIAN)
        b.putInt(x.size)
        b.put(x)
        while (b.position() % n != 0) {
            b.put(0)
        }
        return b.array()
    }

    var udpSendErrors = 0
    var udpSocket: DatagramSocket? = null
    suspend fun sendMessage(address: String, bs: ByteArray, ip: String, port: Int) {
        withContext(Dispatchers.IO) {
            val b = writeString(address) + writeString(",b") + writeBlob(bs)
            if (udpSocket == null) {
                udpSocket = DatagramSocket()
            }
            val serverAddress = InetAddress.getByName(ip)
            val packet = DatagramPacket(b, b.size, serverAddress, port)
            try {
                udpSocket!!.send(packet)
            } catch (e: Exception) {
                udpSendErrors++
            }
        }
    }

    private fun udpColor(r: Float, g: Float, b: Float) {
        val values = UByteArray(512) { 0U }
        val toValue = { x: Float -> (x * 255).toInt().coerceIn(0, 255).toUByte() }
        for (device in 0..10) {
            values[device * 8 + 0] = 255U
            values[device * 8 + 1] = toValue(r)
            values[device * 8 + 2] = toValue(g)
            values[device * 8 + 3] = toValue(b)
        }
        runBlocking {
            sendMessage("/dmx/universe/0", values.toByteArray(), "192.168.1.41", 7770)
        }
    }

    override fun onSensorChanged(e: SensorEvent?) {

        fun xyzToRgb(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
            val r = (x + 10) / 20
            val g = (y + 10) / 20
            val b = (z + 10) / 20
            return Triple(r, g, b)
        }

        fun zToRb(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
            val r = (10 + z) / 20
            val g = 0F
            val b = (10 - z) / 20
            return Triple(r, g, b)
        }

        val x = e!!.values[0]
        val y = e!!.values[1]
        val z = e!!.values[2]

//        val (r, g, b) = xyzToRgb(x, y, z);
        val (r, g, b) = zToRb(x, y, z);

//        val h = (y+10) / 20 * 360
//        val s = (x+10) / 20
//        val l = (z+10) / 20
        i++;
//        lightsBrightness((y + 10) / 20)

//        bluetoothColor(r, g, b)
        udpColor(r, g, b)

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
        Greeting( Color.Red, 0.0F, 0.0F, 0.0F, 0, 0)
    }
}