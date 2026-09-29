package com.abhinav.shaketorch.core

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TorchController(context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    val cameraId: String? = findFlashCamera()

    /** Highest brightness level the flash supports; 1 means brightness can't be changed. */
    val maxStrength: Int = cameraId?.let(::readMaxStrength) ?: 1

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    var onChange: ((Boolean) -> Unit)? = null

    // The system tells us about every torch change, including ones from the Quick Settings
    // flashlight or other apps, so our state never drifts out of sync.
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) update(enabled)
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) update(false)
        }
    }

    fun start() {
        cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        cameraManager.unregisterTorchCallback(callback)
    }

    /** Returns the new torch state, or null if the torch couldn't be changed (e.g. camera in use). */
    fun toggle(strength: Int): Boolean? {
        val target = !_isOn.value
        return if (setOn(target, strength)) target else null
    }

    fun setOn(on: Boolean, strength: Int): Boolean {
        val id = cameraId ?: return false
        return try {
            if (on && strength in 1..maxStrength && maxStrength > 1 &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                cameraManager.turnOnTorchWithStrengthLevel(id, strength)
            } else {
                cameraManager.setTorchMode(id, on)
            }
            true
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Torch unavailable", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Torch rejected request", e)
            false
        }
    }

    private fun update(enabled: Boolean) {
        _isOn.value = enabled
        onChange?.invoke(enabled)
    }

    private fun findFlashCamera(): String? {
        val withFlash = cameraManager.cameraIdList.filter {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        return withFlash.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }

    private fun readMaxStrength(id: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return cameraManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    }

    private companion object {
        const val TAG = "TorchController"
    }
}
