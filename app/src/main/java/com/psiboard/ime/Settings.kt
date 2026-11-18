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
    private const val A = "Appearance: colors"
    private const val B = "Appearance: shape & size"
    private const val C = "Layout"
    private const val D = "Typing"
    private const val E = "Feedback"
    private const val F = "Suggestions"
    private const val G = "Clipboard"
    private const val H = "Languages & layouts"
    private const val I = "Toolbar"

    val fonts = listOf("sans", "sans-bold", "serif", "mono", "italic", "light", "condensed")
    val caseModes = listOf("auto", "lower", "upper")
    val swipeActions = listOf("none", "cursor", "delete-word")
    val langs = listOf("en", "fa")

    val all: List<S> = listOf(
        ChoiceS("preset", "Theme preset (applies colors and key shape)", A, Presets.names(), "Midnight"),
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

        SliderS("key_height", "Key height", B, 36, 80, 50, "dp"),
        SliderS("key_gap_h", "Horizontal gap between keys", B, 0, 14, 3, "dp"),
        SliderS("key_gap_v", "Vertical gap between rows", B, 0, 14, 6, "dp"),
        SliderS("key_radius", "Key corner radius", B, 0, 30, 9, "dp"),
        SliderS("key_border_w", "Key border width", B, 0, 6, 0, "dp"),
        SliderS("key_highlight", "Key top-edge highlight", B, 0, 100, 45, "%"),
        SliderS("key_shadow", "Key shadow offset", B, 0, 6, 1, "dp"),
        SliderS("text_size", "Key text size", B, 12, 34, 21, "sp"),
        SliderS("special_text_size", "Special key text size", B, 10, 28, 14, "sp"),
        SliderS("hint_size", "Hint text size", B, 6, 16, 9, "sp"),
        SliderS("text_opacity", "Key text opacity", B, 20, 100, 100, "%"),
        SliderS("key_opacity", "Key opacity", B, 0, 100, 100, "%"),
        SliderS("kb_opacity", "Keyboard background opacity", B, 0, 100, 100, "%"),
        SliderS("side_pad", "Side padding", B, 0, 24, 3, "dp"),
        SliderS("bottom_pad", "Bottom padding", B, 0, 60, 6, "dp"),
        SliderS("top_pad", "Top padding", B, 0, 20, 4, "dp"),
        ChoiceS("font", "Key font", B, fonts, "sans"),
        ChoiceS("label_case", "Letter case on keys", B, caseModes, "auto"),
        SwitchS("show_hints", "Show long-press hints on keys", B, true),
        SwitchS("key_preview", "Key preview bubble", B, true),
        SliderS("preview_size", "Preview bubble size", B, 36, 100, 56, "dp"),
        SliderS("popup_radius", "Popup corner radius", B, 0, 24, 10, "dp"),

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
        SliderS("space_weight", "Space bar width", C, 20, 60, 40, "%"),

        SwitchS("auto_cap", "Auto-capitalize sentences", D, true),
        SwitchS("double_space_period", "Double space inserts period", D, true),
        SwitchS("auto_space_after_punct", "Auto space after punctuation from suggestions", D, true),
        SwitchS("caps_double_tap", "Double-tap shift = caps lock", D, true),
        SwitchS("shift_cursor_select", "Hold shift + swipe space = select text", D, true),
        ChoiceS("space_swipe", "Swipe on space bar", D, swipeActions, "cursor"),
        ChoiceS("delete_swipe", "Swipe left on delete", D, swipeActions, "delete-word"),
        SliderS("longpress_ms", "Long-press delay", D, 150, 800, 300, "ms"),
        SliderS("repeat_start_ms", "Key repeat start delay", D, 100, 800, 350, "ms"),
        SliderS("repeat_ms", "Key repeat interval", D, 15, 150, 45, "ms"),
        SliderS("swipe_step", "Cursor swipe sensitivity (px/char)", D, 8, 60, 22, "px"),
        SwitchS("enter_sends_action", "Enter performs editor action (Go/Search/Send)", D, true),
        SwitchS("password_safe", "Disable learning/clipboard in password fields", D, true),
        SwitchS("incognito", "Incognito (never learn words)", D, false),
        TextS("long_press_extra", "Extra long-press symbols for letters (letter=chars per line)", D, "", true, "Example:\na=àáâä\ne=éèê"),

        SwitchS("haptic", "Vibrate on key press", E, true),
        SliderS("haptic_ms", "Vibration strength/length", E, 1, 40, 12, "ms"),
        SwitchS("haptic_longpress", "Vibrate on long-press", E, true),
        SwitchS("sound", "Sound on key press", E, false),
        SliderS("sound_vol", "Sound volume", E, 0, 100, 40, "%"),

        SwitchS("suggest", "Show suggestions", F, true),
        SwitchS("autocorrect", "Auto-correct on space", F, false),
        SliderS("suggest_count", "Number of suggestions", F, 1, 5, 3),
        SwitchS("learn_words", "Learn new words", F, true),
        SwitchS("next_word", "Next-word suggestions (from your history)", F, true),
        SliderS("strip_height", "Suggestion/toolbar strip height", F, 32, 64, 42, "dp"),
        SliderS("strip_text_size", "Suggestion text size", F, 11, 24, 16, "sp"),
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
        SwitchS("tb_voice", "Switch-keyboard (globe) button", I, true),
        SliderS("emoji_cols", "Emoji grid columns", I, 5, 12, 8),
        SliderS("emoji_size", "Emoji size", I, 18, 44, 28, "sp"),
        SliderS("emoji_recent", "Recent emoji count", I, 8, 60, 32)
    )

    private val byKey = all.associateBy { it.key }
    val keys: Set<String> = byKey.keys
    lateinit var sp: SharedPreferences
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("psiboard", Context.MODE_PRIVATE) }

    fun bool(k: String): Boolean = sp.getBoolean(k, (byKey[k] as SwitchS).def)
    fun int(k: String): Int = when (val s = byKey[k]) {
        is ColorS -> sp.getInt(k, s.def)
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
    private fun h(v: Long) = v
    private val p = linkedMapOf<String, Map<String, Long>>(
        "Midnight" to emptyMap(),
        "Graphite" to mapOf("c_bg" to 0xFF1A1A1C, "c_bg2" to 0xFF111112, "c_key" to 0xFF2E2E31, "c_key_pressed" to 0xFF58585E, "c_special" to 0xFF222225,
            "c_special_pressed" to 0xFF45454A, "c_accent" to 0xFFFF9F43, "c_popup_sel" to 0xFFFF9F43),
        "Ocean" to mapOf("c_bg" to 0xFF0D1A25, "c_bg2" to 0xFF08111A, "c_key" to 0xFF1B3144, "c_key_pressed" to 0xFF2F5878, "c_special" to 0xFF132535,
            "c_special_pressed" to 0xFF26465F, "c_accent" to 0xFF38BDF8, "c_popup_sel" to 0xFF38BDF8, "c_text_accent" to 0xFF04202E),
        "Sakura" to mapOf("c_bg" to 0xFF22171E, "c_bg2" to 0xFF170F14, "c_key" to 0xFF3A2632, "c_key_pressed" to 0xFF6B4259, "c_special" to 0xFF2C1C26,
            "c_special_pressed" to 0xFF553449, "c_accent" to 0xFFFF7EB6, "c_popup_sel" to 0xFFFF7EB6, "c_text_accent" to 0xFF3A0D22),
        "Mint" to mapOf("c_bg" to 0xFF0E1C19, "c_bg2" to 0xFF08120F, "c_key" to 0xFF1D3531, "c_key_pressed" to 0xFF2F5C52, "c_special" to 0xFF152A26,
            "c_special_pressed" to 0xFF264A42, "c_accent" to 0xFF34D399, "c_popup_sel" to 0xFF34D399, "c_text_accent" to 0xFF04271B),
        "Light" to mapOf("c_bg" to 0xFFE6E8EF, "c_bg2" to 0xFFD9DCE6, "c_key" to 0xFFFFFFFF, "c_key_pressed" to 0xFFC3C8D8, "c_special" to 0xFFC9CDDA,
            "c_special_pressed" to 0xFFADB3C6, "c_accent" to 0xFF4F6BFF, "c_text" to 0xFF1B1C24, "c_text_special" to 0xFF3C3E4D, "c_hint" to 0xFF8B90A0,
            "c_popup_bg" to 0xFFFFFFFF, "c_popup_text" to 0xFF1B1C24, "c_strip_text" to 0xFF1B1C24, "c_strip_icon" to 0xFF3C3E4D, "c_shadow" to 0x30000000,
            "c_clip_card" to 0xFFFFFFFF, "c_clip_text" to 0xFF1B1C24, "c_popup_sel" to 0xFF4F6BFF, "key_highlight" to 0),
        "AMOLED" to mapOf("c_bg" to 0xFF000000, "c_bg2" to 0xFF000000, "c_key" to 0xFF141417, "c_key_pressed" to 0xFF3A3B4C, "c_special" to 0xFF0B0B0D,
            "c_special_pressed" to 0xFF2A2B38, "c_shadow" to 0x00000000, "key_shadow" to 0),
        "Flat" to mapOf("key_radius" to 3, "key_shadow" to 0, "key_highlight" to 0, "key_gap_h" to 2, "key_gap_v" to 2, "c_shadow" to 0x00000000),
        "Bubble" to mapOf("key_radius" to 24, "key_gap_h" to 5, "key_gap_v" to 8, "key_height" to 52, "key_highlight" to 70)
    )
    private val shapeKeys = listOf("key_radius", "key_shadow", "key_highlight", "key_gap_h", "key_gap_v", "key_height", "key_border_w")
    private fun mix(a: Int, b: Int, f: Float) = android.graphics.Color.rgb(
        (android.graphics.Color.red(a) * (1 - f) + android.graphics.Color.red(b) * f).toInt(),
        (android.graphics.Color.green(a) * (1 - f) + android.graphics.Color.green(b) * f).toInt(),
        (android.graphics.Color.blue(a) * (1 - f) + android.graphics.Color.blue(b) * f).toInt()) or (0xFF shl 24)

    fun names() = p.keys.toList()
    fun apply(name: String) {
        val e = Settings.sp.edit()
        for (s in Settings.all) if (s is ColorS || s.key in shapeKeys) e.remove(s.key)
        val m = HashMap(p[name] ?: emptyMap())
        val bg = m["c_bg"]?.toInt()
        if (bg != null) {
            m.putIfAbsent("c_strip_bg", bg.toLong() and 0xFFFFFFFFL); m.putIfAbsent("c_nav", bg.toLong() and 0xFFFFFFFFL)
            m["c_key"]?.toInt()?.let { k ->
                m.putIfAbsent("c_clip_card", k.toLong() and 0xFFFFFFFFL)
                if (!m.containsKey("c_popup_bg")) m["c_popup_bg"] = mix(k, 0xFFFFFFFF.toInt(), 0.1f).toLong() and 0xFFFFFFFFL
            }
        }
        m.forEach { (k, v) -> e.putInt(k, v.toInt()) }
        e.apply()
    }
}
