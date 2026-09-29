package com.saiabhinavgandesree.shaketorch.service

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.saiabhinavgandesree.shaketorch.MainActivity
import com.saiabhinavgandesree.shaketorch.R
import com.saiabhinavgandesree.shaketorch.core.Prefs

/** Quick Settings tile that turns shake listening on/off (not the torch itself). */
class ShakeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render(ShakeService.isRunning.value)
    }

    override fun onClick() {
        super.onClick()
        val prefs = Prefs(this)
        val turnOn = !ShakeService.isRunning.value
        prefs.enabled = turnOn

        if (!turnOn) {
            ShakeService.stop(this)
            render(false)
            return
        }
        try {
            ShakeService.start(this)
            render(true)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Android refused a background start; opening the app starts the service from the foreground.
            openApp()
        }
    }

    // The Intent overload is only reached on Android 12–13, where it is still the supported API.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(if (active) R.string.tile_on else R.string.tile_off)
        tile.updateTile()
    }
}
