package nu.nuru.hellogravity.lights

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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nu.nuru.hellogravity.DebouncedLogger
import nu.nuru.hellogravity.HysteresisLogger
import nu.nuru.hellogravity.TAG
import nu.nuru.hellogravity.clip01
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * This is a rather hacky Bluetooth -> Philips Hue implementation. It kind of works, but the bulbs
 * are really not very reactive and become very unreliable with n>2.
 */
class LightsBluetooth(val activity: ComponentActivity)
//    : LightsInterface
{
    private var bluetoothGatts: MutableMap<String, BluetoothGatt> = mutableMapOf()
    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    private var nConnected: Int = 0
    private val hysteresisLogger = HysteresisLogger()
    private val debouncedLogger = DebouncedLogger()
    private val characteristicsMaps: MutableMap<String, Map<PhlbleEnum, BluetoothGattCharacteristic?>> = mutableMapOf()
    private val reentrantLock = ReentrantReadWriteLock()

    private val BT_SCAN_REQUEST_CODE = 1

    fun getI(): Int {
        return nConnected
    }

    fun init() {
        val bluetoothAdapter = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            showDialog("Bluetooth disabled", "Cannot scan for lights without Bluetooth")
            return
        }
        if (permissions.all { ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }) {
            doScan()
        } else {
            ActivityCompat.requestPermissions(activity, permissions, BT_SCAN_REQUEST_CODE)
        }
    }

    @SuppressLint("MissingPermission")
    fun setColor(color: LightColor) {
        CoroutineScope(Dispatchers.IO).launch {
            val lock = reentrantLock.readLock()
            lock.lock()
            try {
                for ((address, characteristicsMap) in characteristicsMaps) {
                    val characteristic = characteristicsMap.get(PhlbleEnum.COLOR)
                    if (characteristic != null) {
                        @Suppress("DEPRECATION")
                        characteristic.value = PhlbleEnum.COLOR.value(color.r, color.g, color.b)
                        @Suppress("DEPRECATION")
                        bluetoothGatts.get(address)!!.writeCharacteristic(characteristic)
                        hysteresisLogger.log(address, color.r);
                    } else {
                        debouncedLogger.log("color characteristic missing on $address")
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != BT_SCAN_REQUEST_CODE) {
            return false;
        }
        Log.v(TAG, "request permissions: permissions=$permissions")
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            doScan()
        } else {
            showDialog(
                "Missing permissions",
                "Cannot scan for lights without Bluetooth & fine location permissions"
            )
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun doScan() {
        val bluetoothAdapter = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

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
                        PhlbleEnum.BRIGHTNESS to service?.getCharacteristic(
                            PhlbleEnum.BRIGHTNESS.uuid)
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
                            val bluetoothGatt = device.connectGatt(activity, true, gattCallback)
                            bluetoothGatts[address] = bluetoothGatt
                            nConnected = bluetoothGatts.size
                        }
                    }
                }
            })
    }

    private fun showDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(activity)
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

    // https://github.com/npaun/philble/blob/master/philble/client.py
    private enum class PhlbleEnum(val code: Int) {
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

//    @SuppressLint("MissingPermission")
//    private fun lightsOnoff(value: Boolean) {
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
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun lightsBrightness(value: Float) {
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
//    }
}