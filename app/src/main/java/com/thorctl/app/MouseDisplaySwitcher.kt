package com.thorctl.app

import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.util.Log
import android.view.Display
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object MouseDisplaySwitcher {
    private const val TAG = "MouseDisplaySwitcher"

    @Volatile
    private var running = false

    @Volatile
    var isOnBottom = false
        private set

    private var listenThread: Thread? = null
    private var listenProcess: Process? = null
    private var shellProcess: Process? = null
    private var shellWriter: OutputStreamWriter? = null
    private var lastTriggerMs = 0L

    private fun bottomDisplayId(): Int? {
        val dm = OverlayController.appContext.getSystemService(DisplayManager::class.java)
        val id = DaemonClient.state.value?.bottomDisplayId ?: -1
        if (id >= 0 && dm.getDisplay(id) != null) return id
        return dm.displays
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .minByOrNull { it.mode.physicalWidth * it.mode.physicalHeight }
            ?.displayId
    }

    fun start() {
        stop()
        running = true
        listenThread = Thread({
            try {
                val proc = ProcessBuilder("su", "-c", "getevent -l")
                    .redirectErrorStream(true)
                    .start()
                listenProcess = proc
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break
                        if (line.contains("BTN_MIDDLE") && line.contains("DOWN")) {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastTriggerMs > 250L) {
                                lastTriggerMs = now
                                toggle()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) Log.w(TAG, "Mausrad-Listener beendet", e)
            }
        }, "ThorMouseWheelListener").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try {
            listenProcess?.destroyForcibly()
        } catch (_: Exception) {
        }
        listenProcess = null
        listenThread = null

        try {
            shellWriter?.close()
            shellProcess?.destroyForcibly()
        } catch (_: Exception) {
        }
        shellWriter = null
        shellProcess = null
    }

    fun toggle() {
        moveTo(!isOnBottom)
    }

    @Synchronized
    fun moveTo(toBottom: Boolean) {
        val targetId = if (toBottom) {
            bottomDisplayId() ?: return
        } else {
            Display.DEFAULT_DISPLAY
        }
        isOnBottom = toBottom
        OverlayController.haptic(VibrationEffect.EFFECT_CLICK)

        val cmd = "input -d $targetId tap 1 1\n"
        try {
            val writer = ensureShell()
            writer.write(cmd)
            writer.flush()
        } catch (_: Exception) {
            shellWriter = null
            shellProcess = null
            try {
                val writer = ensureShell()
                writer.write(cmd)
                writer.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Mauswechsel auf Display $targetId fehlgeschlagen", e)
            }
        }
    }

    private fun ensureShell(): OutputStreamWriter {
        val existing = shellWriter
        if (existing != null && shellProcess?.isAlive == true) return existing
        val proc = ProcessBuilder("su")
            .redirectErrorStream(true)
            .start()
        val writer = OutputStreamWriter(proc.outputStream)
        shellProcess = proc
        shellWriter = writer
        return writer
    }
}
