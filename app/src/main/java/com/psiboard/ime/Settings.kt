package com.psiboard.ime

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Single source of truth for EVERY setting. The settings screen is generated from this list,
 * so adding an entry here automatically adds it to the UI, to import/export and to defaults.
 */
sealed class S(val key: String, val title: String, val section: String, val hint: String = "")
class SwitchS(key: String, title: String, section: String, val def: Boolean, hint: String = "") : S(key, title, section, hint)
class SliderS(key: String, title: String, section: String, val min: Int, val max: Int, val def: Int, val unit: String = "", hint: String = "") : S(key, title, section, hint)
class ColorS(key: String, title: String, section: String, val def: Int) : S(key, title, section)
class ChoiceS(key: String, title: String, section: String, val options: List<String>, val def: String) : S(key, title, section)
class TextS(key: String, title: String, section: String, val def: String, val multiline: Boolean = false, hint: String = "") : S(key, title, section, hint)

object Settings {
    private const val A0 = "Theme"
    private const val A = "Color overrides (advanced)"
    private const val B = "Appearance: shape & size"
    private const val C = "Layout"
    private const val D = "Typing"
    private const val E = "Feedback"
    private const val F = "Suggestions"
    private const val G = "Clipboard"
    private const val H = "Languages & layouts"
    private const val I = "Toolbar"

    val fonts = listOf("auto", "sans", "sans-medium", "sans-bold", "light", "condensed", "serif", "mono", "italic", "vazirmatn")
    val caseModes = listOf("auto", "lower", "upper")
    val swipeActions = listOf("none", "cursor", "delete-word")
    val langs = listOf("en", "fa")

