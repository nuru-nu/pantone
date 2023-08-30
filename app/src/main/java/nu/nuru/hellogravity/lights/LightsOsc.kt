package nu.nuru.hellogravity.lights

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nu.nuru.hellogravity.TAG
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LightsOsc(
    val oscAddress: String = "/dmx/universe/0",
    val port: Int = 7770,
): LightsInterface {
    var udpSendErrors = 0
    var udpSocket: DatagramSocket = DatagramSocket()
    var serverAddress: InetAddress? = null

    override fun getI(): Int {
        return udpSendErrors
    }

    override fun setColor(color: LightColor): Int {
        val values = UByteArray(512) { 0U }
        val toValue = { x: Float -> (x * 255).toInt().coerceIn(0, 255).toUByte() }
        for (device in 0..10) {
            values[device * 8 + 0] = 255U
            values[device * 8 + 1] = toValue(color.r)
            values[device * 8 + 2] = toValue(color.g)
            values[device * 8 + 3] = toValue(color.b)
        }
        runBlocking {
            sendMessage(oscAddress, values.toByteArray())
        }
        return values.size
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

    private suspend fun sendMessage(address: String, bs: ByteArray) {
        if (serverAddress == null) return
        withContext(Dispatchers.IO) {
            val b = writeString(address) + writeString(",b") + writeBlob(bs)
            val packet = DatagramPacket(b, b.size, serverAddress, port)
            try {
                udpSocket.send(packet)
            } catch (e: Exception) {
                udpSendErrors++
            }
        }
    }

    override fun setServerIp(ip: String?) {
        if (ip == null) {
            serverAddress = null
        } else {
            serverAddress = InetAddress.getByName(ip)
        }
        Log.i(TAG, "LightsOsc: updated serverIp=$ip")
    }
}