package com.thorctl.app

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.text.InputType
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.ComposeView
import com.thorctl.ui.KeyboardPanel
import com.thorctl.ui.ThorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@SuppressLint("StaticFieldLeak")
object KeyboardController {
    private const val TAG = "ThorKeyboard"
    private const val PREFS_NAME = "thor_keyboard_prefs"
    private const val KEY_CUSTOM_HEIGHT_DP = "custom_height_dp"
    private const val KEY_OFFSET_Y_DP = "offset_y_dp"
    private const val DEFAULT_HEIGHT_DP = 315f

    enum class Layer { LETTERS, SYMBOLS, MORE }
    enum class Enter { RETURN, GO, SEARCH, SEND, NEXT, DONE }

    @Immutable
    data class Ui(
        val layer: Layer = Layer.LETTERS,
        val shift: Boolean = false,
        val caps: Boolean = false,
        val selecting: Boolean = false,
        val enter: Enter = Enter.RETURN,
        val before: String = "",
        val selected: String = "",
        val after: String = "",
        val flash: String? = null,
        val flashId: Long = 0,
        val heightMode: Int = 1,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    private val main = Handler(Looper.getMainLooper())
    private var ime: ThorKeyboard? = null
    private var editor: EditorInfo? = null
    private var savedHeightMode = 1

    private var selStart = 0
    private var selEnd = 0
    private var anchor = 0
    private var moving = 0

    private var text: CharSequence? = null
    private var textOffset = 0

    private var lastShiftTap = 0L

    fun getSavedHeightDp(): Float {
        return try {
            OverlayController.appContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_CUSTOM_HEIGHT_DP, DEFAULT_HEIGHT_DP)
                .coerceIn(190f, 480f)
        } catch (_: Exception) {
            DEFAULT_HEIGHT_DP
        }
    }

    fun updateCustomHeightDp(dp: Float) {
        val clamped = dp.coerceIn(190f, 480f)
        try {
            OverlayController.appContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_CUSTOM_HEIGHT_DP, clamped)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun getSavedOffsetYDp(): Float {
        return try {
            OverlayController.appContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_OFFSET_Y_DP, 0f)
                .coerceIn(0f, 350f)
        } catch (_: Exception) {
            0f
        }
    }