    val all: List<S> = listOf(
        ChoiceS("preset", "Theme preset", A0, Presets.names(), "Default"),
        ChoiceS("theme_mode", "Light / dark", A0, listOf("auto", "dark", "light"), "auto"),
        SwitchS("dynamic_color", "Use wallpaper colors (Material You, Android 12+)", A0, false),
        ColorS("seed_color", "Theme color (seed)", A0, 0xFF7B8CFF.toInt()),
        SliderS("tint", "Surface tint strength", A0, 0, 200, 100, "%", "How much of the theme color tints the grey surfaces"),
        SliderS("contrast", "Key vs background contrast", A0, 0, 100, 50, "%", "Text always keeps at least WCAG 4.5:1 contrast"),
        SwitchS("amoled", "True black background (dark mode)", A0, false),
        ColorS("c_bg", "Keyboard background", A, 0xFF14141A.toInt()),
        SwitchS("bg_gradient", "Background gradient", A, true),
        ColorS("c_bg2", "Gradient end color", A, 0xFF0C0C11.toInt()),
        ColorS("c_key", "Key", A, 0xFF2A2A34.toInt()),
        ColorS("c_key_pressed", "Key pressed", A, 0xFF4B4D66.toInt()),
        ColorS("c_special", "Special key (shift, del, ...)", A, 0xFF1D1D25.toInt()),
        ColorS("c_special_pressed", "Special key pressed", A, 0xFF383A50.toInt()),
        ColorS("c_accent", "Enter / accent key", A, 0xFF7C9BFF.toInt()),
        ColorS("c_text", "Key text", A, 0xFFF1F1F6.toInt()),
        ColorS("c_text_special", "Special key text / icons", A, 0xFFB4B6C8.toInt()),
        ColorS("c_text_accent", "Accent key text", A, 0xFFFFFFFF.toInt()),
        ColorS("c_hint", "Long-press hint text", A, 0xFF8A8A98.toInt()),
        ColorS("c_border", "Key border", A, 0x00000000),
        ColorS("c_shadow", "Key shadow", A, 0x55000000),
        ColorS("c_popup_bg", "Popup background", A, 0xFF353643.toInt()),
        ColorS("c_popup_sel", "Popup selected item", A, 0xFF7C9BFF.toInt()),
        ColorS("c_popup_text", "Popup text", A, 0xFFFFFFFF.toInt()),
        ColorS("c_strip_bg", "Suggestion/toolbar background", A, 0xFF14141A.toInt()),
        ColorS("c_strip_text", "Suggestion text", A, 0xFFF1F1F6.toInt()),
        ColorS("c_strip_icon", "Toolbar icons", A, 0xFFB4B6C8.toInt()),
        ColorS("c_nav", "Navigation bar tint (bottom padding)", A, 0xFF14141A.toInt()),

        SliderS("key_height", "Row height", B, 36, 80, 48, "dp", "48dp = Material's minimum touch target (about 9 mm)"),
        SliderS("key_gap_h", "Horizontal gap between keys", B, 0, 14, 3, "dp"),
        SliderS("key_gap_v", "Vertical gap between rows", B, 0, 14, 7, "dp"),
        SliderS("key_radius", "Key corner radius", B, 0, 30, 9, "dp"),
        SliderS("key_border_w", "Key border width", B, 0, 6, 0, "dp"),
        SliderS("key_highlight", "Key top-edge highlight", B, -1, 100, -1, "%", "Leftmost position = Auto (adapts to light / dark)"),
        SliderS("key_shadow", "Key shadow offset", B, -1, 6, -1, "dp", "Leftmost position = Auto (shadow in light mode, none in dark)"),
        SliderS("text_size", "Key text size", B, 12, 34, 21, "sp"),
        SliderS("special_text_size", "Special key text size", B, 10, 28, 14, "sp"),
        SliderS("hint_size", "Hint text size", B, 6, 16, 9, "sp"),
        SliderS("text_opacity", "Key text opacity", B, 20, 100, 100, "%"),
        SliderS("key_opacity", "Key opacity", B, 0, 100, 100, "%"),
        SliderS("kb_opacity", "Keyboard background opacity", B, 0, 100, 100, "%"),
        SliderS("side_pad", "Side padding", B, 0, 24, 3, "dp"),
        SliderS("bottom_pad", "Bottom padding", B, 0, 60, 6, "dp"),
        SliderS("top_pad", "Top padding", B, 0, 20, 4, "dp"),
        ChoiceS("font_en", "Latin key font", B, fonts, "auto"),
        ChoiceS("font_fa", "Persian key font", B, fonts, "auto"),
        SliderS("fa_text_scale", "Persian text size", B, 70, 130, 92, "%"),
        ChoiceS("label_case", "Letter case on keys", B, caseModes, "auto"),
        SwitchS("show_hints", "Show long-press hints on keys", B, true),
        SwitchS("key_preview", "Key preview bubble", B, true),
        SliderS("preview_size", "Preview bubble size", B, 36, 100, 56, "dp"),
        SliderS("popup_radius", "Popup corner radius", B, 0, 24, 12, "dp"),

        SliderS("height_scale", "Overall keyboard height scale", C, 60, 160, 100, "%"),
        SwitchS("number_row", "Number row", C, true),
        SwitchS("one_handed", "One-handed mode", C, false),
        ChoiceS("one_handed_side", "One-handed side", C, listOf("left", "right"), "right"),
        SliderS("one_handed_width", "One-handed width", C, 50, 90, 75, "%"),
        SwitchS("floating_landscape_split", "Split keyboard in landscape", C, false),
        SliderS("landscape_height_scale", "Landscape height scale", C, 50, 130, 85, "%"),
        SwitchS("show_space_label", "Language name on space bar", C, true),
        SwitchS("show_symbols_key", "Symbols key on bottom row", C, true),
        SwitchS("show_emoji_key", "Emoji key on bottom row", C, true),
        SwitchS("show_comma_period", "Comma and period keys on bottom row", C, true),
        SwitchS("enter_icons", "Icons on the Enter key (instead of words like Next / Search)", C, true),
        SliderS("space_weight", "Space bar width", C, 20, 60, 40, "%"),

        SwitchS("auto_cap", "Auto-capitalize sentences", D, true),
        SwitchS("double_space_period", "Double space inserts period", D, true),
        SwitchS("auto_space_after_punct", "Auto space after punctuation from suggestions", D, true),
        SwitchS("caps_double_tap", "Double-tap shift = caps lock", D, true),
        SwitchS("shift_cursor_select", "Hold shift + swipe space = select text", D, true),
        ChoiceS("space_swipe", "Swipe on space bar", D, swipeActions, "cursor"),
        ChoiceS("delete_swipe", "Swipe left on delete", D, swipeActions, "delete-word"),
        SliderS("touch_y_offset", "Touch point correction (up)", D, -8, 12, 2, "dp", "Fingers land slightly below the intended key (Henze et al.); this shifts where a tap is read"),
        SliderS("longpress_ms", "Long-press delay", D, 150, 800, 300, "ms"),
        SliderS("repeat_start_ms", "Key repeat start delay", D, 100, 800, 350, "ms"),
        SliderS("repeat_ms", "Key repeat interval", D, 15, 150, 45, "ms"),
        SliderS("swipe_step", "Cursor swipe sensitivity (px/char)", D, 8, 60, 22, "px"),
        SwitchS("enter_sends_action", "Enter performs editor action (Go/Search/Send)", D, true),
        SwitchS("password_safe", "Disable learning/clipboard in password fields", D, true),
        SwitchS("incognito", "Incognito (never learn words)", D, false),
        TextS("long_press_extra", "Extra long-press symbols for letters (letter=chars per line)", D, "", true, "Example:\na=àáâä\ne=éèê"),

        SwitchS("haptic", "Vibrate on key press", E, true),
        ChoiceS("haptic_style", "Haptic style", E, listOf("system", "custom"), "system"),
        SliderS("haptic_ms", "Vibration strength/length", E, 1, 40, 12, "ms"),
        SwitchS("haptic_longpress", "Vibrate on long-press", E, true),
        SwitchS("sound", "Sound on key press", E, false),
        SliderS("sound_vol", "Sound volume", E, 0, 100, 40, "%"),

        SwitchS("suggest", "Show suggestions", F, true),
        SwitchS("autocorrect", "Auto-correct on space", F, false),
        SliderS("suggest_count", "Number of suggestions", F, 1, 5, 3),
        SwitchS("learn_words", "Learn new words", F, true),
        SwitchS("next_word", "Next-word suggestions (from your history)", F, true),
        SliderS("strip_height", "Suggestion/toolbar strip height", F, 32, 64, 40, "dp"),
        SliderS("strip_text_size", "Suggestion text size", F, 11, 24, 17, "sp"),
        TextS("shortcuts", "Text shortcuts (trigger=expansion per line)", F, "omw=on my way\nbrb=be right back", true),
        SwitchS("expand_shortcuts", "Expand text shortcuts on space", F, true),

        SwitchS("clip_enabled", "Clipboard history", G, true),
        SliderS("clip_max", "Max history items", G, 5, 500, 60),
        SliderS("clip_hours", "Delete unpinned after (0 = never)", G, 0, 720, 24, "h"),
        SwitchS("clip_clear_on_boot", "Clear unpinned items on reboot", G, false),
        SwitchS("clip_dedupe", "Merge duplicates (move to top)", G, true),
        SwitchS("clip_skip_sensitive", "Ignore items flagged sensitive / from password fields", G, true),
        SwitchS("clip_trim", "Trim whitespace when saving", G, false),
        SliderS("clip_min_len", "Ignore copies shorter than", G, 1, 20, 1, " chars"),
        SliderS("clip_max_len", "Ignore copies longer than", G, 200, 20000, 5000, " chars"),
        SwitchS("clip_suggest", "Offer fresh clip as a suggestion chip", G, true),
        SliderS("clip_suggest_sec", "Suggestion chip stays for", G, 5, 300, 60, "s"),
        SwitchS("clip_paste_plain", "Always paste as plain text", G, true),
        SwitchS("clip_close_after_paste", "Return to keyboard after pasting", G, true),
        SwitchS("clip_pinned_first", "Show pinned items first", G, true),
        SwitchS("clip_show_time", "Show time on items", G, true),
        SwitchS("clip_confirm_clear", "Confirm before 'clear all'", G, true),
        SliderS("clip_columns", "Columns in panel", G, 1, 4, 2),
        SliderS("clip_lines", "Max lines per item", G, 1, 12, 3),
        SliderS("clip_text_size", "Clipboard text size", G, 10, 22, 14, "sp"),
        SliderS("clip_card_radius", "Clip card corner radius", G, 0, 24, 10, "dp"),
        SliderS("clip_card_gap", "Clip card gap", G, 0, 16, 5, "dp"),
        SliderS("clip_panel_height", "Panel height (0 = same as keyboard)", G, 0, 100, 0, "%"),
        ColorS("c_clip_card", "Clip card color", G, 0xFF2A2A34.toInt()),
        ColorS("c_clip_text", "Clip text color", G, 0xFFF1F1F6.toInt()),
        ColorS("c_clip_pin", "Pinned accent color", G, 0xFFFFC857.toInt()),
        ColorS("c_clip_time", "Clip time/meta color", G, 0xFF8A8A98.toInt()),
        TextS("clip_snippets", "Saved snippets (one per line, always available)", G, "", true),
        TextS("clip_blocked_apps", "Never record from apps (package names, one per line)", G, "", true),

        SwitchS("lang_en", "English", H, true),
        SwitchS("lang_fa", "Persian (فارسی)", H, true),
        ChoiceS("default_lang", "Default language", H, langs, "en"),
        SwitchS("fa_digits", "Use Persian digits in Persian layout", H, true),
        TextS("layout_en", "English letters layout (leave empty = default)", H, "", true,
            "Rows on lines. Keys separated by space. a/àá = long-press popups. {shift} {del} etc with *width"),
        TextS("layout_fa", "Persian letters layout (leave empty = default)", H, "", true),
        TextS("layout_sym", "Symbols layout (leave empty = default)", H, "", true),
        TextS("layout_sym2", "Symbols page 2 (leave empty = default)", H, "", true),
        TextS("layout_numrow", "Number row keys (space separated)", H, "1 2 3 4 5 6 7 8 9 0", false),

        SwitchS("tb_show", "Show toolbar when no suggestions", I, true),
        SwitchS("tb_clip", "Clipboard button", I, true),
        SwitchS("tb_emoji", "Emoji button", I, true),
        SwitchS("tb_settings", "Settings button", I, true),
        SwitchS("tb_cursor", "Cursor arrows", I, true),
        SwitchS("tb_select_all", "Select all / copy / paste buttons", I, true),
        SwitchS("tb_onehand", "One-handed toggle", I, false),
        SwitchS("tb_voice", "Switch-keyboard button", I, true),
        SliderS("emoji_cols", "Emoji grid columns", I, 5, 12, 8),
        SliderS("emoji_size", "Emoji size", I, 18, 44, 28, "sp"),
        SliderS("emoji_recent", "Recent emoji count", I, 8, 60, 32)
    )

