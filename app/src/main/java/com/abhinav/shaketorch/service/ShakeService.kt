package com.abhinav.shaketorch.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abhinav.shaketorch.MainActivity
import com.abhinav.shaketorch.R
import com.abhinav.shaketorch.core.Prefs
import com.abhinav.shaketorch.core.ShakeDetector
import com.abhinav.shaketorch.core.TorchController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShakeService : Service(), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var torch: TorchController
    private lateinit var detector: ShakeDetector

    private var accelerometer: Sensor? = null
    private var sensorRegistered = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private val autoOff = Runnable { torch.setOn(false, 0) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> applyScreenState(interactive = true)
                Intent.ACTION_SCREEN_OFF -> applyScreenState(interactive = false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        detector = ShakeDetector(prefs.detectorConfig())
        torch = TorchController(this)

        createNotificationChannel()
        startInForeground()

        torch.onChange = ::onTorchChanged
        torch.start()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)

        applyScreenState(powerManager.isInteractive)
        running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_TORCH -> torch.toggle(prefs.strengthLevel)
            ACTION_STOP -> {
                prefs.enabled = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.value = false
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        unregisterReceiver(screenReceiver)
        unregisterSensor()
        releaseWakeLock()
        handler.removeCallbacksAndMessages(null)
        torch.onChange = null
        torch.stop()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        if (detector.onSample(x, y, z, event.timestamp / 1_000_000)) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.KEY_THRESHOLD, Prefs.KEY_SHAKES, Prefs.KEY_COOLDOWN ->
                detector.config = prefs.detectorConfig()
            Prefs.KEY_SCREEN_OFF -> applyScreenState(powerManager.isInteractive)
            Prefs.KEY_AUTO_OFF -> scheduleAutoOff(torch.isOn.value)
        }
    }

    private fun onShake() {
        val newState = torch.toggle(prefs.strengthLevel) ?: return
        if (prefs.vibrate) vibrate(newState)
    }

    private fun onTorchChanged(on: Boolean) {
        scheduleAutoOff(on)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun scheduleAutoOff(torchOn: Boolean) {
        handler.removeCallbacks(autoOff)
        val minutes = prefs.autoOffMinutes
        if (torchOn && minutes > 0) handler.postDelayed(autoOff, minutes * 60_000L)
    }

    /**
     * Screen on: the CPU is awake anyway, so just listen.
     * Screen off: Android suspends the CPU within seconds, which would silence the sensor.
     * A partial wake lock keeps the CPU (not the screen) running so shakes still register.
     */
    private fun applyScreenState(interactive: Boolean) {
        detector.reset()
        when {
            interactive -> {
                releaseWakeLock()
                registerSensor()
            }
            prefs.screenOffEnabled -> {
                acquireWakeLock()
                registerSensor()
            }
            else -> {
                unregisterSensor()
                releaseWakeLock()
            }
        }
    }

    private fun registerSensor() {
        val sensor = accelerometer ?: return
        if (sensorRegistered) return
        sensorRegistered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensorManager.unregisterListener(this)
        sensorRegistered = false
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShakeTorch:listener")
            .apply { setReferenceCounted(false); acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun vibrate(turnedOn: Boolean) {
        val vibrator = (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        val effect = if (turnedOn) {
            VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1)
        }
        vibrator.vibrate(effect)
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val torchOn = torch.isOn.value
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val toggle = PendingIntent.getService(this, 1, serviceIntent(ACTION_TOGGLE_TORCH), flags)
        val stop = PendingIntent.getService(this, 2, serviceIntent(ACTION_STOP), flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_torch)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(if (torchOn) R.string.notif_text_on else R.string.notif_text_off))
            .setContentIntent(openApp)
            .addAction(0, getString(if (torchOn) R.string.action_turn_off else R.string.action_turn_on), toggle)
            .addAction(0, getString(R.string.action_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun serviceIntent(action: String) = Intent(this, ShakeService::class.java).setAction(action)

    companion object {
        const val ACTION_TOGGLE_TORCH = "com.abhinav.shaketorch.action.TOGGLE_TORCH"
        const val ACTION_STOP = "com.abhinav.shaketorch.action.STOP"
        private const val CHANNEL_ID = "shake_listener"
        private const val NOTIFICATION_ID = 1

        private val running = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = running.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ShakeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShakeService::class.java))
        }
    }
}
