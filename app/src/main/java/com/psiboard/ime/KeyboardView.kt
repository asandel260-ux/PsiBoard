package com.psiboard.ime

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.SparseArray
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Key-cap fonts. Persian defaults to Vazirmatn (monolinear UI face, bundled at build time) when present. */
object Fonts {
    private var vz: Typeface? = null
    private var tried = false
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private fun vazir(ctx: Context): Typeface? {
        if (!tried) {
            tried = true
            vz = try { Typeface.createFromAsset(ctx.assets, "fonts/Vazirmatn-Medium.ttf") } catch (_: Exception) { null }
            android.util.Log.i("psiboard", if (vz != null) "font: Vazirmatn loaded" else "font: Vazirmatn asset missing, using system font")
        }
        return vz
    }
    fun resolve(ctx: Context, key: String, fa: Boolean): Typeface = when (key) {
        "sans" -> Typeface.create("sans-serif", Typeface.NORMAL)
        "sans-medium" -> medium
        "sans-bold" -> Typeface.create("sans-serif", Typeface.BOLD)
        "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        "italic" -> Typeface.create("sans-serif", Typeface.ITALIC)
        "vazirmatn" -> vazir(ctx) ?: medium
        else -> if (fa) (vazir(ctx) ?: medium) else medium
    }
}

/** Snapshot of every visual setting, pre-scaled to pixels. Rebuilt whenever a setting changes. */
class Theme(ctx: Context) {
    init { Settings.refreshDerived(ctx) }

