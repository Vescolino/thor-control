package com.thorctl.app

import android.content.res.Configuration
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Surface
import android.view.WindowManager

class LandscapeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val locked = isRotationLocked()
        try {
            if (locked) {
                // Auto-Rotate wieder einschalten
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            } else {
                // Auto-Rotate aus & auf Querformat sperren
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                val currentRot = getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation
                val targetRot = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && currentRot != null) {
                    currentRot
                } else {
                    Surface.ROTATION_90
                }
                Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, targetRot)
            }
        } catch (_: Exception) {
        }
        updateTile()
    }

    private fun isRotationLocked(): Boolean =
        Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    private fun updateTile() {
        val tile = qsTile ?: return
        val locked = isRotationLocked()
        tile.state = if (locked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Querformat"
        tile.subtitle = if (locked) "Gesperrt" else "Auto"
        tile.updateTile()
    }
}
