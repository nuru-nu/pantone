package nu.nuru.hellogravity

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Timer
import java.util.TimerTask


interface TcpClientListener {
    fun addressChanged(address: String?);
    fun inControlChanged(inControl: Boolean);
    fun connectedChanged(connected: Boolean);
}

class Client(
    private val tcpPort: Int = 9000,
    private val udpPort: Int = 9001,
) {

    private var serverIp: String? = null
    private var udpSocket: DatagramSocket = DatagramSocket()
    private var serverAddress: InetAddress? = null
    private var udpSent = 0
    private var udpSendErrors = 0
    val networkScope = CoroutineScope(Dispatchers.IO)
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null

    private var data = JSONObject()
    private var address: String? = null
    private var connected: Boolean = false
    private var inControl: Boolean = false
    private var pings: Int = 0

    private val timer = Timer()
    private var heartbeatTask: TimerTask? = null
    private var retryTask: TimerTask? = null
    private val listeners: MutableSet<TcpClientListener> = mutableSetOf()

    private var retries = 0
    private val retryDelaySecs = listOf(1, 1, 1, 5, 5, 5, 10, 10, 10, 60)
    private val timeoutMillis = 1000

    private val lock = Any()

    fun connect(newServerIp: String?): Boolean {

        if (newServerIp == serverIp && connected) {
            Log.d(TAG, "TcpClient: already connected to $newServerIp")
            return true
        }
        Log.d(TAG, "TcpClient: trying to connect to $newServerIp")
        synchronized (lock) {
            serverIp = newServerIp
            reset()
            if (serverIp == null) return false
            serverAddress = InetAddress.getByName(serverIp)

            try {
                socket = Socket()
                socket!!.connect(InetSocketAddress(serverIp, tcpPort), timeoutMillis)
                reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                writer = OutputStreamWriter(socket?.getOutputStream())
                if (updateFromJson(reader?.readLine(), true)) {
                    setConnected(true)
                    retries = 0
                    startHeartbeat()
                    return true
                } else {}
            } catch (e: Exception) {
                Log.i(TAG, "TcpClient: could not connect: $e")
            }
        }
        setConnected(false)
        scheduleRetry()
        return false
    }

    fun sendSensordata(sensorDate: SensorData) {
        if (serverAddress == null) return
        val b = sensorDate.toByteArray()
        val packet = DatagramPacket(b, b.size, serverAddress, udpPort)
        networkScope.launch {
            try {
                udpSocket.send(packet)
                udpSent++
            } catch (e: Exception) {
                udpSendErrors++
            }
        }
    }

    fun getStatus(): String {
        return "ip=$serverIp connected=$connected pings=$pings address=$address inControl=$inControl retries=$retries udp=$udpSent/$udpSendErrors"
    }

    fun registerListener(listener: TcpClientListener) { listeners.add(listener) }
    fun unregisterListener(listener: TcpClientListener) { listeners.remove(listener) }

    private fun updateFromJson(json: String?, setAddress: Boolean = false): Boolean {
        if (json == null) return false
        try {
            data = JSONObject(json)
            val connected = data.getJSONArray("connected")
            val controlling = data.getString("controlling")
            if (setAddress) {
                address = connected.getString(connected.length() - 1)
            }
            setInControl(address == controlling)
            pings++
        } catch (e: JSONException) {
            Log.e(TAG, "TcpClient: Cannot parse JSON (${json.length} bytes): $e")
            return false
        }
        return true
    }

    private fun startHeartbeat(intervalMs: Long = 1 * 1000L) {
        heartbeatTask?.cancel()
        heartbeatTask = object : TimerTask() {
            override fun run() {
                try {
                    writer?.write( "{}\n")
                    updateFromJson(reader?.readLine())
                    Log.d(TAG, "TcpClient: heartbeat")
                } catch (e: IOException) {
                    Log.w(TAG, "TcpClient: IO Exception in heartbeat: $e")
                    disconnect()
                }
            }
        }
        timer.scheduleAtFixedRate(heartbeatTask, 0L, intervalMs)
    }

    private fun getRetrySecs(): Int {
        return retryDelaySecs[retries.coerceAtMost(retryDelaySecs.size - 1)]
    }

    private fun scheduleRetry() {
        reset()
        retryTask = object: TimerTask() {
            override fun run() {
                Log.i(TAG, "TcpClient: Retry $retries")
                connect(serverIp)
            }
        }
        val retrySecs = getRetrySecs()
        retries++
        Log.i(TAG, "TcpClient: Scheduling retry $retries after $retrySecs")
        timer.schedule(retryTask, 1000L * retrySecs)
    }

    fun disconnect() {
        reset()
        serverIp = null
    }

    private fun reset() {
        synchronized (lock) {
            heartbeatTask?.cancel()
            heartbeatTask = null
            retryTask?.cancel()
            retryTask = null

            socket?.close()
            socket = null
            reader?.close()
            reader = null
            writer?.close()
            writer = null

            setConnected(false)
            setAddress(null)
            pings = 0
            serverAddress = null
            udpSendErrors = 0
            udpSent = 0
        }
    }

    private fun setAddress(newAddress: String?) {
        synchronized (lock) {
            if (newAddress == address) return
            address = newAddress
        }
        for (listener in listeners) {
            listener.addressChanged(address)
        }
    }

    private fun setInControl(newInControl: Boolean) {
        synchronized (lock) {
            if (newInControl == inControl) return
            inControl = newInControl
        }
        for (listener in listeners) {
            listener.inControlChanged(inControl)
        }
    }

    private fun setConnected(newConnected: Boolean) {
        synchronized (lock) {
            if (newConnected == connected) return
            connected = newConnected
        }
        for (listener in listeners) {
            listener.inControlChanged(inControl)
        }
    }
}
