@file:OptIn(ExperimentalComposeUiApi::class)

package com.thorctl.ui

import android.os.VibrationEffect
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorctl.app.KeyboardController
import com.thorctl.app.KeyboardController.Enter
import com.thorctl.app.KeyboardController.Layer
import com.thorctl.app.OverlayController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val KbBg = Color(0xFF000000)
private val KeyFill = Color(0xFF1A1A1E)
private val KeyPressed = Color(0xFF32323A)
private val FnFill = Color(0xFF101014)
private val KeyInk = Color(0xFFFFFFFF)
private val KeyInkSoft = Color(0xFF8A8A96)
private val Accent = Color(0xFF4F8EF0)
private val KeyShape = RoundedCornerShape(10.dp)
private val ToolBtnShape = RoundedCornerShape(12.dp)
private val KeyShadow = Color(0x80000000)

private fun keyHaptic() = OverlayController.haptic(VibrationEffect.EFFECT_CLICK)

private val EMOJIS = listOf(
    "😀", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤩", "🥳",
    "😏", "😒", "🙄", "😬", "🤔", "😴", "🤯", "😱", "😭", "😤",
    "😡", "🤬", "🤢", "🤮", "🥵", "🥶", "🤡", "👻", "💀", "💩",
    "👍", "👎", "👌", "✌️", "🤞", "🤙", "💪", "👏", "🙌", "🙏",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "❤️‍🔥",
    "🔥", "✨", "⚡", "💥", "💯", "🎉", "🎊", "🏆", "🎮", "🕹️",
    "📱", "💻", "🎧", "🔋", "⚙️", "🔧", "🚀", "🚗", "🍺", "☕",
    "🍕", "🍔", "🍟", "🍗", "🥩", "🏋️", "🥊", "🐱", "🐶", "🦅",
)

@Composable
fun KeyboardPanel(ui: StateFlow<KeyboardController.Ui>, visible: StateFlow<Boolean>) {
    val state by ui.collectAsState()
    val shown by visible.collectAsState()
    var showEmojis by remember { mutableStateOf(false) }
    var customHeightDp by remember { mutableFloatStateOf(KeyboardController.getSavedHeightDp()) }
    var offsetYDp by remember { mutableFloatStateOf(KeyboardController.getSavedOffsetYDp()) }
    val density = LocalDensity.current

    LaunchedEffect(shown) {
        if (!shown) {
            showEmojis = false
        } else {
            customHeightDp = KeyboardController.getSavedHeightDp()
            offsetYDp = KeyboardController.getSavedOffsetYDp()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            KeyboardController.updateCustomHeightDp(customHeightDp)
            KeyboardController.updateOffsetYDp(offsetYDp)
        }
    }

    if (!shown) return

    val getOffset = { offsetYDp }
    val setOffset: (Float) -> Unit = { newVal ->
        val clamped = newVal.coerceIn(0f, 350f)
        offsetYDp = clamped
        KeyboardController.updateOffsetYDp(clamped)
    }

    val getHeight = { customHeightDp }
    val setHeight: (Float) -> Unit = { newVal ->
        val clamped = newVal.coerceIn(190f, 480f)
        customHeightDp = clamped
        KeyboardController.updateCustomHeightDp(clamped)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(customHeightDp.dp)
            .background(KbBg),
    ) {
        val popup = remember { KeyPopup() }
        popup.panelW = constraints.maxWidth.toFloat()
        Column(Modifier.fillMaxSize()) {
            ToolRow(
                state = state,
                showEmojis = showEmojis,
                onToggleEmojis = { showEmojis = !showEmojis },
                getOffset = getOffset,
                setOffset = setOffset,
                getHeight = getHeight,
                setHeight = setHeight,
                density = density.density,
            )
            if (showEmojis) {
                EmojiGrid(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            } else {
                Keys(
                    state = state,
                    popup = popup,
                    getOffset = getOffset,
                    setOffset = setOffset,
                    density = density.density,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 2.dp, end = 2.dp, bottom = 3.dp),
                )
            }
        }
        Flash(state, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        AccentPopup(popup)
    }
}

@Composable
private fun ToolRow(
    state: KeyboardController.Ui,
    showEmojis: Boolean,
    onToggleEmojis: () -> Unit,
    getOffset: () -> Float,
    setOffset: (Float) -> Unit,
    getHeight: () -> Float,
    setHeight: (Float) -> Unit,
    density: Float,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill(Icons.Rounded.HighlightAlt, "Markieren", active = state.selecting) {
            KeyboardController.toggleSelect()
        }
        Spacer(Modifier.width(4.dp))
        Pill(Icons.Rounded.ContentCopy, "Kopieren") { KeyboardController.copy() }
        Spacer(Modifier.width(4.dp))
        Pill(Icons.Rounded.ContentPaste, "Einfügen") { KeyboardController.paste() }
        Spacer(Modifier.weight(1f))
        WideIconButton(Icons.Rounded.EmojiEmotions, "Emojis", active = showEmojis, onTap = onToggleEmojis)
        Spacer(Modifier.width(5.dp))
        DirectDragButton(
            icon = Icons.Rounded.SwapVert,
            desc = "Auf/Ab verschieben",
            getValue = getOffset,
            setValue = setOffset,
            density = density,
            onTap = { setOffset(0f) },
        )
        Spacer(Modifier.width(5.dp))
        DirectDragButton(
            icon = Icons.Rounded.DragHandle,
            desc = "Höher/Kleiner ziehen",
            getValue = getHeight,
            setValue = setHeight,
            density = density,
        )
        Spacer(Modifier.width(5.dp))
        HideButton()
    }
}

