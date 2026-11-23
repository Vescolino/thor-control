package com.thorctl.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.io.File

class WallpaperPickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQ_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    val outFile = File(filesDir, ThorWallpaperService.WALLPAPER_FILE)
                    contentResolver.openInputStream(uri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    getSharedPreferences(ThorWallpaperService.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(ThorWallpaperService.KEY_UPDATED, System.currentTimeMillis())
                        .apply()
                    Toast.makeText(this, "Thor Wallpaper aktualisiert", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Bild konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
                }
            }
        }
        finish()
    }

    companion object {
        private const val REQ_PICK_IMAGE = 1001
    }
}
