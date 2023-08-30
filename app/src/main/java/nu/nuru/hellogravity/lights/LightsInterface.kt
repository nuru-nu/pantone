package nu.nuru.hellogravity.lights

data class LightColor(val r: Float, val g: Float, val b: Float) {
    init {
        require(r in 0.0f..1.0f) { "r value out of range" }
        require(g in 0.0f..1.0f) { "g value out of range" }
        require(b in 0.0f..1.0f) { "b value out of range" }
    }
}

interface LightsInterface {
    fun init() {}
    fun setServerIp(ip: String?) {}
    fun setColor(color: LightColor): Int
    // Returns true if permission request was handled (i.e. if
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        return false
    }
    // Some useful display variable for debugging.
    fun getI(): Int { return 0 }
}
