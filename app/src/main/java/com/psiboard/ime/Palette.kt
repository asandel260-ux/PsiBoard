package com.psiboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Colour science used by the theme engine.
 *  - Oklab / OKLCH (Ottosson, 2020): lightness steps are perceptually even, so tonal surfaces look evenly spaced.
 *  - WCAG 2.x relative luminance contrast: every text/icon colour is nudged until it meets its target ratio.
 */
object Ok {
    private fun toLin(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    private fun toSrgb(c: Float) = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f
    private fun cbrt(x: Float) = Math.cbrt(x.toDouble()).toFloat()

    /** ARGB -> [L, C, h(deg)] */
    fun fromArgb(argb: Int): FloatArray {
        val r = toLin(Color.red(argb) / 255f); val g = toLin(Color.green(argb) / 255f); val b = toLin(Color.blue(argb) / 255f)
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        val L = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        var h = Math.toDegrees(atan2(bb.toDouble(), a.toDouble())).toFloat(); if (h < 0) h += 360f
        return floatArrayOf(L, sqrt(a * a + bb * bb), h)
    }

    private fun linear(L: Float, C: Float, h: Float): FloatArray? {
        val hr = Math.toRadians(h.toDouble()); val a = (C * cos(hr)).toFloat(); val b = (C * sin(hr)).toFloat()
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b
        val l = l_ * l_ * l_; val m = m_ * m_ * m_; val s = s_ * s_ * s_
        val r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        val g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        val bl = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
        val e = 0.0005f
        return if (r < -e || r > 1 + e || g < -e || g > 1 + e || bl < -e || bl > 1 + e) null else floatArrayOf(r, g, bl)
    }

    private fun pack(rgb: FloatArray, alpha: Int): Int {
        fun ch(v: Float) = (toSrgb(v.coerceIn(0f, 1f)) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return Color.argb(alpha, ch(rgb[0]), ch(rgb[1]), ch(rgb[2]))
    }

    /** OKLCH -> ARGB. Out-of-gamut chroma is reduced (binary search) while keeping lightness and hue. */
    fun argb(L: Float, C: Float, h: Float, alpha: Int = 255): Int {
        val l = L.coerceIn(0f, 1f)
        if (l <= 0f) return Color.argb(alpha, 0, 0, 0)
        if (l >= 1f) return Color.argb(alpha, 255, 255, 255)
        linear(l, C, h)?.let { return pack(it, alpha) }
        var lo = 0f; var hi = C
        repeat(14) { val mid = (lo + hi) / 2; if (linear(l, mid, h) != null) lo = mid else hi = mid }
        return pack(linear(l, lo, h) ?: floatArrayOf(l, l, l), alpha)
    }

    fun luminance(argb: Int): Float {
        val r = toLin(Color.red(argb) / 255f); val g = toLin(Color.green(argb) / 255f); val b = toLin(Color.blue(argb) / 255f)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** WCAG 2.x contrast ratio, 1..21 */
    fun contrast(a: Int, b: Int): Float {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** Moves lightness of (C,h) away from [bg] in OKLCH steps until [ratio] is met. */
    fun withContrast(L: Float, C: Float, h: Float, bg: Int, ratio: Float): Int {
        val lighten = luminance(bg) < 0.18f
        var l = L; var out = argb(l, C, h)
        var i = 0
        while (contrast(out, bg) < ratio && i++ < 60) { l += if (lighten) 0.01f else -0.01f; out = argb(l, C, h) }
        return out
    }

    fun mix(a: Int, b: Int, f: Float) = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(), (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt())
}

/**
 * Tone-based colour scheme (same idea as Material 3 surface containers): one hue, a few lightness steps.
 *  letters = lightest key tone, function keys = tinted + one step lower, accent = high-chroma primary.
 */
object Palette {
    /** [hue, accentChroma] from the seed colour, or from the wallpaper palette on Android 12+. */
    fun seed(ctx: Context): FloatArray {
        if (Settings.bool("dynamic_color") && Build.VERSION.SDK_INT >= 31) {
            try {
                val t = Ok.fromArgb(ctx.getColor(android.R.color.system_accent1_200))
                return floatArrayOf(t[2], t[1])
            } catch (_: Exception) {}
        }
        val s = Ok.fromArgb(Settings.int("seed_color"))
        return floatArrayOf(s[2], s[1])
    }

    fun isNight(ctx: Context) = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun build(dark: Boolean, hue: Float, seedChroma: Float, tint: Float, amoled: Boolean, contrast: Float): Map<String, Int> {
        val h = hue
        val cn = 0.014f * tint              // neutral chroma (barely-there tint)
        val cs = 0.034f * tint              // tonal chroma for function keys / pressed states
        val ca = seedChroma.coerceIn(0.10f, 0.17f)
        val d = 0.105f + (contrast - 0.5f) * 0.07f   // lightness gap between background and letter keys
        val m = HashMap<String, Int>()
        if (dark) {
            val bgL = if (amoled) 0f else 0.185f
            val bg = Ok.argb(bgL, if (amoled) 0f else cn, h)
            val keyL = if (amoled) 0.225f + (contrast - 0.5f) * 0.05f else bgL + d
            val spL = if (amoled) keyL - 0.06f else bgL + d * 0.55f
            val key = Ok.argb(keyL, cn * 1.1f, h)
            val special = Ok.argb(spL, cs, h)
            val accent = Ok.argb(0.80f, ca, h)
            m["c_bg"] = bg; m["c_bg2"] = if (amoled) bg else Ok.argb(bgL - 0.03f, cn, h)
            m["c_key"] = key; m["c_key_pressed"] = Ok.argb(keyL + 0.12f, cs * 1.4f, h)
            m["c_special"] = special; m["c_special_pressed"] = Ok.argb(spL + 0.12f, cs * 1.4f, h)
            m["c_accent"] = accent
            m["c_text"] = Ok.withContrast(0.94f, 0.008f, h, key, 9f)
            m["c_text_special"] = Ok.withContrast(0.88f, cs * 0.7f, h, special, 7f)
            m["c_text_accent"] = Ok.withContrast(0.22f, ca * 0.3f, h, accent, 7f)
            m["c_hint"] = Ok.withContrast(0.72f, 0.02f, h, key, 4.5f)
            m["c_popup_bg"] = Ok.argb(keyL + 0.06f, cs, h)
            m["c_shadow"] = Color.argb(150, 0, 0, 0)
        } else {
            val bg = Ok.argb(0.925f, cn * 0.5f, h)
            val key = Ok.argb(0.995f, cn * 0.2f, h)
            val special = Ok.argb(0.845f - (d - 0.105f), cs * 0.8f, h)
            val accent = Ok.argb(0.50f, ca * 1.05f, h)
            m["c_bg"] = bg; m["c_bg2"] = Ok.argb(0.90f, cn * 0.5f, h)
            m["c_key"] = key; m["c_key_pressed"] = Ok.argb(0.83f, cs * 1.2f, h)
            m["c_special"] = special; m["c_special_pressed"] = Ok.argb(0.74f, cs, h)
            m["c_accent"] = accent
            m["c_text"] = Ok.withContrast(0.22f, 0.01f, h, key, 9f)
            m["c_text_special"] = Ok.withContrast(0.30f, cs * 0.6f, h, special, 7f)
            m["c_text_accent"] = Ok.withContrast(0.99f, 0.01f, h, accent, 4.5f)
            m["c_hint"] = Ok.withContrast(0.50f, 0.02f, h, key, 4.5f)
            m["c_popup_bg"] = Color.WHITE
            m["c_shadow"] = Color.argb(56, 0, 0, 0)
        }
        m["c_border"] = 0
        m["c_popup_sel"] = m["c_accent"]!!
        m["c_popup_text"] = m["c_text"]!!
        m["c_strip_bg"] = m["c_bg"]!!
        m["c_strip_text"] = m["c_text"]!!
        m["c_strip_icon"] = m["c_text_special"]!!
        m["c_nav"] = m["c_bg"]!!
        m["c_clip_card"] = m["c_key"]!!
        m["c_clip_text"] = m["c_text"]!!
        m["c_clip_pin"] = Ok.argb(if (dark) 0.85f else 0.65f, 0.13f, 85f)
        m["c_clip_time"] = m["c_hint"]!!
        return m
    }
}
