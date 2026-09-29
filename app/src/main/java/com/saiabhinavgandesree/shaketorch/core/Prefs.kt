package com.saiabhinavgandesree.shaketorch.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Prefs(context: Context) {

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var enabled by boolean(KEY_ENABLED, false)
    var thresholdG by float(KEY_THRESHOLD, 2.6f)
    var shakesRequired by int(KEY_SHAKES, 2)
    var cooldownMs by int(KEY_COOLDOWN, 1500)
    var screenOffEnabled by boolean(KEY_SCREEN_OFF, true)
    var vibrate by boolean(KEY_VIBRATE, true)
    var autoOffMinutes by int(KEY_AUTO_OFF, 0)

    /** Torch brightness level; 0 means "use the device default". */
    var strengthLevel by int(KEY_STRENGTH, 0)

    var shakeToggleCount by int(KEY_TOGGLE_COUNT, 0)
    var reviewRequested by boolean(KEY_REVIEW_REQUESTED, false)

    fun detectorConfig() = ShakeDetector.Config.fromSettings(thresholdG, shakesRequired, cooldownMs)

    private fun boolean(key: String, default: Boolean) = object : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getBoolean(key, default)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) =
            sp.edit { putBoolean(key, value) }
    }

    private fun int(key: String, default: Int) = object : ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getInt(key, default)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) =
            sp.edit { putInt(key, value) }
    }

    private fun float(key: String, default: Float) = object : ReadWriteProperty<Any, Float> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getFloat(key, default)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Float) =
            sp.edit { putFloat(key, value) }
    }

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_THRESHOLD = "threshold_g"
        const val KEY_SHAKES = "shakes_required"
        const val KEY_COOLDOWN = "cooldown_ms"
        const val KEY_SCREEN_OFF = "screen_off_enabled"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_AUTO_OFF = "auto_off_minutes"
        const val KEY_STRENGTH = "strength_level"
        const val KEY_TOGGLE_COUNT = "shake_toggle_count"
        const val KEY_REVIEW_REQUESTED = "review_requested"
    }
}
