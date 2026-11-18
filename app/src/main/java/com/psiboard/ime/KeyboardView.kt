package com.psiboard.ime

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.SparseArray
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Snapshot of every visual setting, pre-scaled to pixels. Rebuilt whenever a setting changes. */
class Theme(ctx: Context) {
    private val d = ctx.resources.displayMetrics.density
    private val sd = ctx.resources.displayMetrics.scaledDensity
    private fun dp(k: String) = Settings.int(k) * d
    private fun sp(k: String) = Settings.int(k) * sd
    private fun a(color: Int, pct: Int) = Color.argb((Color.alpha(color) * pct / 100f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

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

    val keyH = dp("key_height")
    val gapH = dp("key_gap_h")
    val gapV = dp("key_gap_v")
    val radius = dp("key_radius")
    val borderW = dp("key_border_w")
    val shadowOff = dp("key_shadow")
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
    val stripH = dp("strip_height")
    val stripTextSize = sp("strip_text_size")
    val showSpaceLabel = Settings.bool("show_space_label")
    val typeface: Typeface = when (Settings.str("font")) {
        "sans-bold" -> Typeface.create("sans-serif", Typeface.BOLD)
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        "italic" -> Typeface.create("sans-serif", Typeface.ITALIC)
        "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }
}

class Feedback(private val ctx: Context) {
    private val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    fun tick(longPress: Boolean = false) {
        if (Settings.bool("haptic") && (!longPress || Settings.bool("haptic_longpress"))) {
            val ms = Settings.int("haptic_ms").toLong()
            try {
                if (Build.VERSION.SDK_INT >= 26) vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vib?.vibrate(ms)
            } catch (_: Exception) {}
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
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((itemW * items.size).toInt(), itemH.toInt())
    override fun onDraw(c: Canvas) {
        val t = th ?: return
        p.style = Paint.Style.FILL; p.color = t.popupBg
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(r, t.popupRadius, t.popupRadius, p)
        p.textAlign = Paint.Align.CENTER; p.typeface = t.typeface; p.textSize = t.textSize * 1.3f
        for (i in items.indices) {
            if (i == sel) { p.color = t.popupSel; r.set(i * itemW + 3, 3f, (i + 1) * itemW - 3, height - 3f); c.drawRoundRect(r, t.popupRadius, t.popupRadius, p) }
            p.color = t.popupText
            c.drawText(items[i], i * itemW + itemW / 2, height / 2f - (p.ascent() + p.descent()) / 2, p)
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
    var enterLabel: String? = null
    var isLandscape = false

    private val feedback = Feedback(ctx)
    private val h = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val rect = RectF()
    private val path = Path()
    private val allKeys = ArrayList<Key>()

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
        val hh = th.topPad + th.bottomPad + rows.size * th.keyH * scale()
        setMeasuredDimension(MeasureSpec.getSize(wSpec), hh.toInt())
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
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val u = if (ri == rows.size - 1) availW / total else unit
            var x = left + (availW - total * u) / 2f
            val y = t.topPad + ri * pitch
            for (k in row) { k.x = x; k.y = y; k.w = k.weight * u; k.h = pitch; x += k.w }
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
        for (k in allKeys) drawKey(c, k)
    }

    private fun drawKey(c: Canvas, k: Key) {
        val t = th
        rect.set(k.x + t.gapH / 2, k.y + t.gapV / 2, k.x + k.w - t.gapH / 2, k.y + k.h - t.gapV / 2)
        val enter = k.token == "{enter}"
        if (t.shadowOff > 0) {
            paint.color = t.shadow; paint.style = Paint.Style.FILL
            rect.offset(0f, t.shadowOff); c.drawRoundRect(rect, t.radius, t.radius, paint); rect.offset(0f, -t.shadowOff)
        }
        paint.style = Paint.Style.FILL
        paint.color = when {
            k.pressed -> if (k.special) t.specialP else t.keyP
            enter -> t.accent
            k.special -> t.special
            else -> t.key
        }
        c.drawRoundRect(rect, t.radius, t.radius, paint)
        if (t.borderW > 0 && Color.alpha(t.border) > 0) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = t.borderW; paint.color = t.border
            c.drawRoundRect(rect, t.radius, t.radius, paint)
        }
        val cx = rect.centerX(); val cy = rect.centerY()
        val fg = if (enter) t.textAccent else if (k.special) t.textSpecial else t.text
        paint.style = Paint.Style.FILL; paint.color = fg; paint.typeface = t.typeface; paint.textAlign = Paint.Align.CENTER
        ip.color = fg; ip.strokeWidth = max(2f, rect.height() * 0.045f); ip.style = Paint.Style.STROKE
        val s = min(rect.width(), rect.height()) * 0.2f
        when (k.token) {
            "{shift}" -> drawShift(c, cx, cy, s)
            "{del}" -> drawDel(c, cx, cy, s)
            "{enter}" -> if (enterLabel != null) label(c, enterLabel!!, cx, cy, t.specialSize, rect.width()) else drawEnter(c, cx, cy, s)
            "{emoji}" -> drawSmiley(c, cx, cy, s * 1.2f)
            "{lang}" -> drawGlobe(c, cx, cy, s * 1.2f)
            "{space}" -> if (t.showSpaceLabel) { paint.color = t.hint; label(c, spaceLabel, cx, cy, t.specialSize * 0.85f, rect.width()) }
            "{sym}" -> label(c, "?123", cx, cy, t.specialSize, rect.width())
            "{sym2}" -> label(c, "=\\<", cx, cy, t.specialSize, rect.width())
            "{abc}" -> label(c, "ABC", cx, cy, t.specialSize, rect.width())
            else -> {
                label(c, caseOf(k.label), cx, cy, t.textSize, rect.width())
                if (t.showHints && k.hint != null) {
                    paint.color = t.hint; paint.textSize = t.hintSize; paint.textAlign = Paint.Align.RIGHT
                    c.drawText(k.hint!!, rect.right - t.hintSize * 0.5f, rect.top + t.hintSize * 1.4f, paint)
                } else if (t.showHints && k.popups.isNotEmpty() && k.label.length == 1 && !k.label[0].isLetterOrDigit()) {
                    // punctuation keys show their first alternative as a hint
                    paint.color = t.hint; paint.textSize = t.hintSize; paint.textAlign = Paint.Align.RIGHT
                    c.drawText(k.popups[0], rect.right - t.hintSize * 0.5f, rect.top + t.hintSize * 1.4f, paint)
                }
            }
        }
    }

    private fun label(c: Canvas, s: String, cx: Float, cy: Float, size: Float, maxW: Float) {
        paint.textSize = size
        val w = paint.measureText(s)
        if (w > maxW * 0.85f) paint.textSize = size * maxW * 0.85f / w
        c.drawText(s, cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
    }

    private fun drawShift(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s); path.lineTo(x + s, y); path.lineTo(x + s * .45f, y); path.lineTo(x + s * .45f, y + s * .8f)
        path.lineTo(x - s * .45f, y + s * .8f); path.lineTo(x - s * .45f, y); path.lineTo(x - s, y); path.close()
        ip.style = if (shift != 0) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        c.drawPath(path, ip)
        if (shift == 2) { ip.style = Paint.Style.STROKE; c.drawLine(x - s * .45f, y + s * 1.2f, x + s * .45f, y + s * 1.2f, ip) }
    }
    private fun drawDel(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x - s * 1.2f, y); path.lineTo(x - s * .5f, y - s * .8f); path.lineTo(x + s * 1.1f, y - s * .8f)
        path.lineTo(x + s * 1.1f, y + s * .8f); path.lineTo(x - s * .5f, y + s * .8f); path.close()
        c.drawPath(path, ip)
        c.drawLine(x - s * .1f, y - s * .35f, x + s * .6f, y + s * .35f, ip)
        c.drawLine(x - s * .1f, y + s * .35f, x + s * .6f, y - s * .35f, ip)
    }
    private fun drawEnter(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x + s, y - s * .8f); path.lineTo(x + s, y); path.lineTo(x - s, y)
        path.moveTo(x - s * .35f, y - s * .6f); path.lineTo(x - s, y); path.lineTo(x - s * .35f, y + s * .6f)
        c.drawPath(path, ip)
    }
    private fun drawSmiley(c: Canvas, x: Float, y: Float, s: Float) {
        c.drawCircle(x, y, s, ip)
        paint.style = Paint.Style.FILL
        c.drawCircle(x - s * .35f, y - s * .25f, s * .1f, paint); c.drawCircle(x + s * .35f, y - s * .25f, s * .1f, paint)
        rect.set(x - s * .55f, y - s * .5f, x + s * .55f, y + s * .6f); c.drawArc(rect, 30f, 120f, false, ip)
    }
    private fun drawGlobe(c: Canvas, x: Float, y: Float, s: Float) {
        c.drawCircle(x, y, s, ip)
        rect.set(x - s * .45f, y - s, x + s * .45f, y + s); c.drawOval(rect, ip)
        c.drawLine(x - s, y, x + s, y, ip)
    }

    // ---------------- touch ----------------

    private fun keyAt(x: Float, y: Float): Key? {
        var best: Key? = null; var bd = Float.MAX_VALUE
        for (k in allKeys) {
            if (y < k.y || y >= k.y + k.h) continue
            if (x >= k.x && x < k.x + k.w) return k
            val d = if (x < k.x) k.x - x else x - (k.x + k.w)
            if (d < bd) { bd = d; best = k }
        }
        return if (bd < th.gapH + th.sidePad + 12f) best else null
    }

    private fun inval(k: Key) = invalidate(k.x.toInt() - 2, k.y.toInt() - 2, (k.x + k.w).toInt() + 2, (k.y + k.h).toInt() + 6)

    private fun press(p: P, k: Key, x: Float, y: Float) {
        p.key = k; k.pressed = true; inval(k)
        feedback.tick()
        when {
            k.token == "{del}" -> {
                listener?.onToken("{del}")
                p.rep = object : Runnable { override fun run() { listener?.onToken("{del}"); h.postDelayed(this, th.repeatMs) } }
                h.postDelayed(p.rep!!, th.repeatStart)
            }
            !k.special -> {
                if (th.preview && k.popups.isEmpty() || (th.preview && !p.popup)) showPreview(k)
                if (k.popups.isNotEmpty()) {
                    p.lp = Runnable { p.lp = null; if (p.key === k) { feedback.tick(true); showPopup(p, k, p.lastX) } }
                    h.postDelayed(p.lp!!, th.longPressMs)
                }
            }
            k.token == "{space}" || k.token == "{emoji}" || k.token == "{lang}" -> {
                p.lp = Runnable { p.lp = null; if (p.key === k && !p.swiped) { feedback.tick(true); p.swiped = true; listener?.onToken(k.token + "_long") } }
                h.postDelayed(p.lp!!, th.longPressMs + 200)
            }
        }
    }

    private fun release(p: P) {
        p.lp?.let { h.removeCallbacks(it) }; p.rep?.let { h.removeCallbacks(it) }; p.lp = null; p.rep = null
        p.key?.let { it.pressed = false; inval(it) }
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
        val v = ensurePop(); v.th = th; v.items = listOf(caseOf(k.label)); v.sel = -1
        v.itemW = max(th.previewSize, k.w * 0.9f); v.itemH = th.previewSize * 1.1f
        v.measure(0, 0)
        placePopup(v, k.x + k.w / 2 - v.itemW / 2, k.y - v.itemH - th.gapV)
    }

    private fun showPopup(p: P, k: Key, x: Float) {
        val v = ensurePop(); v.th = th; v.items = k.popups.map { caseOf(it) }
        val n = v.items.size
        v.itemW = min(max(th.previewSize * 0.8f, k.w), width.toFloat() / n); v.itemH = th.previewSize * 1.1f
        v.measure(0, 0)
        val total = v.itemW * n
        val left = (k.x + k.w / 2 - total / 2).coerceIn(0f, max(0f, width - total))
        popLeft = left; popItemW = v.itemW
        p.popup = true
        v.sel = ((x - left) / v.itemW).toInt().coerceIn(0, n - 1); p.sel = v.sel
        placePopup(v, left, k.y - v.itemH - th.gapV)
    }

    private fun updatePopupSel(p: P, x: Float) {
        val v = popView ?: return
        val s = ((x - popLeft) / popItemW).toInt().coerceIn(0, v.items.size - 1)
        if (s != p.sel) { p.sel = s; v.sel = s; v.invalidate(); feedback.tick(true) }
    }

    private fun placePopup(v: PopupView, x: Float, y: Float) {
        val loc = IntArray(2); getLocationOnScreen(loc)
        val px = (loc[0] + x).toInt(); val py = (loc[1] + y).toInt()
        val w = v.measuredWidth; val hh = v.measuredHeight
        val pw = pop ?: return
        try {
            if (pw.isShowing) pw.update(px, py, w, hh) else { pw.width = w; pw.height = hh; pw.showAtLocation(this, Gravity.NO_GRAVITY, px, py) }
            v.invalidate()
        } catch (_: Exception) {}
    }

    private fun hidePopup() { try { pop?.dismiss() } catch (_: Exception) {} }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (i in 0 until ptrs.size()) release(ptrs.valueAt(i)); ptrs.clear(); hidePopup()
    }
}
