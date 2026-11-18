package com.psiboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class PsiBoardService : InputMethodService(), KbListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var store: ClipboardStore
    private lateinit var suggester: Suggester
    private lateinit var cm: ClipboardManager
    private lateinit var kb: KeyboardView
    private lateinit var root: LinearLayout
    private lateinit var strip: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var clipPanel: ClipboardPanel
    private lateinit var emojiPanel: EmojiPanel
    private var th: Theme? = null

    private var lang = "en"
    private var page = 0
    private var lastShiftTap = 0L
    private var lastSpace = 0L
    private var password = false
    private var noSuggest = false
    private var lastClip: String? = null
    private var lastClipAt = 0L

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }
    private val refreshRunnable = Runnable { rebuildAll() }

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        store = ClipboardStore(this)
        suggester = Suggester(this)
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        lang = Settings.str("default_lang")
        // elapsedRealtime resets to ~0 on reboot, so a smaller value than last seen means the phone rebooted
        val last = Settings.sp.getLong("last_elapsed", 0L)
        if (Settings.bool("clip_clear_on_boot") && SystemClock.elapsedRealtime() < last) store.clearUnpinned()
        Settings.sp.edit().putLong("last_elapsed", SystemClock.elapsedRealtime()).apply()
        Settings.sp.registerOnSharedPreferenceChangeListener(this)
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
        Settings.sp.unregisterOnSharedPreferenceChangeListener(this)
        suggester.save(); super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key != null && key !in Settings.keys) return
        ui.removeCallbacks(refreshRunnable); ui.postDelayed(refreshRunnable, 60)
    }

    // ---------------- views ----------------

    override fun onCreateInputView(): View {
        val t = Theme(this); th = t
        kb = KeyboardView(this).also { it.listener = this }
        clipPanel = ClipboardPanel(this, store, { pasteClip(it) }, { showPanel(null) }, { })
        emojiPanel = EmojiPanel(this, { commit(it) }, { showPanel(null) }, { onToken("{del}") })
        strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        content = FrameLayout(this)
        content.addView(kb, FrameLayout.LayoutParams(-1, -2))
        content.addView(clipPanel, FrameLayout.LayoutParams(-1, -1)); clipPanel.visibility = View.GONE
        content.addView(emojiPanel, FrameLayout.LayoutParams(-1, -1)); emojiPanel.visibility = View.GONE
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(strip, LinearLayout.LayoutParams(-1, t.stripH.toInt()))
        root.addView(content, LinearLayout.LayoutParams(-1, -2))
        rebuildAll()
        return root
    }

    private fun rebuildAll() {
        if (!::root.isInitialized) return
        val t = Theme(this); th = t
        kb.applyTheme(t)
        kb.isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        root.setBackgroundColor(t.nav)
        (strip.layoutParams as LinearLayout.LayoutParams).height = t.stripH.toInt()
        root.setPadding(0, 0, 0, 0)
        val h = (kb.measuredHeight.takeIf { it > 0 } ?: (t.keyH * 5).toInt())
        val ph = Settings.int("clip_panel_height")
        content.layoutParams?.let { }
        clipPanel.layoutParams = FrameLayout.LayoutParams(-1, if (ph == 0) h else (h * ph / 100))
        emojiPanel.layoutParams = FrameLayout.LayoutParams(-1, if (ph == 0) h else (h * ph / 100))
        applyLayout(); refreshStrip(); clipPanel.refresh(t); emojiPanel.refresh(t)
        root.requestLayout()
    }

    private fun enabledLangs() = Settings.langs.filter { Settings.bool("lang_$it") }.ifEmpty { listOf("en") }

    private fun applyLayout() {
        val langs = enabledLangs()
        if (lang !in langs) lang = langs[0]
        kb.submitRows(Layouts.build(lang, page, langs.size > 1))
        kb.spaceLabel = if (lang == "fa") "فارسی" else "English"
    }

    private fun showPanel(which: View?) {
        kb.visibility = if (which == null) View.VISIBLE else View.INVISIBLE
        clipPanel.visibility = if (which === clipPanel) View.VISIBLE else View.GONE
        emojiPanel.visibility = if (which === emojiPanel) View.VISIBLE else View.GONE
        if (which === clipPanel) { store.prune(); clipPanel.refresh(th ?: Theme(this)) }
        if (which === emojiPanel) emojiPanel.refresh(th ?: Theme(this))
        if (which != null) {
            val h = kb.height.takeIf { it > 0 } ?: kb.measuredHeight
            val ph = Settings.int("clip_panel_height")
            val lp = FrameLayout.LayoutParams(-1, if (ph == 0) h else h * ph / 100)
            which.layoutParams = lp
        }
    }

    // ---------------- editor lifecycle ----------------

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        password = cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
            cls == InputType.TYPE_CLASS_NUMBER && (v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        noSuggest = !Settings.bool("suggest") || (password && Settings.bool("password_safe")) || (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME) 1 else 0
        kb.enterLabel = when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Go"; EditorInfo.IME_ACTION_SEARCH -> "Search"; EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"; EditorInfo.IME_ACTION_DONE -> "Done"; else -> null
        }.takeIf { Settings.bool("enter_sends_action") && (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0 }
        rebuildAll(); showPanel(null)
        updateShiftFromEditor(); refreshStrip()
    }

    override fun onFinishInputView(finishingInput: Boolean) { suggester.save(); super.onFinishInputView(finishingInput) }

    override fun onUpdateSelection(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
        super.onUpdateSelection(a, b, c, d, e, f); refreshStrip()
    }

    override fun onEvaluateFullscreenMode() = false

    // ---------------- input ----------------

    override fun onText(s: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(s, 1)
        if (kb.shift == 1) kb.shift = 0
        if (s.length == 1 && !s[0].isLetterOrDigit() && s != "'" && Settings.bool("auto_cap")) updateShiftFromEditor()
        refreshStrip()
    }

    private fun commit(s: String) { currentInputConnection?.commitText(s, 1); refreshStrip() }

    override fun onToken(t: String) {
        val ic = currentInputConnection
        when (t) {
            "{del}" -> if (ic != null) {
                val sel = ic.getSelectedText(0)
                if (!sel.isNullOrEmpty()) ic.commitText("", 1) else {
                    val before = ic.getTextBeforeCursor(2, 0)
                    val n = if (before != null && before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
                    ic.deleteSurroundingText(n, 0)
                }
                updateShiftFromEditor(); refreshStrip()
            }
            "{shift}" -> {
                val now = SystemClock.uptimeMillis()
                kb.shift = if (kb.shift == 2) 0 else if (kb.shift == 1 && Settings.bool("caps_double_tap") && now - lastShiftTap < 350) 2 else if (kb.shift == 1) 0 else 1
                lastShiftTap = now
            }
            "{enter}" -> if (ic != null) {
                val ei = currentInputEditorInfo
                val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
                val multi = (ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                if (Settings.bool("enter_sends_action") && !multi && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
                    && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) ic.performEditorAction(action)
                else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                updateShiftFromEditor()
            }
            "{space}" -> space(ic)
            "{sym}" -> { page = 1; applyLayout() }
            "{sym2}" -> { page = 2; applyLayout() }
            "{abc}" -> { page = 0; applyLayout() }
            "{lang}" -> { val l = enabledLangs(); lang = l[(l.indexOf(lang) + 1) % l.size]; page = 0; applyLayout() }
            "{lang}_long", "{space}_long" -> (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            "{emoji}" -> showPanel(emojiPanel)
            "{emoji}_long" -> showPanel(clipPanel)
        }
    }

    private fun space(ic: android.view.inputmethod.InputConnection?) {
        ic ?: return
        val now = SystemClock.uptimeMillis()
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val (word, prev) = wordAndPrev(before)
        if (Settings.bool("double_space_period") && now - lastSpace < 400 && before.endsWith(" ") && before.length >= 2 && before[before.length - 2].isLetterOrDigit()) {
            ic.deleteSurroundingText(1, 0); ic.commitText(if (lang == "fa") ". " else ". ", 1)
            lastSpace = 0; updateShiftFromEditor(); refreshStrip(); return
        }
        lastSpace = now
        var out = " "
        if (word.isNotEmpty() && !noSuggest) {
            val sc = Settings.str("shortcuts").lines().firstOrNull { it.substringBefore('=').trim() == word && it.contains('=') }
            if (sc != null && Settings.bool("expand_shortcuts")) {
                ic.deleteSurroundingText(word.length, 0); ic.commitText(sc.substringAfter('=') + " ", 1)
                updateShiftFromEditor(); refreshStrip(); return
            }
            val fix = if (Settings.bool("autocorrect")) suggester.correct(lang, word) else null
            if (fix != null) { ic.deleteSurroundingText(word.length, 0); ic.commitText(fix, 1) } else suggester.learn(lang, word, prev)
        }
        ic.commitText(out, 1)
        updateShiftFromEditor(); refreshStrip()
    }

    override fun onMoveCursor(steps: Int, select: Boolean) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val ic = currentInputConnection ?: return
        val meta = if (select && Settings.bool("shift_cursor_select")) KeyEvent.META_SHIFT_ON else 0
        repeat(kotlin.math.abs(steps)) {
            val t = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
        }
    }

    override fun onDeleteWordSwipe() {
        val ic = currentInputConnection ?: return
        val b = ic.getTextBeforeCursor(60, 0)?.toString() ?: return
        var i = b.length
        while (i > 0 && b[i - 1].isWhitespace()) i--
        while (i > 0 && !b[i - 1].isWhitespace()) i--
        if (b.length - i > 0) ic.deleteSurroundingText(b.length - i, 0)
    }

    private fun updateShiftFromEditor() {
        if (!::kb.isInitialized || kb.shift == 2 || page != 0 || lang == "fa") return
        val ei = currentInputEditorInfo ?: return
        if (!Settings.bool("auto_cap") || password) { if (kb.shift == 1) kb.shift = 0; return }
        kb.shift = if ((currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0) != 0) 1 else 0
    }

    // ---------------- suggestions / toolbar ----------------

    private fun wordAndPrev(before: String): Pair<String, String?> {
        var i = before.length
        while (i > 0 && (before[i - 1].isLetter() || before[i - 1] == '\'' || before[i - 1] == '‌')) i--
        val word = before.substring(i)
        var j = i
        while (j > 0 && before[j - 1].isWhitespace()) j--
        var k = j
        while (k > 0 && before[k - 1].isLetter()) k--
        return word to (if (j > k && j < i) before.substring(k, j) else null)
    }

    private fun refreshStrip() {
        if (!::strip.isInitialized) return
        val t = th ?: return
        strip.removeAllViews(); strip.setBackgroundColor(t.stripBg)
        val ic = currentInputConnection
        val before = ic?.getTextBeforeCursor(40, 0)?.toString() ?: ""
        var list: List<String> = emptyList()
        if (!noSuggest && ic != null) {
            val (w, prev) = wordAndPrev(before)
            list = suggester.suggest(lang, w, prev, Settings.int("suggest_count")).filter { !it.equals(w, true) || w.isEmpty() }
            if (w.isNotEmpty() && list.none { it == w }) list = listOf(w) + list.take(Settings.int("suggest_count") - 1)
        }
        val clipFresh = Settings.bool("clip_suggest") && Settings.bool("clip_enabled") && lastClip != null && !password &&
            System.currentTimeMillis() - lastClipAt < Settings.int("clip_suggest_sec") * 1000L
        if (clipFresh && before.isBlank() || (clipFresh && list.isEmpty())) {
            strip.addView(chip("📋 " + lastClip!!.replace('\n', ' ').take(40), t) { pasteClip(lastClip!!) }, LinearLayout.LayoutParams(0, -1, 1f)); return
        }
        if (list.isNotEmpty()) {
            val (w, _) = wordAndPrev(before)
            list.forEachIndexed { i, s ->
                if (i > 0) strip.addView(View(this).apply { setBackgroundColor((t.stripIcon and 0x00FFFFFF) or 0x33000000) }, LinearLayout.LayoutParams(1, (t.stripH * 0.45f).toInt()))
                strip.addView(chip(s, t) { replaceWord(w, s) }, LinearLayout.LayoutParams(0, -1, 1f))
            }
            return
        }
        if (!Settings.bool("tb_show")) return
        fun btn(icon: String, on: () -> Unit) = strip.addView(IconView(this, icon, t.stripIcon, on), LinearLayout.LayoutParams(0, -1, 1f))
        if (Settings.bool("tb_voice")) btn("globe") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        if (Settings.bool("tb_clip")) btn("clip") { showPanel(clipPanel) }
        if (Settings.bool("tb_emoji")) btn("emoji") { showPanel(emojiPanel) }
        if (Settings.bool("tb_cursor")) { btn("left") { onMoveCursor(-1, false) }; btn("right") { onMoveCursor(1, false) } }
        if (Settings.bool("tb_select_all")) {
            btn("all") { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }
            btn("copy") { currentInputConnection?.performContextMenuAction(android.R.id.copy) }
            btn("paste") { currentInputConnection?.performContextMenuAction(android.R.id.paste) }
        }
        if (Settings.bool("tb_onehand")) btn("onehand") { Settings.sp.edit().putBoolean("one_handed", !Settings.bool("one_handed")).apply() }
        if (Settings.bool("tb_settings")) btn("gear") {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun chip(text: String, t: Theme, on: () -> Unit) = TextView(this).apply {
        this.text = text; setTextColor(t.stripText); textSize = Settings.int("strip_text_size").toFloat()
        gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        setOnClickListener { on() }
    }

    private fun replaceWord(cur: String, s: String) {
        val ic = currentInputConnection ?: return
        if (cur.isNotEmpty()) ic.deleteSurroundingText(cur.length, 0)
        ic.commitText(s + " ", 1)
        val (_, prev) = wordAndPrev(ic.getTextBeforeCursor(40, 0)?.toString() ?: "")
        suggester.learn(lang, s, prev)
        updateShiftFromEditor(); refreshStrip()
    }

    // ---------------- clipboard ----------------

    private fun pasteClip(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        if (Settings.bool("clip_close_after_paste")) showPanel(null)
        refreshStrip()
    }

    private fun onClipChanged() {
        if (!Settings.bool("clip_enabled")) return
        if (password && Settings.bool("clip_skip_sensitive")) return
        val blocked = Settings.str("clip_blocked_apps").lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (currentInputEditorInfo?.packageName?.let { it in blocked } == true) return
        val clip = try { cm.primaryClip } catch (_: SecurityException) { null } ?: return
        if (clip.itemCount == 0) return
        if (Settings.bool("clip_skip_sensitive") && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (store.add(text)) {
            lastClip = text; lastClipAt = System.currentTimeMillis()
            ui.post { refreshStrip(); if (::clipPanel.isInitialized && clipPanel.visibility == View.VISIBLE) clipPanel.refresh(th ?: Theme(this)) }
        }
    }
}
