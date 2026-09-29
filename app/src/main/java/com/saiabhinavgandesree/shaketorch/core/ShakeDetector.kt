package com.saiabhinavgandesree.shaketorch.core

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure shake-detection logic with no Android dependencies, so it can be unit tested on the JVM.
 * Feed it raw accelerometer samples; it returns true on the sample that completes a shake gesture.
 */
class ShakeDetector(config: Config = Config()) {

    data class Config(
        val thresholdG: Float = 2.6f,
        val shakesRequired: Int = 2,
        val windowMs: Long = 1200,
        val cooldownMs: Long = 1500,
        val minPeakGapMs: Long = 150,
    ) {
        companion object {
            fun fromSettings(thresholdG: Float, shakesRequired: Int, cooldownMs: Int) = Config(
                thresholdG = thresholdG,
                shakesRequired = shakesRequired,
                windowMs = 400L * shakesRequired + 400,
                cooldownMs = cooldownMs.toLong(),
            )
        }
    }

    var config: Config = config
        set(value) {
            field = value
            reset()
        }

    private val peaks = ArrayDeque<Long>()
    private var armed = true
    private var lastPeakMs = Long.MIN_VALUE / 2
    private var cooldownUntilMs = Long.MIN_VALUE / 2

    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        val g = gForce(x, y, z)

        // Hysteresis: after a peak, the force must drop well below the threshold before
        // another peak can count, so one long jolt isn't read as several shakes.
        if (!armed) {
            if (g < rearmThreshold()) armed = true
            return false
        }
        if (g < config.thresholdG) return false
        armed = false

        if (timeMs < cooldownUntilMs) return false
        if (timeMs - lastPeakMs < config.minPeakGapMs) return false

        lastPeakMs = timeMs
        peaks.addLast(timeMs)
        while (timeMs - peaks.first() > config.windowMs) peaks.removeFirst()

        if (peaks.size < config.shakesRequired) return false

        peaks.clear()
        cooldownUntilMs = timeMs + config.cooldownMs
        return true
    }

    fun reset() {
        peaks.clear()
        armed = true
    }

    private fun rearmThreshold() = max(1.3f, config.thresholdG * 0.75f)

    companion object {
        const val STANDARD_GRAVITY = 9.80665f

        fun gForce(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z) / STANDARD_GRAVITY
    }
}
