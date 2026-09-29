package com.saiabhinavgandesree.shaketorch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    private val config = ShakeDetector.Config(
        thresholdG = 2.5f,
        shakesRequired = 2,
        windowMs = 1000,
        cooldownMs = 1500,
        minPeakGapMs = 150,
    )

    /** Feeds [g] (in multiples of gravity) at 20 ms intervals (≈50 Hz, like SENSOR_DELAY_GAME). */
    private fun ShakeDetector.feed(fromMs: Long, toMs: Long, g: Float): List<Long> {
        val triggers = mutableListOf<Long>()
        var t = fromMs
        while (t < toMs) {
            if (onSample(0f, 0f, g * ShakeDetector.STANDARD_GRAVITY, t)) triggers += t
            t += 20
        }
        return triggers
    }

    @Test
    fun phoneAtRestNeverTriggers() {
        val d = ShakeDetector(config)
        assertTrue(d.feed(0, 10_000, 1f).isEmpty())
    }

    @Test
    fun twoQuickShakesTrigger() {
        val d = ShakeDetector(config)
        d.feed(0, 60, 3f)
        d.feed(60, 300, 1f)
        val triggers = d.feed(300, 360, 3f)
        assertEquals(listOf(300L), triggers)
    }

    @Test
    fun oneLongJoltCountsAsSingleShake() {
        val d = ShakeDetector(config)
        assertTrue(d.feed(0, 800, 3.5f).isEmpty())
    }

    @Test
    fun shakesTooFarApartDoNotTrigger() {
        val d = ShakeDetector(config)
        d.feed(0, 60, 3f)
        d.feed(60, 1500, 1f)
        assertTrue(d.feed(1500, 1560, 3f).isEmpty())
    }

    @Test
    fun cooldownSuppressesImmediateRetrigger() {
        val d = ShakeDetector(config)
        d.feed(0, 60, 3f); d.feed(60, 300, 1f)
        assertFalse(d.feed(300, 360, 3f).isEmpty())

        d.feed(360, 600, 1f); d.feed(600, 660, 3f); d.feed(660, 900, 1f)
        assertTrue("within cooldown", d.feed(900, 960, 3f).isEmpty())

        d.feed(960, 2000, 1f); d.feed(2000, 2060, 3f); d.feed(2060, 2300, 1f)
        assertFalse("after cooldown", d.feed(2300, 2360, 3f).isEmpty())
    }

    @Test
    fun weakShakesBelowThresholdIgnored() {
        val d = ShakeDetector(config)
        repeat(10) { i ->
            val t = i * 200L
            d.feed(t, t + 60, 2.2f)
            d.feed(t + 60, t + 200, 1f)
        }
        assertTrue(d.feed(2000, 2060, 2.2f).isEmpty())
    }

    @Test
    fun singleShakeModeTriggersOnFirstPeak() {
        val d = ShakeDetector(config.copy(shakesRequired = 1))
        assertEquals(listOf(0L), d.feed(0, 60, 3f))
    }
}
