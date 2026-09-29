package com.saiabhinavgandesree.shaketorch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.saiabhinavgandesree.shaketorch.core.Prefs

/** Restarts listening after a reboot or an app update, if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!Prefs(context).enabled) return

        try {
            ShakeService.start(context)
        } catch (e: IllegalStateException) {
            Log.w("BootReceiver", "Could not start shake service", e)
        }
    }
}
