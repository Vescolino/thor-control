package com.thorctl.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File
import kotlin.math.max

class ThorWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ThorEngine()

    inner class ThorEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        private var bitmap: Bitmap? = null
        private var loadedVersion = -1L

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key == KEY_UPDATED) draw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) draw()
        }

        private fun ensureBitmap() {
            val ver = prefs.getLong(KEY_UPDATED, 0L)
            if (ver == loadedVersion && bitmap != null) return
            loadedVersion = ver
            bitmap?.recycle()
            bitmap = null

            val file = File(filesDir, WALLPAPER_FILE)
            if (!file.exists()) return

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val maxDim = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxDim / sample > 2560) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
        }

        private fun draw() {
            val holder = surfaceHolder
            val frame = holder.surfaceFrame
            if (frame.width() <= 0 || frame.height() <= 0) return

            ensureBitmap()
            val canvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            } ?: return

            try {
                canvas.drawColor(Color.BLACK)
                val bmp = bitmap ?: return
                val w = frame.width().toFloat()
                val h = frame.height().toFloat()
                val scale = max(w / bmp.width, h / bmp.height)
                val dx = (w - bmp.width * scale) / 2f
                val dy = (h - bmp.height * scale) / 2f
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(dx, dy)
                }
                canvas.drawBitmap(bmp, matrix, paint)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        const val PREFS_NAME = "thor_wallpaper"
        const val KEY_UPDATED = "updated_at"
        const val WALLPAPER_FILE = "thor_wallpaper.img"
    }
}
