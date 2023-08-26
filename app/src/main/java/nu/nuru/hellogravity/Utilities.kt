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
    val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)
    return formatter.format(instant)
}


class ValuesLogger(val directory: File, val columns: List<String>) {
    private val now = Instant.now()
    val rows: MutableList<FloatArray> = arrayListOf()
    var written = 0

    init {
        Log.v(TAG, "directory=${directory}")
    }

    fun log(values: FloatArray) {
        rows.add(values)
    }

    fun write() {
        val filename = "values_${formatInstant(now)}.csv"
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
            Log.i(TAG, "wrote $lines lines to $filename / ${file.path}")
            rows.clear()
            written += lines
        } catch (e: Exception) {
            Log.e(TAG, "could not write values: ${e.message}")
            e.printStackTrace()
        }
    }
}
