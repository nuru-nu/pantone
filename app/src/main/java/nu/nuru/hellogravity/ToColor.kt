package nu.nuru.hellogravity

internal class ToColor {

    fun xyzToRgb(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val r = (x + 10) / 20
        val g = (y + 10) / 20
        val b = (z + 10) / 20
        return Triple(r, g, b)
    }

    fun zToRb(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val r = (10 + z) / 20
        val g = 0F
        val b = (10 - z) / 20
        return Triple(r, g, b)
    }
}