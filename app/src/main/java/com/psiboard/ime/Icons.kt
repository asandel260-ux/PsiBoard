package com.psiboard.ime

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** One consistent set of stroke icons, shared by keys and toolbar. Caller sets paint color + strokeWidth. */
object Glyphs {
    fun draw(c: Canvas, kind: String, cx: Float, cy: Float, s: Float, p: Paint, r: RectF, path: Path) {
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        when (kind) {
            "shift", "shift_on", "caps" -> {
                path.reset()
                path.moveTo(cx, cy - s); path.lineTo(cx + s * 1.0f, cy + s * .05f); path.lineTo(cx + s * .45f, cy + s * .05f)
                path.lineTo(cx + s * .45f, cy + s * .85f); path.lineTo(cx - s * .45f, cy + s * .85f); path.lineTo(cx - s * .45f, cy + s * .05f)
                path.lineTo(cx - s, cy + s * .05f); path.close()
                p.style = if (kind == "shift") Paint.Style.STROKE else Paint.Style.FILL_AND_STROKE
                c.drawPath(path, p); p.style = Paint.Style.STROKE
                if (kind == "caps") c.drawLine(cx - s * .45f, cy + s * 1.3f, cx + s * .45f, cy + s * 1.3f, p)
            }
            "del" -> {
                path.reset()
                path.moveTo(cx - s * 1.25f, cy); path.lineTo(cx - s * .55f, cy - s * .85f); path.lineTo(cx + s * 1.15f, cy - s * .85f)
                path.lineTo(cx + s * 1.15f, cy + s * .85f); path.lineTo(cx - s * .55f, cy + s * .85f); path.close()
                c.drawPath(path, p)
                c.drawLine(cx - s * .05f, cy - s * .38f, cx + s * .65f, cy + s * .38f, p); c.drawLine(cx - s * .05f, cy + s * .38f, cx + s * .65f, cy - s * .38f, p)
            }
            "enter" -> {
                path.reset()
                path.moveTo(cx + s, cy - s * .8f); path.lineTo(cx + s, cy + s * .05f); path.lineTo(cx - s * .9f, cy + s * .05f)
                path.moveTo(cx - s * .35f, cy - s * .55f); path.lineTo(cx - s * .95f, cy + s * .05f); path.lineTo(cx - s * .35f, cy + s * .65f)
                c.drawPath(path, p)
            }
            "emoji" -> {
                c.drawCircle(cx, cy, s, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx - s * .36f, cy - s * .25f, s * .12f, p); c.drawCircle(cx + s * .36f, cy - s * .25f, s * .12f, p)
                p.style = Paint.Style.STROKE
                r.set(cx - s * .55f, cy - s * .45f, cx + s * .55f, cy + s * .55f); c.drawArc(r, 25f, 130f, false, p)
            }
            "globe" -> {
                c.drawCircle(cx, cy, s, p)
                r.set(cx - s * .42f, cy - s, cx + s * .42f, cy + s); c.drawOval(r, p)
                c.drawLine(cx - s, cy, cx + s, cy, p)
                c.drawLine(cx - s * .86f, cy - s * .5f, cx + s * .86f, cy - s * .5f, p); c.drawLine(cx - s * .86f, cy + s * .5f, cx + s * .86f, cy + s * .5f, p)
            }
            "clip" -> {
                r.set(cx - s * .8f, cy - s * .85f, cx + s * .8f, cy + s); c.drawRoundRect(r, s * .22f, s * .22f, p)
                r.set(cx - s * .38f, cy - s * 1.15f, cx + s * .38f, cy - s * .6f); c.drawRoundRect(r, s * .18f, s * .18f, p)
                c.drawLine(cx - s * .4f, cy + s * .0f, cx + s * .4f, cy + s * .0f, p); c.drawLine(cx - s * .4f, cy + s * .45f, cx + s * .15f, cy + s * .45f, p)
            }
            "left" -> { path.reset(); path.moveTo(cx + s * .35f, cy - s * .75f); path.lineTo(cx - s * .4f, cy); path.lineTo(cx + s * .35f, cy + s * .75f); c.drawPath(path, p) }
            "right" -> { path.reset(); path.moveTo(cx - s * .35f, cy - s * .75f); path.lineTo(cx + s * .4f, cy); path.lineTo(cx - s * .35f, cy + s * .75f); c.drawPath(path, p) }
            "all" -> {
                p.pathEffect = DashPathEffect(floatArrayOf(s * .45f, s * .35f), 0f)
                r.set(cx - s, cy - s * .85f, cx + s, cy + s * .85f); c.drawRoundRect(r, s * .3f, s * .3f, p); p.pathEffect = null
                path.reset(); path.moveTo(cx - s * .38f, cy + s * .4f); path.lineTo(cx, cy - s * .45f); path.lineTo(cx + s * .38f, cy + s * .4f)
                c.drawPath(path, p); c.drawLine(cx - s * .22f, cy + s * .12f, cx + s * .22f, cy + s * .12f, p)
            }
            "copy" -> {
                r.set(cx - s * .15f, cy - s * .15f, cx + s * .9f, cy + s); c.drawRoundRect(r, s * .22f, s * .22f, p)
                path.reset(); path.moveTo(cx - s * .45f, cy + s * .5f); path.lineTo(cx - s * .65f, cy + s * .5f)
                path.quadTo(cx - s * .9f, cy + s * .5f, cx - s * .9f, cy + s * .25f); path.lineTo(cx - s * .9f, cy - s * .65f)
                path.quadTo(cx - s * .9f, cy - s * .9f, cx - s * .65f, cy - s * .9f); path.lineTo(cx + s * .2f, cy - s * .9f)
                path.quadTo(cx + s * .45f, cy - s * .9f, cx + s * .45f, cy - s * .65f); path.lineTo(cx + s * .45f, cy - s * .5f)
                c.drawPath(path, p)
            }
            "paste" -> {
                c.drawLine(cx, cy - s, cx, cy + s * .3f, p)
                path.reset(); path.moveTo(cx - s * .55f, cy - s * .25f); path.lineTo(cx, cy + s * .35f); path.lineTo(cx + s * .55f, cy - s * .25f); c.drawPath(path, p)
                c.drawLine(cx - s * .85f, cy + s * .95f, cx + s * .85f, cy + s * .95f, p)
            }
            "tune" -> {
                val ys = floatArrayOf(-.7f, 0f, .7f); val ks = floatArrayOf(.35f, -.4f, .45f)
                for (i in 0..2) {
                    c.drawLine(cx - s, cy + ys[i] * s, cx + s, cy + ys[i] * s, p)
                    p.style = Paint.Style.FILL; c.drawCircle(cx + ks[i] * s, cy + ys[i] * s, s * .26f, p); p.style = Paint.Style.STROKE
                }
            }
            "zwnj" -> {
                // half-space (ZWNJ): a narrow "space" bracket split in two by a short divider
                path.reset()
                path.moveTo(cx - s * .8f, cy - s * .4f); path.lineTo(cx - s * .8f, cy + s * .5f)
                path.lineTo(cx + s * .8f, cy + s * .5f); path.lineTo(cx + s * .8f, cy - s * .4f)
                c.drawPath(path, p)
                c.drawLine(cx, cy - s * .4f, cx, cy + s * .5f, p)
            }
            "onehand" -> {
                c.drawLine(cx - s, cy - s * .4f, cx + s, cy - s * .4f, p); c.drawLine(cx + s * .55f, cy - s * .8f, cx + s, cy - s * .4f, p); c.drawLine(cx + s * .55f, cy, cx + s, cy - s * .4f, p)
                c.drawLine(cx + s, cy + s * .5f, cx - s, cy + s * .5f, p); c.drawLine(cx - s * .55f, cy + s * .1f, cx - s, cy + s * .5f, p); c.drawLine(cx - s * .55f, cy + s * .9f, cx - s, cy + s * .5f, p)
            }
        }
        p.style = Paint.Style.STROKE
    }
}

/** Toolbar button. Icon is dp-sized (≈20dp glyph, 1.9dp stroke, like Material's 24dp icons); pressed state is a soft round "state layer". */
class IconView(ctx: Context, private val kind: String, private val tint: Int, private val onClick: () -> Unit) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
    private val r = RectF(); private val path = Path()
    private var down = false
    init { isClickable = true; setOnClickListener { onClick() } }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = true; invalidate() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { down = false; invalidate() }
        }
        return super.onTouchEvent(e)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        if (down) {
            p.style = Paint.Style.FILL; p.color = (tint and 0x00FFFFFF) or 0x24000000
            c.drawCircle(cx, cy, minOf(width, height) * 0.46f, p)
        }
        val s = minOf(10f * d, minOf(width, height) * 0.3f)
        p.color = tint; p.strokeWidth = maxOf(1.4f * d, s * 0.19f)
        Glyphs.draw(c, kind, cx, cy, s, p, r, path)
    }
}
