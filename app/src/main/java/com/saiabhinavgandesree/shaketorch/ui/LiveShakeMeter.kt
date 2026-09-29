package com.saiabhinavgandesree.shaketorch.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saiabhinavgandesree.shaketorch.core.ShakeDetector
import kotlinx.coroutines.delay

private const val METER_MAX_G = 6f

/**
 * Shows the live shake force against the current threshold and runs its own detector,
 * so the user can tune settings by feel without toggling the real torch.
 */
@Composable
fun LiveShakeMeter(thresholdG: Float, shakesRequired: Int, cooldownMs: Int) {
    val context = LocalContext.current
    var peakG by remember { mutableFloatStateOf(1f) }
    var detections by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    val detector = remember { ShakeDetector() }

    val config = ShakeDetector.Config.fromSettings(thresholdG, shakesRequired, cooldownMs)
    SideEffect { if (detector.config != config) detector.config = config }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    DisposableEffect(accelerometer) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val g = ShakeDetector.gForce(x, y, z)
                // Peak-hold that decays ~2.5 g/s, so brief spikes stay visible long enough to read.
                peakG = maxOf(g, peakG - 0.05f)
                if (detector.onSample(x, y, z, event.timestamp / 1_000_000)) detections++
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    LaunchedEffect(detections) {
        if (detections == 0) return@LaunchedEffect
        flash = true
        delay(1200)
        flash = false
    }

    if (accelerometer == null) {
        Text("No accelerometer found on this device.", color = MaterialTheme.colorScheme.error)
        return
    }

    val colors = MaterialTheme.colorScheme
    val fillColor = if (peakG >= thresholdG) colors.primary else colors.secondary.copy(alpha = 0.6f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(colors.surfaceVariant, cornerRadius = radius)
            val fraction = (peakG / METER_MAX_G).coerceIn(0f, 1f)
            drawRoundRect(fillColor, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            val x = size.width * (thresholdG / METER_MAX_G)
            drawLine(colors.error, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Force: %.1f g".format(peakG), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Threshold: %.1f g".format(thresholdG),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
            )
        }
        Text(
            text = if (flash) "Shake detected! ($detections)" else "Detections so far: $detections",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (flash) FontWeight.Bold else FontWeight.Normal,
            color = if (flash) colors.primary else colors.onSurfaceVariant,
        )
    }
}
