package com.saiabhinavgandesree.shaketorch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.saiabhinavgandesree.shaketorch.core.Prefs
import com.saiabhinavgandesree.shaketorch.core.TorchController
import com.saiabhinavgandesree.shaketorch.service.ShakeService
import com.saiabhinavgandesree.shaketorch.ui.SettingsScreen
import com.saiabhinavgandesree.shaketorch.ui.theme.ShakeTorchTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var torch: TorchController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        torch = TorchController(this)
        setContent {
            ShakeTorchTheme {
                SettingsScreen(prefs = prefs, torch = torch)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        torch.start()
    }

    override fun onResume() {
        super.onResume()
        // Recover if the system (or Samsung's battery manager) killed the service.
        if (prefs.enabled && !ShakeService.isRunning.value) ShakeService.start(this)
    }

    override fun onStop() {
        torch.stop()
        super.onStop()
    }
}
