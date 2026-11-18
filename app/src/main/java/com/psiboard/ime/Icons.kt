package com.psiboard.ime

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Crisp vector toolbar icons drawn with Canvas, tinted by the theme. */
class IconView(ctx: Context, private val kind: String, private val tint: Int, private val onClick: () -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = tint }
    private val r = RectF(); private val path = Path()
    init { isClickable = true; setOnClickListener { onClick() } }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) { MotionEvent.ACTION_DOWN -> alpha = 0.45f; MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> alpha = 1f }
        return super.onTouchEvent(e)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val s = minOf(width, height) * 0.27f
        p.strokeWidth = s * 0.2f; p.style = Paint.Style.STROKE
        when (kind) {
            "clip" -> { r.set(cx - s * .8f, cy - s, cx + s * .8f, cy + s); c.drawRoundRect(r, s * .2f, s * .2f, p)
                r.set(cx - s * .35f, cy - s * 1.2f, cx + s * .35f, cy - s * .7f); c.drawRoundRect(r, s * .15f, s * .15f, p)
                c.drawLine(cx - s * .4f, cy - s * .1f, cx + s * .4f, cy - s * .1f, p); c.drawLine(cx - s * .4f, cy + s * .4f, cx + s * .2f, cy + s * .4f, p) }
            "emoji" -> { c.drawCircle(cx, cy, s, p); p.style = Paint.Style.FILL
                c.drawCircle(cx - s * .35f, cy - s * .25f, s * .1f, p); c.drawCircle(cx + s * .35f, cy - s * .25f, s * .1f, p); p.style = Paint.Style.STROKE
                r.set(cx - s * .55f, cy - s * .5f, cx + s * .55f, cy + s * .6f); c.drawArc(r, 30f, 120f, false, p) }
            "globe" -> { c.drawCircle(cx, cy, s, p); r.set(cx - s * .45f, cy - s, cx + s * .45f, cy + s); c.drawOval(r, p)
                c.drawLine(cx - s, cy, cx + s, cy, p) }
            "left" -> { path.reset(); path.moveTo(cx + s * .4f, cy - s * .7f); path.lineTo(cx - s * .4f, cy); path.lineTo(cx + s * .4f, cy + s * .7f); c.drawPath(path, p) }
            "right" -> { path.reset(); path.moveTo(cx - s * .4f, cy - s * .7f); path.lineTo(cx + s * .4f, cy); path.lineTo(cx - s * .4f, cy + s * .7f); c.drawPath(path, p) }
            "all" -> { p.pathEffect = DashPathEffect(floatArrayOf(s * .4f, s * .3f), 0f); r.set(cx - s, cy - s * .8f, cx + s, cy + s * .8f); c.drawRoundRect(r, s * .25f, s * .25f, p); p.pathEffect = null
                p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.textSize = s * 1.1f; p.typeface = Typeface.DEFAULT_BOLD
                c.drawText("A", cx, cy - (p.ascent() + p.descent()) / 2, p); p.typeface = Typeface.DEFAULT }
            "copy" -> { r.set(cx - s * .2f, cy - s * .2f, cx + s * .9f, cy + s); c.drawRoundRect(r, s * .2f, s * .2f, p)
                path.reset(); path.moveTo(cx - s * .5f, cy + s * .45f); path.lineTo(cx - s * .9f, cy + s * .45f); path.lineTo(cx - s * .9f, cy - s * .9f); path.lineTo(cx + s * .3f, cy - s * .9f); path.lineTo(cx + s * .3f, cy - s * .5f); c.drawPath(path, p) }
            "paste" -> { c.drawLine(cx, cy - s, cx, cy + s * .35f, p); path.reset(); path.moveTo(cx - s * .5f, cy - s * .15f); path.lineTo(cx, cy + s * .4f); path.lineTo(cx + s * .5f, cy - s * .15f); c.drawPath(path, p)
                c.drawLine(cx - s * .8f, cy + s, cx + s * .8f, cy + s, p) }
            "gear" -> { c.drawCircle(cx, cy, s * .38f, p); c.drawCircle(cx, cy, s * .75f, p)
                for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); c.drawLine(cx + (s * .75f * Math.cos(a)).toFloat(), cy + (s * .75f * Math.sin(a)).toFloat(), cx + (s * 1.05f * Math.cos(a)).toFloat(), cy + (s * 1.05f * Math.sin(a)).toFloat(), p) } }
            "onehand" -> { c.drawLine(cx - s, cy - s * .35f, cx + s, cy - s * .35f, p); c.drawLine(cx + s * .6f, cy - s * .75f, cx + s, cy - s * .35f, p); c.drawLine(cx + s * .6f, cy + 0.05f * s, cx + s, cy - s * .35f, p)
                c.drawLine(cx + s, cy + s * .55f, cx - s, cy + s * .55f, p); c.drawLine(cx - s * .6f, cy + s * .15f, cx - s, cy + s * .55f, p); c.drawLine(cx - s * .6f, cy + s * .95f, cx - s, cy + s * .55f, p) }
        }
    }
}
