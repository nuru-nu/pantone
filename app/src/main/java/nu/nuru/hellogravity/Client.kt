package nu.nuru.hellogravity

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

const val PROTOCOL = "PANTONE1"

interface TcpClientListener {
    fun addressChanged(address: String?);
    fun inControlChanged(inControl: Boolean);
    fun connectedChanged(connected: Boolean);
}

class Client(
    private val udpPort: Int = 9001,
    private val udpBroadcastPort: Int = 9002,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

    private var udpSocket: DatagramSocket = DatagramSocket()
    private var udpSent = 0
    private var udpSendErrors = 0
    private val stats = NetworkStats()

    private var serverAddress: InetAddress? = null
    private var selfAddress: InetAddress? = null
    private var error: String? = null

    private var broadcastSocket: DatagramSocket? = null
    private var isListening = false

    private val timeoutSeconds = 20
    private var timeoutJob: Job? = null
    private val lastMessageTimestamp = AtomicLong(System.currentTimeMillis())
    private val isTimeout = AtomicBoolean(false)


    private val listeners: MutableSet<TcpClientListener> = mutableSetOf()

    private var retries = 0
    private val retryDelaySecs = listOf(1, 1, 1, 5, 5, 5, 10, 10, 10, 60)

    private val lock = Any()

    fun sendSensordata(sensorDate: SensorData) {
        if (serverAddress == null) return
        val b = sensorDate.toByteArray()
        val packet = DatagramPacket(b, b.size, serverAddress, udpPort)
        scope.launch {
            try {
                udpSocket.send(packet)
                stats.add(b.size)
                udpSent++
            } catch (e: Exception) {
                udpSendErrors++
            }
        }
    }

    fun startListening() {
        if (isListening) return

        isListening = true
        broadcastSocket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(udpBroadcastPort))
        }

        selfAddress = getLocalAddresses().firstOrNull()
        Log.i(TAG, "Addresses: ${getLocalAddresses().size}")

        startTimeoutMonitor()

        scope.launch {
            val buffer = ByteArray(32)
            val packet = DatagramPacket(buffer, buffer.size)

            Log.i(TAG, "Going to listen on $udpBroadcastPort...")

            while (isListening) {
                try {
                    broadcastSocket?.receive(packet)
                    lastMessageTimestamp.set(System.currentTimeMillis())
                    val message = String(
                        packet.data,
                        packet.offset,
                        packet.length,
                        Charsets.UTF_8
                    )
                    Log.i(TAG, "Received UDP broadcast: $message on $udpBroadcastPort")
                    if (message != PROTOCOL) {
                        error = "$message!=$PROTOCOL"
                        continue
                    }
                    error = null
                    serverAddress = packet.address

                    // Reset the packet length for the next receive
                    packet.length = buffer.size
                } catch (e: Exception) {
                    if (isListening) {
                        Log.e(TAG, "Error receiving UDP broadcast: ${e.message}")
                    }
                }
            }
        }
    }

    private fun startTimeoutMonitor() {
        timeoutJob = scope.launch {
            while (isListening) {
                val timeSinceLastMessage = System.currentTimeMillis() - lastMessageTimestamp.get()
                val newTimeoutState = timeSinceLastMessage >= 1000 * timeoutSeconds

                if (isTimeout.getAndSet(newTimeoutState) != newTimeoutState) {
                    error = "timeout"
                    serverAddress = null
                }

                delay(1000)
            }
        }
    }


    private fun getLocalAddresses(): List<InetAddress> {
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            .toList()
    }

    fun stopListening() {
        isListening = false
        broadcastSocket?.close()
        broadcastSocket = null
    }

    fun getStatus(): String {
        if (!isListening) return "(not listening)"
        return "server=${serverAddress?.hostAddress} self=${selfAddress?.hostAddress} udp=$udpSent/$udpSendErrors error=$error"
    }

    fun getStats(): NetworkStats {
        return stats
    }
}
