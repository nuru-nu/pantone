package nu.nuru.hellogravity

import android.hardware.Sensor
import android.hardware.SensorEvent
import java.nio.ByteBuffer

data class SensorData(
    // TYPE_GRAVITY
    var gx: Float = 0F, var gy: Float = 0F, var gz: Float = 0F,
    // TYPE_ACCELEROMETER
    var ax: Float = 0F, var ay: Float = 0F, var az: Float = 0F,
    // TYPE_GYROSCOPE
    var rx: Float = 0F, var ry: Float = 0F, var rz: Float = 0F,
) {
    companion object {
        fun getColumns(): List<String> {
            return listOf(
                "gx", "gy", "gz",
                "ax", "ay", "az",
                "rx", "ry", "rz",
                )
        }
    }

    fun getValues(): FloatArray {
        return floatArrayOf(
            gx, gy, gz,
            ax, ay, az,
            rx, ry, rz,
        )
    }

    fun serializeMultiline(): String {
        return """
            gx=%+.3f gy=%+.3f gz=%+.3f
            ax=%+.3f ay=%+.3f az=%+.3f
            rx=%+.3f ry=%+.3f rz=%+.3f
        """.trimIndent().format(
            gx, gy, gz,
            ax, ay, az,
            rx, ry, rz,
        )
    }

    fun update(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_GRAVITY) {
            gx = e.values[0]
            gy = e.values[1]
            gz = e.values[2]
        }
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            ax = e.values[0]
            ay = e.values[1]
            az = e.values[2]
        }
        if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
            rx = e.values[0]
            ry = e.values[1]
            rz = e.values[2]
        }
    }

    fun toByteArray(): ByteArray {
        // Each float is 4 bytes, and we have 9 float values
        return ByteBuffer.allocate(9 * 4).apply {
            // Write all values in order
            putFloat(gx)
            putFloat(gy)
            putFloat(gz)
            putFloat(ax)
            putFloat(ay)
            putFloat(az)
            putFloat(rx)
            putFloat(ry)
            putFloat(rz)
        }.array()
    }
}
