package nu.nuru.hellogravity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val SAMPLING_US = 50 * 1000;

class SensorService: Service(), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var model : ApplicationModel
    private val client = Client()

    private lateinit var valuesLogger: ValuesLogger
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private val runnable = object : Runnable {
        override fun run() {
            valuesLogger.write()
            handler.postDelayed(this, 10000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SensorService: onCreate")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SAMPLING_US)
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SAMPLING_US)
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SAMPLING_US)  // main trigger

        val notification = createNotification()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)

        model = (application as MyApplication).sharedViewModel

        valuesLogger = ValuesLogger(
            getExternalFilesDir(null) ?: this.filesDir,
            listOf("t") + SensorData.getColumns()
        )
        handler.postDelayed(runnable, 1000)

        prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefs.registerOnSharedPreferenceChangeListener(this)
        if (prefs.getBoolean(getString(R.string.preferences_log_csv), false)) {
            valuesLogger.start()
        }

        client.startListening()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (prefs == null) return
        if (key == getString(R.string.preferences_log_csv)) {
            if (prefs.getBoolean(key, false)) valuesLogger.start() else valuesLogger.stop()
        }
    }

    override fun onDestroy() {
        valuesLogger.write()
        client.stopListening()
        Log.i(TAG, "SensorService: onDestroy")
        // Unregister sensor listener here
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var i = 0
    private val sensorData = SensorData()
    private val t0 = System.currentTimeMillis()

    override fun onSensorChanged(e: SensorEvent?) {

        if (e == null) return
        sensorData.update(e)
        if (e.sensor.type != Sensor.TYPE_GRAVITY) return

        val t = (System.currentTimeMillis() - t0) / 1e3f
        valuesLogger.log(floatArrayOf(t) + sensorData.getValues())

        client.sendSensordata(sensorData)

        i++
        if (i % 10 == 0) {
            model.liveData.postValue(ServiceState(
                sensorData,
                client.getStats(),
                client.getStatus(),
            ))
//            debouncedLogger.log("SensorService: posted live data")
        }
    }

    val debouncedLogger = DebouncedLogger(5F)

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.i(TAG, "SensorService: onAccuracyChanged type=${sensor?.type}")
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        // Create the notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sensor Service")
            .setContentText("Collecting sensor data")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(BitmapFactory.decodeResource(resources, android.R.drawable.ic_dialog_info))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // If the Android version is Oreo (API level 26) or above, create a notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        return notificationBuilder.build()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 123
        private const val CHANNEL_ID = "HELLO_GRAVITY_SENSOR_SERVICE"
    }
}