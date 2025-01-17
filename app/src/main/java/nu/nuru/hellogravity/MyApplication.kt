package nu.nuru.hellogravity

import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.Instant

class MyApplication: Application() {

    val sharedViewModel: ApplicationModel by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(this).create(ApplicationModel::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application.onCreate()")
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application.onTerminate()")
    }
}

class ApplicationModel : ViewModel() {
    val name = formatInstant(Instant.now())
    val liveData: MutableLiveData<ServiceState> = MutableLiveData()
}

data class ServiceState(
    val sensorData: SensorData = SensorData(),
    val stats: NetworkStats = NetworkStats(),
    val connectionStatus: String = "?",
    )