    val dens = ctx.resources.displayMetrics.density
    private val d = dens
    private val sd = ctx.resources.displayMetrics.scaledDensity
    private fun dp(k: String) = Settings.int(k) * d
    private fun sp(k: String) = Settings.int(k) * sd
    private fun a(color: Int, pct: Int) = Color.argb((Color.alpha(color) * pct / 100f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    val dark = Settings.isDark
    val keyOp = Settings.int("key_opacity")
    val bg = a(Settings.int("c_bg"), Settings.int("kb_opacity"))
    val bg2 = a(Settings.int("c_bg2"), Settings.int("kb_opacity"))
    val gradient = Settings.bool("bg_gradient")
    val key = a(Settings.int("c_key"), keyOp)
    val keyP = Settings.int("c_key_pressed")
    val special = a(Settings.int("c_special"), keyOp)
    val specialP = Settings.int("c_special_pressed")
    val accent = Settings.int("c_accent")
    val text = a(Settings.int("c_text"), Settings.int("text_opacity"))
    val textSpecial = Settings.int("c_text_special")
    val textAccent = Settings.int("c_text_accent")
    val hint = Settings.int("c_hint")
    val border = Settings.int("c_border")
    val shadow = Settings.int("c_shadow")
    val popupBg = Settings.int("c_popup_bg")
    val popupSel = Settings.int("c_popup_sel")
    val popupText = Settings.int("c_popup_text")
    val stripBg = Settings.int("c_strip_bg")
    val stripText = Settings.int("c_strip_text")
    val stripIcon = Settings.int("c_strip_icon")
    val nav = Settings.int("c_nav")

    val highlight = Settings.int("key_highlight").let { if (it < 0) (if (dark) 26 else 0) else it }
    val shadowOff = Settings.int("key_shadow").let { if (it < 0) (if (dark) 0f else 1.2f * d) else it * d }
    val keyH = dp("key_height")
    val gapH = dp("key_gap_h")
    val gapV = dp("key_gap_v")
    val radius = dp("key_radius")
    val borderW = dp("key_border_w")
    val textSize = sp("text_size")
    val specialSize = sp("special_text_size")
    val hintSize = sp("hint_size")
    val sidePad = dp("side_pad")
    val bottomPad = dp("bottom_pad")
    val topPad = dp("top_pad")
    val showHints = Settings.bool("show_hints")
    val preview = Settings.bool("key_preview")
    val previewSize = dp("preview_size")
    val popupRadius = dp("popup_radius")
    val heightScale = Settings.int("height_scale") / 100f
    val landScale = Settings.int("landscape_height_scale") / 100f
    val oneHanded = Settings.bool("one_handed")
    val oneHandedRight = Settings.str("one_handed_side") == "right"
    val oneHandedW = Settings.int("one_handed_width") / 100f
    val labelCase = Settings.str("label_case")
    val longPressMs = Settings.int("longpress_ms").toLong()
    val repeatStart = Settings.int("repeat_start_ms").toLong()
    val repeatMs = Settings.int("repeat_ms").toLong()
    val swipeStep = Settings.int("swipe_step") * d / 2f
    val spaceSwipe = Settings.str("space_swipe")
    val deleteSwipe = Settings.str("delete_swipe")
    val touchY = Settings.int("touch_y_offset") * d
    val stripH = dp("strip_height")
    val stripTextSize = sp("strip_text_size")
    val showSpaceLabel = Settings.bool("show_space_label")
    val enterIcons = Settings.bool("enter_icons")
    val enTf: Typeface = Fonts.resolve(ctx, Settings.str("font_en"), false)
    val faTf: Typeface = Fonts.resolve(ctx, Settings.str("font_fa"), true)
    val faScale = Settings.int("fa_text_scale") / 100f
}

class Feedback(private val ctx: Context) {
    private val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    fun tick(v: View, longPress: Boolean = false) {
        if (Settings.bool("haptic") && (!longPress || Settings.bool("haptic_longpress"))) {
            if (Settings.str("haptic_style") == "system") {
                v.performHapticFeedback(if (longPress) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                val ms = Settings.int("haptic_ms").toLong()
                try {
                    if (Build.VERSION.SDK_INT >= 26) vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vib?.vibrate(ms)
                } catch (_: Exception) {}
            }
        }
        if (Settings.bool("sound")) am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, Settings.int("sound_vol") / 100f)
    }
}

interface KbListener {
    fun onText(s: String)
    fun onToken(t: String)
    fun onMoveCursor(steps: Int, select: Boolean)
    fun onDeleteWordSwipe()
}

/** Floating bubble used for key preview and long-press popups. */
class PopupView(ctx: Context) : View(ctx) {
    var th: Theme? = null
    var items: List<String> = emptyList()
    var sel = -1
    var itemW = 0f
    var itemH = 0f
    var fa = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val refB = Rect()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((itemW * items.size).toInt(), itemH.toInt())
    override fun onDraw(c: Canvas) {
        val t = th ?: return
        val pad = 3f * t.dens
        p.style = Paint.Style.FILL; p.color = t.popupBg
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(r, t.popupRadius, t.popupRadius, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = t.dens; p.color = Ok.mix(t.popupBg, t.popupText, 0.22f)
        r.inset(t.dens / 2, t.dens / 2); c.drawRoundRect(r, t.popupRadius, t.popupRadius, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER; p.typeface = if (fa) t.faTf else t.enTf; p.textSize = t.textSize * 1.25f * (if (fa) t.faScale else 1f)
        val ref = if (fa) "اسب" else "Hx"
        p.getTextBounds(ref, 0, ref.length, refB)
        val base = height / 2f - (refB.top + refB.bottom) / 2f
        for (i in items.indices) {
            if (i == sel) { p.color = t.popupSel; r.set(i * itemW + pad, pad, (i + 1) * itemW - pad, height - pad); c.drawRoundRect(r, max(0f, t.popupRadius - pad), max(0f, t.popupRadius - pad), p) }
            p.color = if (i == sel) t.textAccent else t.popupText
            c.drawText(items[i], i * itemW + itemW / 2, base, p)
        }
    }
}

class KeyboardView(ctx: Context) : View(ctx) {
    var listener: KbListener? = null
    var th = Theme(ctx)
    var rows: List<List<Key>> = emptyList()
    var shift = 0 // 0 off, 1 once, 2 caps lock
        set(v) { field = v; invalidate() }
    var spaceLabel = ""
    var symLabel = "?123"
    var abcLabel = "ABC"
    var enterLabel: String? = null
    var isLandscape = false
    var lang = "en"

    private val feedback = Feedback(ctx)
    private val h = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val rect = RectF()
    private val rect2 = RectF()
    private val refB = Rect()
    private val path = Path()
    private val allKeys = ArrayList<Key>()
    private var animating = false

    private class P(val id: Int) {
        var key: Key? = null; var downX = 0f; var lastX = 0f; var swiped = false
        var popup = false; var sel = -1; var acc = 0f
        var lp: Runnable? = null; var rep: Runnable? = null
    }
    private val ptrs = SparseArray<P>()
    private var pop: PopupWindow? = null
    private var popView: PopupView? = null
    private var popLeft = 0f
    private var popItemW = 0f

    fun applyTheme(t: Theme) { th = t; requestLayout(); invalidate() }
    fun submitRows(r: List<List<Key>>) { rows = r; allKeys.clear(); r.forEach { allKeys.addAll(it) }; requestLayout(); layoutKeys(); invalidate() }

    private fun scale() = if (isLandscape) th.landScale else th.heightScale

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val units = rows.sumOf { (it.firstOrNull()?.hScale ?: 1f).toDouble() }.toFloat()
        setMeasuredDimension(MeasureSpec.getSize(wSpec), (th.topPad + th.bottomPad + units * th.keyH * scale()).toInt())
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { layoutKeys() }

    private fun layoutKeys() {
        if (width == 0 || rows.isEmpty()) return
        val t = th
        var availW = width - 2 * t.sidePad
        var left = t.sidePad
        if (t.oneHanded) { val nw = availW * t.oneHandedW; if (t.oneHandedRight) left = width - t.sidePad - nw; availW = nw }
        val pitch = t.keyH * scale()
        val body = rows.dropLast(1)
        val unit = availW / max(10f, body.maxOfOrNull { r -> r.sumOf { it.weight.toDouble() }.toFloat() } ?: 10f)
        var yy = t.topPad
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val u = if (ri == rows.size - 1) availW / total else unit
            var x = left + (availW - total * u) / 2f
            val rh = pitch * row[0].hScale
            for (k in row) { k.x = x; k.y = yy; k.w = k.weight * u; k.h = rh; x += k.w }
            yy += rh
        }
    }

    private fun caseOf(s: String) = when {
        th.labelCase == "upper" || shift != 0 -> s.uppercase()
        else -> s
    }

    override fun onDraw(c: Canvas) {
        val t = th
        paint.style = Paint.Style.FILL
        if (t.gradient) paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), t.bg, t.bg2, Shader.TileMode.CLAMP) else { paint.shader = null; paint.color = t.bg }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        animating = false
        val now = SystemClock.uptimeMillis()
        for (k in allKeys) drawKey(c, k, now)
        if (animating) postInvalidateOnAnimation()
    }

    /** 1 while held, easing back to 0 over 150 ms after release: the press state fades instead of snapping. */
    private fun pressAmt(k: Key, now: Long): Float {
        if (k.pressed) return 1f
        val dt = now - k.releaseAt
        if (dt >= 150L || k.releaseAt == 0L) return 0f
        animating = true
        val f = 1f - dt / 150f
        return f * f
    }

    private fun drawKey(c: Canvas, k: Key, now: Long) {
        val t = th
        rect.set(k.x + t.gapH / 2, k.y + t.gapV / 2, k.x + k.w - t.gapH / 2, k.y + k.h - t.gapV / 2)
        val minSide = min(rect.width(), rect.height())
        val rad = min(t.radius, minSide * 0.34f)
        val enter = k.token == "{enter}"
        val fn = k.special && k.token != "{space}"   // space bar is a big key: letter tone, so tinted function keys frame the letters
        val amt = pressAmt(k, now)
        if (t.shadowOff > 0 && amt < 0.5f) {
            paint.style = Paint.Style.FILL; paint.color = t.shadow
            rect.offset(0f, t.shadowOff); c.drawRoundRect(rect, rad, rad, paint); rect.offset(0f, -t.shadowOff)
        }
        val base = if (enter) t.accent else if (fn) t.special else t.key
        val down = if (enter) Ok.mix(t.accent, Color.BLACK, 0.22f) else if (fn) t.specialP else t.keyP
        paint.style = Paint.Style.FILL
        paint.color = if (amt > 0f) Ok.mix(base, down, amt) else base
        c.drawRoundRect(rect, rad, rad, paint)
        if (t.highlight > 0 && amt < 0.5f) {
            // thin light edge along the top of the key = tactile depth (light from above)
            val hw = 1.2f * t.dens
            c.save(); c.clipRect(rect.left, rect.top, rect.right, rect.top + rad * 0.9f + hw)
            rect2.set(rect); rect2.inset(hw / 2, hw / 2)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = hw
            paint.color = Color.argb((t.highlight * 0.45f * 2.55f * (1f - amt * 2f)).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawRoundRect(rect2, rad, rad, paint); c.restore()
        }
        if (t.borderW > 0 && Color.alpha(t.border) > 0) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = t.borderW; paint.color = t.border
            c.drawRoundRect(rect, rad, rad, paint)
        }
        val cx = rect.centerX(); val cy = rect.centerY()
        val fg = if (enter) t.textAccent else if (fn) t.textSpecial else t.text
        val fa = lang == "fa"
        val tscale = if (fa) t.faScale else 1f
        paint.style = Paint.Style.FILL; paint.color = fg; paint.typeface = if (fa) t.faTf else t.enTf; paint.textAlign = Paint.Align.CENTER
        ip.color = fg; ip.strokeWidth = max(1.6f * t.dens, 2f * t.dens * 0.92f)
        val s = min(9.5f * t.dens, minSide * 0.3f)
        when (k.token) {
            "{shift}" -> Glyphs.draw(c, if (shift == 2) "caps" else if (shift == 1) "shift_on" else "shift", cx, cy, s, ip, rect2, path)
            "{del}" -> Glyphs.draw(c, "del", cx, cy, s, ip, rect2, path)
            "{zwnj}" -> Glyphs.draw(c, "zwnj", cx, cy, s, ip, rect2, path)
            "{enter}" -> {
                val ic = if (t.enterIcons || enterLabel == null) when (enterLabel) {
                    "Go" -> "go"; "Search" -> "search"; "Send" -> "send"; "Next" -> "next"; "Done" -> "done"; else -> "enter"
                } else null
                if (ic != null) Glyphs.draw(c, ic, cx, cy, s, ip, rect2, path) else label(c, enterLabel!!, cx, cy, t.specialSize * 0.95f, rect.width())
            }
            "{emoji}" -> Glyphs.draw(c, "emoji", cx, cy, s * 1.05f, ip, rect2, path)
            "{lang}" -> Glyphs.draw(c, "globe", cx, cy, s * 1.05f, ip, rect2, path)
            "{space}" -> if (t.showSpaceLabel) { paint.color = t.hint; label(c, spaceLabel, cx, cy, t.specialSize * 0.9f * tscale, rect.width()) }
            "{sym}" -> label(c, symLabel, cx, cy, t.specialSize * 0.95f * tscale, rect.width())
            "{sym2}" -> label(c, "=\\<", cx, cy, t.specialSize, rect.width())
            "{abc}" -> label(c, abcLabel, cx, cy, t.specialSize * 0.95f * tscale, rect.width())
            else -> {
                val numRow = k.hScale < 1f
                if (numRow) paint.color = t.textSpecial
                val punct = k.label.length == 1 && !k.label[0].isLetterOrDigit()
                label(c, caseOf(k.label), cx, cy, t.textSize * tscale * (if (numRow) 0.82f else if (punct) 1.15f else 1f), rect.width())
                if (t.showHints && k.hint != null) {
                    paint.color = t.hint; paint.textSize = t.hintSize * tscale; paint.textAlign = Paint.Align.RIGHT
                    c.drawText(k.hint!!, rect.right - t.hintSize * 0.55f, rect.top + t.hintSize * 1.5f, paint)
                }
            }
        }
    }

    /** Text is centred on the optical middle of a reference glyph pair, so every key's letters sit on one baseline. */
    private fun label(c: Canvas, s: String, cx: Float, cy: Float, size: Float, maxW: Float) {
        paint.textSize = size
        val w = paint.measureText(s)
        if (w > maxW * 0.86f) paint.textSize = size * maxW * 0.86f / w
        val ref = if (lang == "fa") "اسب" else "Hx"
        paint.getTextBounds(ref, 0, ref.length, refB)
        c.drawText(s, cx, cy - (refB.top + refB.bottom) / 2f, paint)
    }

    // ---------------- touch ----------------

    private fun keyAt(x: Float, yRaw: Float): Key? {
        if (allKeys.isEmpty()) return null
        val top = rows.first()[0].y
        val last = rows.last()[0]
        val y = (yRaw - th.touchY).coerceIn(top, last.y + last.h - 1f)
        var best: Key? = null; var bd = Float.MAX_VALUE
        for (k in allKeys) {
            if (y < k.y || y >= k.y + k.h) continue
            if (x >= k.x && x < k.x + k.w) return k
            val d = if (x < k.x) k.x - x else x - (k.x + k.w)
            if (d < bd) { bd = d; best = k }
        }
        return if (bd < th.gapH + th.sidePad + 12f * th.dens) best else null
    }

    private fun inval(k: Key) = invalidate(k.x.toInt() - 2, k.y.toInt() - 2, (k.x + k.w).toInt() + 2, (k.y + k.h).toInt() + 6)

    private fun unpress(k: Key) { if (k.pressed) { k.pressed = false; k.releaseAt = SystemClock.uptimeMillis(); inval(k) } }

    private fun press(p: P, k: Key, x: Float, y: Float) {
        p.key = k; k.pressed = true; inval(k)
        feedback.tick(this)
        when {
            k.token == "{del}" -> {
                listener?.onToken("{del}")
                p.rep = object : Runnable { override fun run() { listener?.onToken("{del}"); h.postDelayed(this, th.repeatMs) } }
                h.postDelayed(p.rep!!, th.repeatStart)
            }
            !k.special -> {
                if (th.preview) showPreview(k)
                if (k.popups.isNotEmpty()) {
                    p.lp = Runnable { p.lp = null; if (p.key === k) { feedback.tick(this, true); showPopup(p, k, p.lastX) } }
                    h.postDelayed(p.lp!!, th.longPressMs)
                }
            }
            k.token == "{space}" || k.token == "{emoji}" || k.token == "{lang}" -> {
                p.lp = Runnable { p.lp = null; if (p.key === k && !p.swiped) { feedback.tick(this, true); p.swiped = true; listener?.onToken(k.token + "_long") } }
                h.postDelayed(p.lp!!, th.longPressMs + 200)
            }
        }
    }

    private fun release(p: P) {
        p.lp?.let { h.removeCallbacks(it) }; p.rep?.let { h.removeCallbacks(it) }; p.lp = null; p.rep = null
        p.key?.let { unpress(it) }
        hidePopup()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = e.getPointerId(idx); val x = e.getX(idx); val y = e.getY(idx)
                val k = keyAt(x, y) ?: return true
                val p = P(id); p.downX = x; p.lastX = x; ptrs.put(id, p); press(p, k, x, y)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(ptrs.get(e.getPointerId(i)) ?: continue, e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val p = ptrs.get(e.getPointerId(idx)) ?: return true
                ptrs.remove(p.id); up(p)
            }
            MotionEvent.ACTION_CANCEL -> { for (i in 0 until ptrs.size()) release(ptrs.valueAt(i)); ptrs.clear() }
        }
        return true
    }

