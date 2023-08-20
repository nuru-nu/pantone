package nu.nuru.hellogravity

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.HashMap

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