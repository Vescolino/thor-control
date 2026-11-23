package com.thorctl.app

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.VibrationEffect
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

object BottomGestures {
    private const val TAG = "BottomGestures"

    private var windowManager: WindowManager? = null
    private val overlayViews = mutableListOf<View>()

    fun start(service: AccessibilityService) {
        stop()
        try {
            val dm = service.getSystemService(DisplayManager::class.java)
            val targetId = DaemonClient.state.value?.bottomDisplayId ?: -1
            val bottomDisplay = (if (targetId >= 0) dm.getDisplay(targetId) else null)
                ?: dm.displays
                    .filter { it.displayId != Display.DEFAULT_DISPLAY }
                    .minByOrNull { it.mode.physicalWidth * it.mode.physicalHeight }
                ?: return

            val displayContext = service.createDisplayContext(bottomDisplay)
            val wm = displayContext.getSystemService(WindowManager::class.java) ?: return
            windowManager = wm

            val density = displayContext.resources.displayMetrics.density
            val edgePx = (14f * density).toInt().coerceAtLeast(12)
            val swipeThreshold = 28f * density

            // 1. Oberer Rand: Nach unten wischen -> Quick Toggles
            addEdgeStrip(
                wm = wm,
                view = View(displayContext),
                width = WindowManager.LayoutParams.MATCH_PARENT,
                height = edgePx,
                gravity = Gravity.TOP or Gravity.START
            ) { dx, dy ->
                if (dy > swipeThreshold && abs(dy) > abs(dx)) {
                    OverlayController.haptic(VibrationEffect.EFFECT_CLICK)
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    true
                } else {
                    false
                }
            }

            // 2. Linker Rand: Nach rechts wischen -> Zurück
            addEdgeStrip(
                wm = wm,
                view = View(displayContext),
                width = edgePx,
                height = WindowManager.LayoutParams.MATCH_PARENT,
                gravity = Gravity.TOP or Gravity.START
            ) { dx, dy ->
                if (dx > swipeThreshold && abs(dx) > abs(dy)) {
                    OverlayController.haptic(VibrationEffect.EFFECT_CLICK)
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    true
                } else {
                    false
                }
            }

            // 3. Rechter Rand: Nach links wischen -> Zurück
            addEdgeStrip(
                wm = wm,
                view = View(displayContext),
                width = edgePx,
                height = WindowManager.LayoutParams.MATCH_PARENT,
                gravity = Gravity.TOP or Gravity.END
            ) { dx, dy ->
                if (dx < -swipeThreshold && abs(dx) > abs(dy)) {
                    OverlayController.haptic(VibrationEffect.EFFECT_CLICK)
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "BottomGestures konnte nicht gestartet werden", e)
        }
    }

    fun stop() {
        val wm = windowManager
        if (wm != null) {
            for (v in overlayViews) {
                try {
                    wm.removeViewImmediate(v)
                } catch (_: Exception) {
                }
            }
        }
        overlayViews.clear()
        windowManager = null
    }

    private fun addEdgeStrip(
        wm: WindowManager,
        view: View,
        width: Int,
        height: Int,
        gravity: Int,
        onSwipe: (dx: Float, dy: Float) -> Boolean
    ) {
        var startX = 0f
        var startY = 0f
        var triggered = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    triggered = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!triggered) {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        if (onSwipe(dx, dy)) {
                            triggered = true
                        }
                    }
                    true
                }
                else -> false
            }
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }

        wm.addView(view, params)
        overlayViews.add(view)
    }
}