    private fun move(p: P, x: Float, y: Float) {
        val k = p.key ?: return
        if (p.popup) { updatePopupSel(p, x); return }
        val dx = x - p.lastX
        when {
            k.token == "{space}" && th.spaceSwipe == "cursor" -> {
                p.acc += dx
                if (abs(x - p.downX) > th.swipeStep * 2 || p.swiped) {
                    p.swiped = true; p.lp?.let { h.removeCallbacks(it) }
                    val steps = (p.acc / th.swipeStep).toInt()
                    if (steps != 0) { p.acc -= steps * th.swipeStep; listener?.onMoveCursor(steps, shift != 0) }
                }
            }
            k.token == "{del}" && th.deleteSwipe != "none" -> {
                p.acc += dx
                if (p.acc < -th.swipeStep * 2) {
                    p.swiped = true; p.rep?.let { h.removeCallbacks(it) }
                    val step = th.swipeStep * 2.5f
                    val steps = (-p.acc / step).toInt()
                    if (steps > 0) {
                        p.acc += steps * step
                        if (th.deleteSwipe == "delete-word") repeat(steps) { listener?.onDeleteWordSwipe() } else listener?.onMoveCursor(-steps, true)
                    }
                }
            }
            else -> {
                val nk = keyAt(x, y)
                if (nk != null && nk !== k && !k.special && !nk.special && !p.swiped) {
                    release(p); press(p, nk, x, y)
                }
            }
        }
        p.lastX = x
    }