    fun updateOffsetYDp(dp: Float) {
        val clamped = dp.coerceIn(0f, 350f)
        try {
            OverlayController.appContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_OFFSET_Y_DP, clamped)
                .apply()
        } catch (_: Exception) {
        }
        val d = dialog ?: return
        val w = d.window ?: return
        val density = d.context.resources.displayMetrics.density
        w.attributes = w.attributes.apply {
            y = (clamped * density).roundToInt()
        }
    }

    fun currentHeightPx(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (getSavedHeightDp() * density).roundToInt()
    }

    fun attach(s: ThorKeyboard) {
        ime = s
    }

    fun detach(s: ThorKeyboard) {
        if (ime === s) ime = null
        removeNow()
    }

    fun onStartInput(info: EditorInfo) {
        editor = info
        selStart = info.initialSelStart.coerceAtLeast(0)
        selEnd = info.initialSelEnd.coerceAtLeast(0)
    }

        fun onShow(info: EditorInfo, inline: Boolean) {
        editor = info
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val layer = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) Layer.SYMBOLS else Layer.LETTERS
        _ui.value = Ui(layer = layer, enter = enterFor(info), heightMode = savedHeightMode)
        autoShift()
        refreshPreview()
        main.removeCallbacks(hideRunnable)
        inlineShown = inline
        if (inline) {
            _visible.value = false
            dialog?.hide()
        } else {
            show()
        }
        DaemonClient.keyboardVisible(true)
    }
        
    fun onHide() {
        inlineShown = false
        _visible.value = false
        DaemonClient.keyboardVisible(false)
        _ui.value = _ui.value.copy(selecting = false)
        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, 240)
    }


    fun onSelection(start: Int, end: Int) {
        selStart = start
        selEnd = end
        refreshPreview()
    }

    private fun enterFor(info: EditorInfo): Enter {
        val multiLine = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 || multiLine) return Enter.RETURN
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> Enter.GO
            EditorInfo.IME_ACTION_SEARCH -> Enter.SEARCH
            EditorInfo.IME_ACTION_SEND -> Enter.SEND
            EditorInfo.IME_ACTION_NEXT -> Enter.NEXT
            EditorInfo.IME_ACTION_DONE -> Enter.DONE
            else -> Enter.RETURN
        }
    }

    private val ic: InputConnection? get() = ime?.currentInputConnection

    private fun isPassword(): Boolean {
        val t = editor?.inputType ?: return false
        val v = t and InputType.TYPE_MASK_VARIATION
        return (t and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) ||
            (t and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    fun type(s: String) {
        val k = ime ?: return
        val u = _ui.value
        val out = if (u.layer == Layer.LETTERS && (u.shift || u.caps)) s.uppercase() else s
        if (out.length == 1) k.sendKeyChar(out[0]) else ic?.commitText(out, 1)
        if (u.shift && !u.caps) _ui.value = _ui.value.copy(shift = false)
        endSelecting()
        autoShift()
    }

    fun typeExact(s: String) {
        val k = ime ?: return
        if (s.length == 1) k.sendKeyChar(s[0]) else ic?.commitText(s, 1)
        val u = _ui.value
        if (u.shift && !u.caps) _ui.value = u.copy(shift = false)
        endSelecting()
        autoShift()
    }

    fun onControllerKey(key: String) {
        if (key == "shift" && isShown) shift()
    }

    @Volatile
    private var inlineShown = false

    val isShown: Boolean get() = _visible.value || inlineShown

    fun space() {
        ime?.sendKeyChar(' ')
        endSelecting()
        autoShift()
    }

    fun backspace() {
        val k = ime ?: return
        val c = ic
        val raw = editor?.inputType == InputType.TYPE_NULL
        when {
            c == null || raw -> k.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            selStart != selEnd -> c.commitText("", 1)
            else -> if (!c.deleteSurroundingTextInCodePoints(1, 0)) k.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        endSelecting()
        autoShift()
    }

    private var wordLens: List<Int> = emptyList()
    private var wordBase = 0
    private var wordEnd = 0
    private var wordCount = 0

    fun beginWordDelete() {
        wordLens = emptyList()
        wordCount = 0
        val c = ic ?: return
        val et = try {
            c.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
        } catch (_: Exception) {
            null
        } ?: return
        val off = et.startOffset
        wordBase = minOf(et.selectionStart, et.selectionEnd) + off
        wordEnd = maxOf(et.selectionStart, et.selectionEnd) + off
        val before = et.text?.subSequence(0, (wordBase - off).coerceIn(0, et.text.length))?.toString() ?: return
        wordLens = wordsBack(before, MAX_WORDS)
    }

    fun selectWords(n: Int) {
        val k = n.coerceIn(0, wordLens.size)
        if (k == wordCount) return
        wordCount = k
        val start = wordBase - (if (k == 0) 0 else wordLens[k - 1])
        ic?.setSelection(start, wordEnd)
        OverlayController.haptic(VibrationEffect.EFFECT_TICK)
    }

    fun endWordDelete(commit: Boolean) {
        if (wordCount > 0) {
            if (commit) ic?.commitText("", 1) else ic?.setSelection(wordBase, wordEnd)
        }
        wordLens = emptyList()
        wordCount = 0
        endSelecting()
        autoShift()
    }

    private fun wordsBack(before: String, max: Int): List<Int> {
        val out = ArrayList<Int>()
        var i = before.length
        while (i > 0 && out.size < max) {
            while (i > 0 && before[i - 1].isWhitespace()) i--
            if (i > 0) {
                val word = before[i - 1].isLetterOrDigit()
                while (i > 0 && !before[i - 1].isWhitespace() && before[i - 1].isLetterOrDigit() == word) i--
            }
            out += before.length - i
        }
        return out
    }

    fun enter() {
        val k = ime ?: return
        val info = editor
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (_ui.value.enter != Enter.RETURN && action != EditorInfo.IME_ACTION_NONE) {
            ic?.performEditorAction(action)
        } else {
            k.sendKeyChar('\n')
        }
        endSelecting()
    }

    fun shift() {
        val u = _ui.value
        val now = SystemClock.uptimeMillis()
        _ui.value = when {
            u.caps -> u.copy(caps = false, shift = false)
            u.shift && now - lastShiftTap < 350 -> u.copy(caps = true, shift = false)
            else -> u.copy(shift = !u.shift)
        }
        lastShiftTap = now
    }

    fun layer(l: Layer) {
        _ui.value = _ui.value.copy(layer = l, shift = false)
    }

    fun hideKeyboard() {
        ime?.requestHideSelf(0)
    }

    fun cycleHeight() {
        setHeightMode((savedHeightMode + 1) % 3)
    }

    fun setHeightMode(mode: Int) {
        savedHeightMode = mode.coerceIn(0, 2)
        _ui.value = _ui.value.copy(heightMode = savedHeightMode)
    }

    private fun autoShift() {
        val u = _ui.value
        if (u.caps || u.layer != Layer.LETTERS) return
        val type = editor?.inputType ?: return
        val caps = try {
            ic?.getCursorCapsMode(type) ?: 0
        } catch (_: Exception) {
            0
        }
        _ui.value = u.copy(shift = caps != 0)
    }

    fun beginSwipe() {
        val et = try {
            ic?.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
        } catch (_: Exception) {
            null
        }
        text = et?.text
        textOffset = et?.startOffset ?: 0
        if (et != null) {
            selStart = et.selectionStart + textOffset
            selEnd = et.selectionEnd + textOffset
            if (!_ui.value.selecting) moving = selEnd
        }
    }

    fun endSwipe() {
        text = null
    }

    fun move(dir: Int): Boolean {
        val c = ic ?: return false
        val t = text
        val selecting = _ui.value.selecting
        if (t == null) {
            val code = if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val meta = if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            val now = SystemClock.uptimeMillis()
            c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
            c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
            return true
        }
        val len = textOffset + t.length
        val from = when {
            selecting -> moving
            dir < 0 -> selStart
            else -> selEnd
        }
        var to = (from + dir).coerceIn(0, len)
        val i = to - textOffset
        if (i in 1 until t.length && Character.isHighSurrogate(t[i - 1]) && Character.isLowSurrogate(t[i])) to += dir
        to = to.coerceIn(0, len)
        val collapsing = !selecting && selStart != selEnd
        if (to == from && !collapsing) return false
        if (selecting) {
            moving = to
            c.setSelection(anchor, to)
            selStart = minOf(anchor, to)
            selEnd = maxOf(anchor, to)
        } else {
            val p = if (collapsing) from else to
            c.setSelection(p, p)
            selStart = p
            selEnd = p
            moving = p
        }
        OverlayController.haptic(VibrationEffect.EFFECT_TICK)
        return true
    }

    fun toggleSelect() {
        val on = !_ui.value.selecting
        if (on) {
            beginSwipe()
            endSwipe()
            anchor = selStart
            moving = selEnd
        }
        _ui.value = _ui.value.copy(selecting = on)
    }

    private fun endSelecting() {
        if (_ui.value.selecting) _ui.value = _ui.value.copy(selecting = false)
    }

    fun copy() {
        val sel = try {
            ic?.getSelectedText(0)
        } catch (_: Exception) {
            null
        }
        if (sel.isNullOrEmpty()) {
            flash("Nichts markiert")
            return
        }
        if (isPassword()) {
            flash("Passwort geschützt")
            return
        }
        clipboard()?.setPrimaryClip(ClipData.newPlainText("text", sel))
        endSelecting()
        flash("Kopiert")
    }

    fun paste() {
        val app = OverlayController.appContext
        val clip = clipboard()?.primaryClip
        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(app)?.toString() else null
        if (t.isNullOrEmpty()) {
            flash("Zwischenablage leer")
            return
        }
        ic?.commitText(t, 1)
        endSelecting()
        autoShift()
    }

    private fun clipboard(): ClipboardManager? = OverlayController.appContext.getSystemService(ClipboardManager::class.java)

    private fun flash(msg: String) {
        _ui.value = _ui.value.copy(flash = msg, flashId = SystemClock.uptimeMillis())
    }

    private fun refreshPreview() {
        val c = ic ?: return
        try {
            var before = c.getTextBeforeCursor(PREVIEW_CHARS, 0)?.toString().orEmpty()
            var selected = c.getSelectedText(0)?.toString().orEmpty()
            var after = c.getTextAfterCursor(PREVIEW_CHARS, 0)?.toString().orEmpty()
            if (isPassword()) {
                before = "•".repeat(before.length)
                selected = "•".repeat(selected.length)
                after = "•".repeat(after.length)
            }
            if (selected.length > 40) selected = selected.take(18) + " … " + selected.takeLast(18)
            _ui.value = _ui.value.copy(before = before.substringAfterLast('\n'), selected = selected, after = after.substringBefore('\n'))
        } catch (e: Exception) {
            Log.w(TAG, "preview", e)
        }
    }

    private var dialog: Dialog? = null
    private var owner: OverlayOwner? = null
    private val hideRunnable = Runnable {
        if (!_visible.value) {
            try {
                dialog?.hide()
            } catch (_: Exception) {
            }
        }
    }

    private fun bottomDisplay(): Display? {
        val dm = OverlayController.appContext.getSystemService(DisplayManager::class.java)
        val id = DaemonClient.state.value?.bottomDisplayId ?: -1
        if (id >= 0) dm.getDisplay(id)?.let { return it }
        return dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }.minByOrNull { it.mode.physicalWidth * it.mode.physicalHeight }
    }

    private fun show() {
        if (DaemonClient.state.value?.bottomOn == false) {
            DaemonClient.setBottomPower(true)
            main.postDelayed({ if (_visible.value) showWindow() }, 350)
            _visible.value = true
            return
        }
        _visible.value = true
        showWindow()
    }

    private fun showWindow() {
        val existing = dialog
        if (existing != null) {
            val w = existing.window
            if (w != null) {
                val density = existing.context.resources.displayMetrics.density
                w.attributes = w.attributes.apply {
                    y = (getSavedOffsetYDp() * density).roundToInt()
                }
            }
            if (!existing.isShowing) {
                try {
                    existing.show()
                } catch (e: Exception) {
                    Log.w(TAG, "reshow keyboard failed", e)
                    removeNow()
                }
            }
            if (dialog != null) return
        }
        val display = bottomDisplay() ?: return
        val (ctx, type) = OverlayController.overlayContext(display) ?: run {
            Log.w(TAG, "no way to show the keyboard overlay")
            return
        }
        val d = Dialog(ctx, com.thorctl.R.style.Theme_ThorControl_Menu)
        val w = d.window ?: return
        w.setType(type)
        w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        w.setFormat(PixelFormat.TRANSLUCENT)
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        w.setGravity(Gravity.BOTTOM)
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        w.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        val density = ctx.resources.displayMetrics.density
        w.attributes = w.attributes.apply {
            title = "ThorKeyboard"
            y = (getSavedOffsetYDp() * density).roundToInt()
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            windowAnimations = 0
        }
        val o = OverlayOwner()
        val v = ComposeView(ctx)
        o.attach(v)
        v.setContent { ThorTheme { KeyboardPanel(ui = ui, visible = visible) } }
        d.setContentView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        d.setCancelable(false)
        try {
            d.show()
            o.resume()
            dialog = d
            owner = o
        } catch (e: Exception) {
            Log.w(TAG, "show keyboard failed", e)
            o.destroy()
        }
    }

    private fun removeNow() {
        main.removeCallbacks(hideRunnable)
        val d = dialog ?: return
        dialog = null
        try {
            d.dismiss()
        } catch (_: Exception) {
        }
        owner?.destroy()
        owner = null
    }

    private const val PREVIEW_CHARS = 60
    private const val MAX_WORDS = 200
}
