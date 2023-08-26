package nu.nuru.hellogravity

import android.hardware.Sensor
import android.hardware.SensorEvent

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
}

internal class ToColor {

    fun xyzToRgb(data: SensorData): Triple<Float, Float, Float> {
        val r = (data.gx + 10) / 20
        val g = (data.gy + 10) / 20
        val b = (data.gz + 10) / 20
        return Triple(r, g, b)
    }

    fun zToRb(data: SensorData): Triple<Float, Float, Float> {
        val r = (10 + data.gz) / 20
        val g = 0F
        val b = (10 - data.gz) / 20
        return Triple(r, g, b)
    }
}