    private fun up(p: P) {
        val k = p.key
        val wasPopup = p.popup
        val sel = p.sel
        val popItems = popView?.items
        release(p)
        if (k == null) return
        if (wasPopup) { if (popItems != null && sel in popItems.indices) listener?.onText(caseOf(popItems[sel])); return }
        if (p.swiped || k.token == "{del}") return
        if (k.special) listener?.onToken(k.token) else listener?.onText(caseOf(k.label))
    }

    // -------------- popups --------------

    private fun ensurePop(): PopupView {
        popView?.let { return it }
        val v = PopupView(context); popView = v
        pop = PopupWindow(v, -2, -2).apply { isTouchable = false; isClippingEnabled = false; setBackgroundDrawable(null) }
        return v
    }

    private fun showPreview(k: Key) {
        val v = ensurePop(); v.th = th; v.fa = lang == "fa"; v.items = listOf(caseOf(k.label)); v.sel = -1
        v.itemW = max(th.previewSize, k.w * 0.95f); v.itemH = th.previewSize * 1.1f
        v.measure(0, 0)
        placePopup(v, k.x + k.w / 2 - v.itemW / 2, k.y - v.itemH - th.gapV * 0.5f)
    }

    private fun showPopup(p: P, k: Key, x: Float) {
        val v = ensurePop(); v.th = th; v.fa = lang == "fa"; v.items = k.popups.map { caseOf(it) }
        val n = v.items.size
        v.itemW = min(max(th.previewSize * 0.8f, k.w), width.toFloat() / n); v.itemH = th.previewSize * 1.1f
        v.measure(0, 0)
        val total = v.itemW * n
        val left = (k.x + k.w / 2 - total / 2).coerceIn(0f, max(0f, width - total))
        popLeft = left; popItemW = v.itemW
        p.popup = true
        v.sel = ((x - left) / v.itemW).toInt().coerceIn(0, n - 1); p.sel = v.sel
        placePopup(v, left, k.y - v.itemH - th.gapV * 0.5f)
    }