    private val byKey = all.associateBy { it.key }
    val keys: Set<String> = byKey.keys
    lateinit var sp: SharedPreferences
    fun init(c: Context) {
        if (!::sp.isInitialized) {
            sp = c.applicationContext.getSharedPreferences("psiboard", Context.MODE_PRIVATE)
            // first run on a Persian phone: start in Persian
            if (!sp.contains("default_lang") && java.util.Locale.getDefault().language == "fa") sp.edit().putString("default_lang", "fa").apply()
            refreshDerived(c)
        }
    }

    /** Colours generated by the theme engine; a colour the user set explicitly overrides its generated value. */
    var derived: Map<String, Int> = emptyMap(); private set
    var isDark = true; private set
    fun refreshDerived(ctx: Context) {
        isDark = when (str("theme_mode")) { "dark" -> true; "light" -> false; else -> Palette.isNight(ctx) }
        val s = Palette.seed(ctx)
        derived = Palette.build(isDark, s[0], s[1], int("tint") / 100f, bool("amoled"), int("contrast") / 100f)
    }

    fun bool(k: String): Boolean = sp.getBoolean(k, (byKey[k] as SwitchS).def)
    fun int(k: String): Int = when (val s = byKey[k]) {
        is ColorS -> if (sp.contains(k)) sp.getInt(k, s.def) else (derived[k] ?: s.def)
        is SliderS -> sp.getInt(k, s.def)
        else -> error("not int: $k")
    }
    fun str(k: String): String = when (val s = byKey[k]) {
        is TextS -> sp.getString(k, s.def) ?: s.def
        is ChoiceS -> sp.getString(k, s.def) ?: s.def
        else -> error("not str: $k")
    }

