package com.abhinav.shaketorch.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhinav.shaketorch.R
import com.abhinav.shaketorch.core.Prefs
import com.abhinav.shaketorch.core.TorchController
import com.abhinav.shaketorch.service.ShakeService
import kotlin.math.roundToInt

private val AUTO_OFF_OPTIONS = listOf(0, 1, 2, 5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, torch: TorchController) {
    val context = LocalContext.current
    val serviceRunning by ShakeService.isRunning.collectAsStateWithLifecycle()
    val torchOn by torch.isOn.collectAsStateWithLifecycle()

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var threshold by remember { mutableFloatStateOf(prefs.thresholdG) }
    var shakes by remember { mutableIntStateOf(prefs.shakesRequired) }
    var cooldown by remember { mutableIntStateOf(prefs.cooldownMs) }
    var screenOff by remember { mutableStateOf(prefs.screenOffEnabled) }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
    var autoOff by remember { mutableIntStateOf(prefs.autoOffMinutes) }
    var strength by remember {
        mutableIntStateOf(prefs.strengthLevel.takeIf { it in 1..torch.maxStrength } ?: torch.maxStrength)
    }

    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    // Returning from system settings (or toggling the QS tile) can change these behind our back.
    LifecycleResumeEffect(Unit) {
        enabled = prefs.enabled
        notificationsGranted = hasNotificationPermission(context)
        batteryUnrestricted = isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs.enabled = on
        if (on) {
            if (!hasNotificationPermission(context)) requestNotificationPermission()
            ShakeService.start(context)
        } else {
            ShakeService.stop(context)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MasterCard(
                enabled = enabled,
                serviceRunning = serviceRunning,
                torchOn = torchOn,
                hasFlash = torch.cameraId != null,
                onEnabledChange = ::setEnabled,
                onTestTorch = { torch.toggle(prefs.strengthLevel) },
            )

            if (!notificationsGranted || !batteryUnrestricted) {
                SectionCard("Setup") {
                    if (!notificationsGranted) {
                        Text(
                            "Allow notifications so you can see when the app is listening and " +
                                "toggle the torch from the notification.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(onClick = ::requestNotificationPermission) {
                            Text("Allow notifications")
                        }
                    }
                    if (!batteryUnrestricted) {
                        Text(
                            "Many phones (especially Samsung) put background apps to sleep. On the next " +
                                "screen, open Battery and choose Unrestricted so shake detection keeps " +
                                "working with the screen off.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(onClick = { openAppDetails(context) }) {
                            Text("Allow background running")
                        }
                    }
                }
            }

            SectionCard("Calibrate") {
                Text(
                    "Shake your phone the way you'd like to trigger the torch. The bar shows how hard " +
                        "you shook; the red line is the current threshold.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LiveShakeMeter(thresholdG = threshold, shakesRequired = shakes, cooldownMs = cooldown)
                if (serviceRunning) {
                    Text(
                        "Tip: listening is on, so test shakes here will also toggle the torch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("Detection") {
                LabeledSlider(
                    label = "Shake strength needed",
                    valueText = "%.1f g".format(threshold),
                    hint = "Lower triggers with gentler shakes but may fire while walking or running.",
                    value = threshold,
                    range = 1.6f..5f,
                    steps = 33,
                    onValueChange = { threshold = (it * 10).roundToInt() / 10f },
                    onDone = { prefs.thresholdG = threshold },
                )
                LabeledSlider(
                    label = "Shakes required",
                    valueText = if (shakes == 1) "1 shake" else "$shakes shakes",
                    hint = "More shakes = fewer accidental triggers.",
                    value = shakes.toFloat(),
                    range = 1f..4f,
                    steps = 2,
                    onValueChange = { shakes = it.roundToInt() },
                    onDone = { prefs.shakesRequired = shakes },
                )
                LabeledSlider(
                    label = "Cooldown",
                    valueText = "%.2f s".format(cooldown / 1000f),
                    hint = "Ignore shakes for this long after a toggle, so one long shake doesn't flip it twice.",
                    value = cooldown.toFloat(),
                    range = 500f..5000f,
                    steps = 17,
                    onValueChange = { cooldown = (it / 250).roundToInt() * 250 },
                    onDone = { prefs.cooldownMs = cooldown },
                )
            }

            SectionCard("Behaviour") {
                SwitchRow(
                    title = "Work with screen off",
                    subtitle = "Keeps the CPU awake while the screen is off. Uses a little more battery.",
                    checked = screenOff,
                    onCheckedChange = { screenOff = it; prefs.screenOffEnabled = it },
                )
                SwitchRow(
                    title = "Vibrate on toggle",
                    subtitle = "One buzz for on, two for off. Handy in the dark or in a pocket.",
                    checked = vibrate,
                    onCheckedChange = { vibrate = it; prefs.vibrate = it },
                )
                val autoOffIndex = AUTO_OFF_OPTIONS.indexOf(autoOff).coerceAtLeast(0)
                LabeledSlider(
                    label = "Auto turn-off",
                    valueText = if (autoOff == 0) "Never" else "After $autoOff min",
                    hint = "Turns the torch off automatically so it can't drain your battery in a pocket.",
                    value = autoOffIndex.toFloat(),
                    range = 0f..(AUTO_OFF_OPTIONS.size - 1).toFloat(),
                    steps = AUTO_OFF_OPTIONS.size - 2,
                    onValueChange = { autoOff = AUTO_OFF_OPTIONS[it.roundToInt()] },
                    onDone = { prefs.autoOffMinutes = autoOff },
                )
                if (torch.maxStrength > 1) {
                    LabeledSlider(
                        label = "Torch brightness",
                        valueText = "$strength / ${torch.maxStrength}",
                        hint = "Your phone supports adjustable flashlight brightness.",
                        value = strength.toFloat(),
                        range = 1f..torch.maxStrength.toFloat(),
                        steps = (torch.maxStrength - 2).coerceAtLeast(0),
                        onValueChange = { strength = it.roundToInt() },
                        onDone = {
                            prefs.strengthLevel = strength
                            if (torchOn) torch.setOn(true, strength)
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MasterCard(
    enabled: Boolean,
    serviceRunning: Boolean,
    torchOn: Boolean,
    hasFlash: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestTorch: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Shake to toggle torch", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = when {
                            !enabled -> "Off"
                            serviceRunning -> "Listening for shakes"
                            else -> "Starting…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange, enabled = hasFlash)
            }
            if (hasFlash) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Torch is ${if (torchOn) "ON" else "off"}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onTestTorch) { Text(if (torchOn) "Turn off" else "Test torch") }
                }
            } else {
                Text("No flashlight found on this device.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            steps = steps,
        )
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
}