    private fun updatePopupSel(p: P, x: Float) {
        val v = popView ?: return
        val s = ((x - popLeft) / popItemW).toInt().coerceIn(0, v.items.size - 1)
        if (s != p.sel) { p.sel = s; v.sel = s; v.invalidate(); feedback.tick(this, true) }
    }

    private fun placePopup(v: PopupView, x: Float, y: Float) {
        // A popup attached to a window is positioned relative to *that window* (the IME window does not start at screen y=0),
        // so use window coordinates; screen coordinates would push the bubble off the bottom of the display.
        val loc = IntArray(2); getLocationInWindow(loc)
        val px = (loc[0] + x).toInt(); val py = (loc[1] + y).toInt()
        val w = v.measuredWidth; val hh = v.measuredHeight
        val pw = pop ?: return
        try {
            if (pw.isShowing) pw.update(px, py, w, hh) else { pw.width = w; pw.height = hh; pw.showAtLocation(this, Gravity.NO_GRAVITY, px, py) }
            v.invalidate()
        } catch (e: Exception) { android.util.Log.w("psiboard", "popup failed", e) }
    }

    private fun hidePopup() { try { pop?.dismiss() } catch (_: Exception) {} }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (i in 0 until ptrs.size()) release(ptrs.valueAt(i)); ptrs.clear(); hidePopup()
    }
}