    fun exportJson(): String {
        val o = JSONObject()
        for (s in all) if (sp.contains(s.key)) o.put(s.key, sp.all[s.key])
        return o.toString(2)
    }

    fun importJson(json: String) {
        val o = JSONObject(json)
        val e = sp.edit()
        for (s in all) if (o.has(s.key)) when (s) {
            is SwitchS -> e.putBoolean(s.key, o.getBoolean(s.key))
            is SliderS, is ColorS -> e.putInt(s.key, o.getInt(s.key))
            is ChoiceS, is TextS -> e.putString(s.key, o.getString(s.key))
        }
        e.apply()
    }

    fun resetAll() { sp.edit().clear().apply() }
}

object Presets {
    /** seed = theme color (null = keep current), mode, wallpaper colors, amoled, tint %, shape overrides */
    private class P(val seed: Long? = null, val mode: String? = null, val dyn: Boolean? = null, val amoled: Boolean = false,
                    val tint: Int = 100, val shape: Map<String, Int> = emptyMap())
    private val indigo = 0xFF7B8CFFL
    private val p = linkedMapOf(
        "Default" to P(indigo, "auto", false),
        "Material You" to P(indigo, "auto", true),
        "Midnight" to P(indigo, "dark", false),
        "Daylight" to P(indigo, "light", false),
        "AMOLED" to P(indigo, "dark", false, true),
        "Ocean" to P(0xFF38BDF8, "dark", false),
        "Sakura" to P(0xFFFF7EB6, "dark", false),
        "Mint" to P(0xFF34D399, "dark", false),
        "Amber" to P(0xFFFFB454, "dark", false),
        "Graphite" to P(0xFFFF9F43, "dark", false, false, 0),
        "Flat (shape)" to P(shape = mapOf("key_radius" to 4, "key_shadow" to 0, "key_highlight" to 0, "key_gap_h" to 2, "key_gap_v" to 3)),
        "Bubble (shape)" to P(shape = mapOf("key_radius" to 22, "key_gap_h" to 5, "key_gap_v" to 9, "key_height" to 50, "key_highlight" to 55))
    )
    private val shapeKeys = listOf("key_radius", "key_shadow", "key_highlight", "key_gap_h", "key_gap_v", "key_height", "key_border_w")

    fun names() = p.keys.toList()
    fun apply(name: String) {
        val pr = p[name] ?: return
        val e = Settings.sp.edit()
        for (s in Settings.all) if ((s is ColorS && s.key != "seed_color") || s.key in shapeKeys) e.remove(s.key)
        pr.seed?.let { e.putInt("seed_color", it.toInt()) }
        pr.mode?.let { e.putString("theme_mode", it) }
        pr.dyn?.let { e.putBoolean("dynamic_color", it) }
        if (pr.seed != null) { e.putBoolean("amoled", pr.amoled); e.putInt("tint", pr.tint) }
        pr.shape.forEach { (k, v) -> e.putInt(k, v) }
        e.apply()
    }
}