@Composable
private fun DirectDragButton(
    icon: ImageVector,
    desc: String,
    getValue: () -> Float,
    setValue: (Float) -> Unit,
    density: Float,
    onTap: (() -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val slop = remember(ctx) { ViewConfiguration.get(ctx).scaledTouchSlop.toFloat() }
    val getCb by rememberUpdatedState(getValue)
    val setCb by rememberUpdatedState(setValue)
    val tapCb by rememberUpdatedState(onTap)

    var startRawY by remember { mutableFloatStateOf(0f) }
    var startVal by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableStateOf(false) }

    Box(
        Modifier
            .width(44.dp)
            .height(34.dp)
            .clip(ToolBtnShape)
            .background(if (dragging) Accent else KeyFill)
            .pointerInteropFilter { ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawY = ev.rawY
                        startVal = getCb()
                        moved = false
                        dragging = true
                        keyHaptic()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val totalDy = ev.rawY - startRawY
                        if (!moved && abs(totalDy) > slop) {
                            moved = true
                        }
                        if (moved) {
                            setCb(startVal - (totalDy / density))
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        dragging = false
                        if (!moved) {
                            tapCb?.invoke()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = KeyInk, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmojiGrid(modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 44.dp),
        modifier = modifier,
    ) {
        items(EMOJIS) { emoji ->
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(emoji) {
                        detectTapGestures(onTap = {
                            keyHaptic()
                            KeyboardController.typeExact(emoji)
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun WideIconButton(icon: ImageVector, desc: String, active: Boolean = false, onTap: () -> Unit) {
    Box(
        Modifier
            .width(44.dp)
            .height(34.dp)
            .clip(ToolBtnShape)
            .background(if (active) Accent else KeyFill)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    keyHaptic()
                    onTap()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = KeyInk, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HideButton() {
    WideIconButton(Icons.Rounded.KeyboardHide, "Tastatur ausblenden") {
        KeyboardController.hideKeyboard()
    }
}

val INLINE_KEYBOARD_HEIGHT: Dp = 268.dp

@Composable
fun InlineKeyboard(ui: StateFlow<KeyboardController.Ui>) {
    val state by ui.collectAsState()
    var showEmojis by remember { mutableStateOf(false) }
    val popup = remember { KeyPopup() }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(INLINE_KEYBOARD_HEIGHT)
            .background(KbBg),
    ) {
        popup.panelW = constraints.maxWidth.toFloat()
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(Icons.Rounded.ContentPaste, "Einfügen") { KeyboardController.paste() }
                Spacer(Modifier.weight(1f))
                WideIconButton(Icons.Rounded.EmojiEmotions, "Emojis", active = showEmojis) {
                    showEmojis = !showEmojis
                }
                Spacer(Modifier.width(6.dp))
                HideButton()
            }
            if (showEmojis) {
                EmojiGrid(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp, vertical = 4.dp))
            } else {
                Keys(
                    state = state,
                    popup = popup,
                    getOffset = null,
                    setOffset = null,
                    density = density.density,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 2.dp, end = 2.dp, bottom = 3.dp),
                )
            }
        }
        AccentPopup(popup)
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, active: Boolean = false, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val fill = when {
        active -> Accent
        pressed -> KeyPressed
        else -> KeyFill
    }
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(fill)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        keyHaptic()
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onTap() },
                )
            }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = KeyInk, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = KeyInk, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Flash(state: KeyboardController.Ui, modifier: Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.flashId) {
        if (state.flash == null) return@LaunchedEffect
        text = state.flash
        delay(1200)
        text = null
    }
    val a by animateFloatAsState(if (text != null) 1f else 0f, tween(150), label = "flash")
    if (a > 0f || text != null) {
        Text(
            text ?: "",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = modifier
                .graphicsLayer { alpha = a }
                .clip(RoundedCornerShape(14.dp))
                .background(KeyPressed)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private sealed interface Key {
    val w: Float
}

private data class Ch(val c: String, val alt: String?, val lead: Float = 0f, val trail: Float = 0f) : Key {
    override val w: Float get() = 1f + lead + trail
}

private data class Fn(val kind: FnKind, override val w: Float) : Key
private enum class FnKind { SHIFT, BACK, SPACE, ENTER, TO_LETTERS, TO_SYMBOLS, TO_MORE }

private val LETTER_ROWS = listOf("q w e r t z u i o p", "a s d f g h j k l", "y x c v b n m", ", .")
private val SYMBOL_ROWS = listOf("1 2 3 4 5 6 7 8 9 0", "@ # € & - + ( ) /", "* \" ' : ; ! ?", ", .")
private val MORE_ROWS = listOf("~ ` | • √ π ÷ × § Δ", "£ ¥ \$ ¢ ^ ° = { }", "\\ % © ® ™ ✓ [ ]", "< >")

private fun rows(layer: Layer): List<List<Key>> {
    val (main, alt) = when (layer) {
        Layer.LETTERS -> LETTER_ROWS to SYMBOL_ROWS
        Layer.SYMBOLS -> SYMBOL_ROWS to MORE_ROWS
        Layer.MORE -> MORE_ROWS to null
    }
    fun row(i: Int): List<Ch> {
        val alts = alt?.get(i)?.split(' ')
        return main[i].split(' ').mapIndexed { j, c -> Ch(c, alts?.getOrNull(j)) }
    }
    val r2 = row(1).let { r -> r.mapIndexed { j, k -> k.copy(lead = if (j == 0) 0.5f else 0f, trail = if (j == r.lastIndex) 0.5f else 0f) } }
    val r4 = row(3)
    val (left3, left4) = when (layer) {
        Layer.LETTERS -> FnKind.SHIFT to FnKind.TO_SYMBOLS
        Layer.SYMBOLS -> FnKind.TO_MORE to FnKind.TO_LETTERS
        Layer.MORE -> FnKind.TO_SYMBOLS to FnKind.TO_LETTERS
    }
    return listOf(
        row(0),
        r2,
        listOf(Fn(left3, 1.5f)) + row(2) + Fn(FnKind.BACK, 1.5f),
        listOf(Fn(left4, 1.5f), r4[0], Fn(FnKind.SPACE, 5f), r4[1], Fn(FnKind.ENTER, 1.5f)),
    )
}

/** Gedrückt halten für Umlaute & Akzente (ü, ö, ä, ß direkt unter dem Finger). */
private val ACCENTS: Map<String, List<String>> = mapOf(
    "a" to "ä à á â æ ã å ā",
    "c" to "ç ć č",
    "d" to "ď ð",
    "e" to "è é ê ë ē ė ę ě",
    "g" to "ğ",
    "i" to "î ï í ī į ì",
    "l" to "ł",
    "n" to "ñ ń ň",
    "o" to "ö ô ò ó œ ø ō õ",
    "r" to "ř",
    "s" to "ß ś š",
    "t" to "ť þ",
    "u" to "ü û ù ú ū ů",
    "y" to "ÿ ý",
    "z" to "ž ź ż",
    "-" to "_ – — ·",
    "€" to "\$ £ ¥ ¢",
    "!" to "¡",
    "?" to "¿",
    "'" to "‘ ’ ‚",
    "\"" to "“ ” „ « »",
    "." to "? ! , ; : / …",
    "0" to "°",
    "%" to "‰",
    "*" to "★ †",
).mapValues { it.value.split(' ') }

private const val LONG_PRESS_MS = 220L

@Stable
private class KeyPopup {
    var options by mutableStateOf<List<String>>(emptyList())
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var cellW by mutableFloatStateOf(0f)
    var cellH by mutableFloatStateOf(0f)
    var selected by mutableIntStateOf(0)
    var panelW = 0f

    fun indexAt(rootX: Float): Int = ((rootX - x) / cellW).toInt().coerceIn(0, (options.size - 1).coerceAtLeast(0))

    fun show(opts: List<String>, face: Rect, fingerX: Float, gap: Float) {
        cellW = face.width
        cellH = face.height
        val total = cellW * opts.size
        val alignLeft = face.left + total <= panelW - gap
        val ordered = if (alignLeft) opts else opts.reversed()
        x = if (alignLeft) face.left else face.right - total
        x = x.coerceIn(gap, (panelW - total - gap).coerceAtLeast(gap))
        y = (face.top - cellH - gap).coerceAtLeast(0f)
        options = ordered
        selected = indexAt(fingerX)
    }
}

@Composable
private fun AccentPopup(p: KeyPopup) {
    if (p.options.isEmpty()) return
    val d = LocalDensity.current
    Row(
        Modifier
            .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
            .size(with(d) { (p.cellW * p.options.size).toDp() }, with(d) { p.cellH.toDp() })
            .shadow(10.dp, KeyShape, ambientColor = KeyShadow, spotColor = KeyShadow)
            .clip(KeyShape)
            .background(KeyPressed)
            .padding(4.dp),
    ) {
        p.options.forEachIndexed { i, o ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (i == p.selected) Accent else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = KeyInk, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun Keys(
    state: KeyboardController.Ui,
    popup: KeyPopup,
    getOffset: (() -> Float)?,
    setOffset: ((Float) -> Unit)?,
    density: Float,
    modifier: Modifier,
) {
    val layout = rows(state.layer)
    Column(modifier) {
        layout.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEachIndexed { i, k ->
                    key(state.layer, r, i) {
                        when (k) {
                            is Ch -> CharKey(k, state, popup)
                            is Fn -> FnKey(k, state, getOffset, setOffset, density)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CharKey(k: Ch, state: KeyboardController.Ui, popup: KeyPopup) {
    val shifted = state.layer == Layer.LETTERS && (state.shift || state.caps)
    val label = if (shifted) k.c.uppercase() else k.c
    val accents = ACCENTS[k.c].orEmpty().map { if (shifted && it != "ß") it.uppercase() else it }
    val accentsNow by rememberUpdatedState(accents)
    var pressed by remember { mutableStateOf(false) }
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var cellRoot by remember { mutableStateOf(Offset.Zero) }
    var face by remember { mutableStateOf(Rect.Zero) }
    val gap = with(LocalDensity.current) { 8.dp.toPx() }
    Box(
        Modifier
            .weight(k.w)
            .fillMaxHeight()
            .onGloballyPositioned { cellRoot = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val acc = accentsNow
                    pressed = true
                    keyHaptic()
                    var mode = PRESS
                    var released = false
                    var past = false
                    val slop = viewConfiguration.touchSlop
                    val swipeDist = (if (face.height > 0f) face.height else size.height.toFloat()) * 0.45f
                    val hold = scope.launch {
                        delay(LONG_PRESS_MS)
                        if (mode == PRESS && acc.isNotEmpty()) {
                            mode = ACCENTS_OPEN
                            popup.show(acc, face, cellRoot.x + down.position.x, gap)
                            keyHaptic()
                        }
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            released = true
                            break
                        }
                        val d = ch.position - down.position
                        when (mode) {
                            PRESS -> if (k.alt != null && d.y > slop && d.y > abs(d.x)) {
                                mode = SWIPE
                                hold.cancel()
                            } else if (d.getDistance() > slop * 3) {
                                hold.cancel()
                            }
                            SWIPE -> {
                                val p = (d.y / swipeDist).coerceIn(0f, 1f)
                                scope.launch { swipe.snapTo(p) }
                                if ((p >= 0.5f) != past) {
                                    past = p >= 0.5f
                                    keyHaptic()
                                }
                            }
                            ACCENTS_OPEN -> {
                                val i = popup.indexAt(cellRoot.x + ch.position.x)
                                if (i != popup.selected) {
                                    popup.selected = i
                                    OverlayController.haptic(VibrationEffect.EFFECT_TICK)
                                }
                            }
                        }
                        ch.consume()
                    }
                    hold.cancel()
                    if (released) {
                        when (mode) {
                            PRESS -> KeyboardController.type(k.c)
                            SWIPE -> if (past && k.alt != null) KeyboardController.typeExact(k.alt) else KeyboardController.type(k.c)
                            ACCENTS_OPEN -> popup.options.getOrNull(popup.selected)?.let { KeyboardController.typeExact(it) }
                        }
                    }
                    popup.options = emptyList()
                    pressed = false
                    scope.launch { swipe.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f)) }
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            if (k.lead > 0f) Spacer(Modifier.weight(k.lead))
            KeyFace(
                pressed = pressed,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onFace = { face = it },
            ) { CharLabels(label, k.alt, swipe) }
            if (k.trail > 0f) Spacer(Modifier.weight(k.trail))
        }
    }
}

private const val PRESS = 0
private const val SWIPE = 1
private const val ACCENTS_OPEN = 2
private const val CURSOR = 5

@Composable
private fun CharLabels(label: String, alt: String?, swipe: Animatable<Float, *>) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val h = constraints.maxHeight.toFloat()
        if (alt != null) {
            Text(
                alt,
                color = KeyInkSoft,
                fontSize = 22.sp,
                modifier = Modifier.graphicsLayer {
                    val p = swipe.value as Float
                    val s = 0.5f + 0.5f * p
                    scaleX = s
                    scaleY = s
                    translationY = -h * 0.27f * (1f - p)
                    alpha = 0.65f + 0.35f * p
                },
            )
        }
        Text(
            label,
            color = KeyInk,
            fontSize = 22.sp,
            modifier = Modifier.graphicsLayer {
                val p = swipe.value as Float
                translationY = (if (alt != null) h * 0.09f else 0f) + h * 0.55f * p
                alpha = 1f - 0.8f * p
                val s = 1f - 0.15f * p
                scaleX = s
                scaleY = s
            },
        )
    }
}

@Composable
private fun RowScope.FnKey(
    k: Fn,
    state: KeyboardController.Ui,
    getOffset: (() -> Float)?,
    setOffset: ((Float) -> Unit)?,
    density: Float,
) {
    val m = Modifier.weight(k.w).fillMaxHeight()
    when (k.kind) {
        FnKind.SHIFT -> FnCell(m, active = state.shift || state.caps, onTap = { KeyboardController.shift() }) {
            ShiftGlyph(filled = state.shift || state.caps, underline = state.caps, tint = KeyInk)
        }
        FnKind.BACK -> BackspaceKey(m)
        FnKind.SPACE -> SpaceKey(m, state)
        FnKind.ENTER -> EnterKey(m, state, getOffset, setOffset, density)
        FnKind.TO_LETTERS -> LayerKey(m, "ABC", Layer.LETTERS)
        FnKind.TO_SYMBOLS -> LayerKey(m, "?123", Layer.SYMBOLS)
        FnKind.TO_MORE -> LayerKey(m, "=\\<", Layer.MORE)
    }
}

@Composable
private fun EnterKey(
    m: Modifier,
    state: KeyboardController.Ui,
    getOffset: (() -> Float)?,
    setOffset: ((Float) -> Unit)?,
    density: Float,
) {
    var pressed by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val getCb by rememberUpdatedState(getOffset)
    val setCb by rememberUpdatedState(setOffset)

    var holdJob by remember { mutableStateOf<Job?>(null) }
    var lastRawY by remember { mutableFloatStateOf(0f) }
    var startRawY by remember { mutableFloatStateOf(0f) }
    var startOffset by remember { mutableFloatStateOf(0f) }

    Box(
        m.pointerInteropFilter { ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = true
                    moving = false
                    lastRawY = ev.rawY
                    keyHaptic()
                    holdJob?.cancel()
                    holdJob = scope.launch {
                        delay(260L)
                        val g = getCb
                        if (g != null && setCb != null) {
                            startRawY = lastRawY
                            startOffset = g()
                            moving = true
                            keyHaptic()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lastRawY = ev.rawY
                    if (moving) {
                        val totalDy = ev.rawY - startRawY
                        setCb?.invoke(startOffset - (totalDy / density))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    holdJob?.cancel()
                    val wasMoving = moving
                    moving = false
                    pressed = false
                    if (!wasMoving) {
                        KeyboardController.enter()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    holdJob?.cancel()
                    moving = false
                    pressed = false
                    true
                }
                else -> false
            }
        },
    ) {
        KeyFace(pressed, Modifier.fillMaxSize(), active = moving, fn = true, onFace = {}) {
            Icon(
                if (moving) Icons.Rounded.SwapVert else enterIcon(state.enter),
                "Enter",
                tint = KeyInk,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SpaceKey(m: Modifier, state: KeyboardController.Ui) {
    var pressed by remember { mutableStateOf(false) }
    val stepPx = with(LocalDensity.current) { STEP.toPx() }
    Box(
        m.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                pressed = true
                keyHaptic()
                val slop = viewConfiguration.touchSlop
                var mode = PRESS
                var released = false
                var acc = 0f
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) {
                        released = true
                        break
                    }
                    val dx = ch.position.x - down.position.x
                    if (mode == PRESS && abs(dx) > slop) {
                        mode = CURSOR
                        KeyboardController.beginSwipe()
                    }
                    if (mode == CURSOR) {
                        acc += ch.positionChange().x
                        while (acc >= stepPx) {
                            acc -= stepPx
                            KeyboardController.move(1)
                        }
                        while (acc <= -stepPx) {
                            acc += stepPx
                            KeyboardController.move(-1)
                        }
                    }
                    ch.consume()
                }
                when (mode) {
                    PRESS -> if (released) KeyboardController.space()
                    CURSOR -> KeyboardController.endSwipe()
                }
                pressed = false
            }
        },
    ) {
        KeyFace(pressed, Modifier.fillMaxSize(), fn = false, onFace = {}) {
            Text(
                if (state.selecting) "‹  Markieren  ›" else "Deutsch (QWERTZ)",
                color = KeyInkSoft,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun BackspaceKey(m: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wordStep = with(LocalDensity.current) { WORD_STEP.toPx() }
    Box(
        m.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                pressed = true
                keyHaptic()
                val slop = viewConfiguration.touchSlop
                var mode = PRESS
                var released = false
                val repeat = scope.launch {
                    delay(420)
                    if (mode == PRESS) {
                        mode = REPEATING
                        while (true) {
                            KeyboardController.backspace()
                            delay(55)
                        }
                    }
                }
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) {
                        released = true
                        break
                    }
                    val dx = ch.position.x - down.position.x
                    if (mode == PRESS && dx < -slop) {
                        mode = WORDS
                        repeat.cancel()
                        KeyboardController.beginWordDelete()
                    }
                    if (mode == WORDS) {
                        val back = -dx - slop
                        KeyboardController.selectWords(if (back <= 0f) 0 else (back / wordStep).toInt() + 1)
                    }
                    ch.consume()
                }
                repeat.cancel()
                when (mode) {
                    PRESS -> if (released) KeyboardController.backspace()
                    WORDS -> KeyboardController.endWordDelete(commit = released)
                }
                pressed = false
            }
        },
    ) {
        KeyFace(pressed, Modifier.fillMaxSize(), fn = true, onFace = {}) {
            Icon(Icons.Rounded.Backspace, "Löschen", tint = KeyInk, modifier = Modifier.size(22.dp))
        }
    }
}

private const val REPEATING = 3
private const val WORDS = 4
private val WORD_STEP: Dp = 28.dp

@Composable
private fun LayerKey(m: Modifier, label: String, to: Layer) {
    FnCell(m, onTap = { KeyboardController.layer(to) }) {
        Text(label, color = KeyInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun enterIcon(e: Enter): ImageVector = when (e) {
    Enter.RETURN -> Icons.Rounded.KeyboardReturn
    Enter.GO, Enter.NEXT -> Icons.Rounded.ArrowForward
    Enter.SEARCH -> Icons.Rounded.Search
    Enter.SEND -> Icons.Rounded.Send
    Enter.DONE -> Icons.Rounded.Done
}

@Composable
private fun FnCell(m: Modifier, active: Boolean = false, repeat: Boolean = false, fn: Boolean = true, onTap: () -> Unit, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val action by rememberUpdatedState(onTap)
    val scope = rememberCoroutineScope()
    Box(
        m.pointerInput(repeat) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    keyHaptic()
                    var job: Job? = null
                    if (repeat) {
                        action()
                        job = scope.launch {
                            delay(420)
                            while (true) {
                                action()
                                delay(55)
                            }
                        }
                    }
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                },
                onTap = { if (!repeat) action() },
            )
        },
    ) {
        KeyFace(pressed, Modifier.fillMaxSize(), active = active, fn = fn, onFace = {}) { content() }
    }
}

@Composable
private fun KeyFace(
    pressed: Boolean,
    modifier: Modifier,
    active: Boolean = false,
    fn: Boolean = false,
    onFace: (Rect) -> Unit,
    content: @Composable () -> Unit,
) {
    val fill = when {
        active -> Accent
        pressed -> KeyPressed
        fn -> FnFill
        else -> KeyFill
    }
    Box(
        modifier
            .padding(3.dp)
            .onGloballyPositioned { onFace(it.boundsInRoot()) }
            .clip(KeyShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ShiftGlyph(filled: Boolean, underline: Boolean, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.52f)
            lineTo(w * 0.66f, h * 0.52f)
            lineTo(w * 0.66f, h * 0.80f)
            lineTo(w * 0.34f, h * 0.80f)
            lineTo(w * 0.34f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.52f)
            close()
        }
        val stroke = Stroke(width = w * 0.075f, join = StrokeJoin.Round)
        drawPath(p, tint, style = if (filled) Fill else stroke)
        if (filled) drawPath(p, tint, style = stroke)
        if (underline) drawLine(tint, Offset(w * 0.28f, h * 0.94f), Offset(w * 0.72f, h * 0.94f), strokeWidth = w * 0.08f)
    }
}

private val STEP: Dp = 11.dp
