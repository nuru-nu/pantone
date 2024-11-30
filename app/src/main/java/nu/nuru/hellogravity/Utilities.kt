package nu.nuru.hellogravity

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun clip01(v: Float) = v.coerceAtLeast(0.0F).coerceAtMost(1.0F)

class DebouncedLogger(val debounceSecs: Float = 10.0f) {

    private val timers = HashMap<String, Long>()

    fun log(message: String) {
        val currentTime = System.currentTimeMillis()
        val lastLogTime = timers[message] ?: 0
        if (currentTime - lastLogTime >= debounceSecs * 1000) {
            timers[message] = currentTime
            Log.v(TAG, message)
        }
    }
}

class HysteresisLogger(val value1: Float = 0.8f, val value2: Float = 0.9f) {

    private val timers: MutableMap<String, Boolean> = mutableMapOf()
    private val mutex = Mutex()
    suspend fun log(device: String, x: Float) {
        mutex.withLock {
            if (!timers.contains(device)) {
                timers[device] = false
            }
            if (timers[device] == true && x < value1) {
                timers[device] = false
            }
            if (timers[device] == true && x > value2) {
                Log.v(TAG, "logHyst: $device OFF->ON")
            }
        }
    }
}

fun formatInstant(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)
    return formatter.format(instant)
}

class NetworkStats(val interruptionSecs: Float = 1F) {
    private var totalBytes = 0L
    private var totalMillis = 0L
    private var lt = System.currentTimeMillis()

    fun add(bytes: Int) { add(bytes.toLong()) }

    fun add(bytes: Long) {
        val t = System.currentTimeMillis()
        val dt = t - lt
        lt = t
        if (dt > 1000 * interruptionSecs) return
        totalBytes += bytes
        totalMillis += dt
    }

    override fun toString(): String {
        val secs = totalMillis / 1000
        return "%.1f KB/s (%.1f MB in %02d:%02d)".format(
            totalBytes.toFloat() / 1L.coerceAtLeast(secs) / 1000,
            totalBytes.toFloat() / 1e6,
            secs / 60,
            secs % 60,
        )
    }
}

class ValuesLogger(val directory: File, val columns: List<String>) {
    private var started: Instant? = null
    private val rows: MutableList<FloatArray> = arrayListOf()
    private var written = 0
    private val lock = Any()

    fun log(values: FloatArray) {
        if (started == null) return
        rows.add(values)
    }

    fun start() {
        synchronized(lock) {
            if (started != null) return
            rows.clear()
            started = Instant.now()
        }
        Log.i(TAG, "started logging ${columns.joinToString(",") } to $directory")
    }

    fun stop() {
        synchronized(lock) {
            if (started == null) return
            write()
            started = null
        }
        Log.i(TAG, "stopped logging")
    }

    fun write() {
        synchronized(lock) {
            if (started == null) return
            val filename = "values_${formatInstant(started!!)}.csv"
            val file = File(directory, filename)
            val content = StringBuilder()
            var lines = 0
            if (written == 0) {
                content.appendLine(columns.joinToString(","))
                lines++
            }
            for (row in rows) {
                content.appendLine(row.joinToString(",") { it.toString() })
                lines++
            }
            try {
                file.appendText(content.toString())
                Log.i(TAG, "wrote $lines lines to $filename")
                rows.clear()
                written += lines
            } catch (e: Exception) {
                Log.e(TAG, "could not write values: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
