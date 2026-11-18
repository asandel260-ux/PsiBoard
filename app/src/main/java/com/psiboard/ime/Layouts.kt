package com.psiboard.ime

class Key(val label: String, val token: String, val popups: List<String>, val weight: Float, val special: Boolean) {
    var x = 0f; var y = 0f; var w = 0f; var h = 0f
    var pressed = false
    var hint: String? = null
}

/**
 * Layout text format (one row per line, keys separated by spaces):
 *   q w e r t           plain keys
 *   e/éèê               key 'e' with long-press popups é è ê
 *   {shift}*1.5         special key with relative width 1.5
 *   {comma} {slash} {star} {period} {pipe}   literal characters that clash with the syntax
 * Specials: {shift} {del} {enter} {space} {sym} {sym2} {abc} {lang} {emoji}
 */
object Layouts {
    const val EN = "q w e r t y u i o p\na s d f g h j k l\n{shift}*1.5 z x c v b n m {del}*1.5"
    const val FA = "ض ص ث ق ف غ ع ه خ ح ج چ\nش س ی ب ل ا ت ن م ک گ\nژ ظ ط ز ر ذ د پ و {del}*1.5"
    const val SYM = "1 2 3 4 5 6 7 8 9 0\n@ # $ _ & - + ( ) {slash}\n{sym2}*1.5 {star} \" ' : ; ! ? {del}*1.5"
    const val SYM2 = "~ ` | • √ π ÷ × ¶ ∆\n£ ¢ € ¥ ^ ° = { } \\\n{sym}*1.5 % © ® ™ ✓ [ ] < > {del}*1.5"

    private val ALIAS = mapOf("comma" to ",", "period" to ".", "fcomma" to "،", "slash" to "/", "star" to "*", "pipe" to "|")

    private val DEF_POP = mapOf(
        "a" to "àáâäæãåā", "e" to "èéêëēėę", "i" to "îïíīįì", "o" to "ôöòóœøōõ", "u" to "ûüùúū",
        "c" to "çćč", "n" to "ñń", "s" to "ßśš", "y" to "ý", "z" to "žźż",
        "." to ",?!:;'\"-…", "," to "", "!" to "¡", "?" to "¿", "-" to "–—•", "'" to "‘’\"“”", "$" to "€£¥¢",
        "ا" to "آأإ", "ی" to "ئيى", "ه" to "ةۀ", "و" to "ؤ", "ک" to "ك", "ز" to "ژ", "،" to "؛؟.", "ب" to "پ"
    )

    fun codepoints(s: String): List<String> {
        val out = ArrayList<String>(s.length)
        var i = 0
        while (i < s.length) { val n = Character.charCount(s.codePointAt(i)); out.add(s.substring(i, i + n)); i += n }
        return out
    }

    private fun parseExtras(text: String): Map<String, String> {
        val m = HashMap<String, String>()
        for (line in text.lines()) { val i = line.indexOf('='); if (i > 0) m[line.substring(0, i).trim()] = line.substring(i + 1).trim() }
        return m
    }

    fun parseKey(tok: String, extra: Map<String, String>): Key {
        var t = tok; var weight = 1f
        val star = t.lastIndexOf('*')
        if (star > 0) t.substring(star + 1).toFloatOrNull()?.let { weight = it; t = t.substring(0, star) }
        if (t.length > 2 && t.startsWith("{") && t.endsWith("}")) {
            val n = t.substring(1, t.length - 1)
            val lit = ALIAS[n]
            return if (lit != null) Key(lit, lit, popupsFor(lit, extra), weight, false) else Key(n, "{$n}", emptyList(), weight, true)
        }
        val slash = t.indexOf('/')
        if (slash > 0) return Key(t.substring(0, slash), t.substring(0, slash), codepoints(t.substring(slash + 1)), weight, false)
        return Key(t, t, popupsFor(t, extra), weight, false)
    }

    private fun popupsFor(label: String, extra: Map<String, String>) = codepoints(extra[label] ?: DEF_POP[label] ?: "")

    fun parseRows(text: String, extra: Map<String, String>): MutableList<List<Key>> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .map { line -> line.split(Regex("\\s+")).map { parseKey(it, extra) } }.toMutableList()

    fun persianDigits(s: String): String = s.map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

    /** page: 0 letters, 1 symbols, 2 more symbols */
    fun build(lang: String, page: Int, multiLang: Boolean): List<List<Key>> {
        val extra = parseExtras(Settings.str("long_press_extra"))
        val fa = lang == "fa"
        val rows: MutableList<List<Key>>
        when (page) {
            1 -> rows = parseRows(Settings.str("layout_sym").ifBlank { Layouts.SYM }, extra)
            2 -> rows = parseRows(Settings.str("layout_sym2").ifBlank { Layouts.SYM2 }, extra)
            else -> {
                val def = if (fa) FA else EN
                rows = parseRows(Settings.str(if (fa) "layout_fa" else "layout_en").ifBlank { def }, extra)
                val numText = Settings.str("layout_numrow").ifBlank { "1 2 3 4 5 6 7 8 9 0" }
                val digits = numText.split(Regex("\\s+")).map { if (fa && Settings.bool("fa_digits")) persianDigits(it) else it }
                if (Settings.bool("number_row")) {
                    rows.add(0, digits.map { Key(it, it, emptyList(), 1f, false) })
                } else if (rows.isNotEmpty()) {
                    rows[0].forEachIndexed { i, k -> if (!k.special && i < digits.size) k.hint = digits[i] }
                    rows[0] = rows[0].mapIndexed { i, k ->
                        if (!k.special && i < digits.size) Key(k.label, k.token, listOf(digits[i]) + k.popups, k.weight, false).also { it.hint = digits[i] } else k
                    }
                }
            }
        }
        val b = ArrayList<String>()
        if (Settings.bool("show_symbols_key")) b.add(if (page == 0) "{sym}*1.4" else "{abc}*1.4")
        if (Settings.bool("show_emoji_key")) b.add("{emoji}")
        if (multiLang) b.add("{lang}")
        val cp = Settings.bool("show_comma_period")
        if (cp) b.add(if (fa) "{fcomma}" else "{comma}")
        b.add("{space}*${Settings.int("space_weight") * 0.2f}")
        if (cp) b.add("{period}")
        b.add("{enter}*1.4")
        rows.add(b.map { parseKey(it, extra) })
        return rows
    }
}
