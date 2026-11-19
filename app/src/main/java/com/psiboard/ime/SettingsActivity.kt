package com.psiboard.ime

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Entire UI is generated from Settings.all, with a live keyboard preview pinned on top. */
class SettingsActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var preview: KeyboardView
    private lateinit var list: LinearLayout
    private var query = ""
    private companion object {
        val collapsed = HashSet<String>()
        var collapsedInit = false
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Settings chrome uses the same OKLCH engine as the keyboard: system light/dark + the user's seed colour/tint.
    private var night = false
    private var cBg = 0; private var cCard = 0; private var cText = 0; private var cSub = 0
    private var cLine = 0; private var cAccent = 0; private var cOnAccent = 0; private var cTrackOff = 0

    private fun computePal() {
        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        Settings.refreshDerived(this)
        val sd = Palette.seed(this); val h = sd[0]; val ca = sd[1].coerceIn(0.10f, 0.17f)
        val tn = Settings.int("tint") / 100f
        if (night) {
            cBg = Ok.argb(0.165f, 0.012f * tn, h); cCard = Ok.argb(0.225f, 0.016f * tn, h)
            cText = Ok.withContrast(0.94f, 0.008f, h, cCard, 9f); cSub = Ok.withContrast(0.74f, 0.02f, h, cCard, 4.5f)
            cLine = Ok.argb(0.30f, 0.02f * tn, h)
            cAccent = Ok.argb(0.80f, ca, h); cOnAccent = Ok.withContrast(0.22f, ca * 0.3f, h, cAccent, 7f)
        } else {
            cBg = Ok.argb(0.965f, 0.008f * tn, h); cCard = Ok.argb(0.998f, 0.002f, h)
            cText = Ok.withContrast(0.22f, 0.01f, h, cCard, 9f); cSub = Ok.withContrast(0.50f, 0.02f, h, cCard, 4.5f)
            cLine = Ok.argb(0.90f, 0.01f * tn, h)
            cAccent = Ok.argb(0.50f, ca * 1.05f, h); cOnAccent = Ok.withContrast(0.99f, 0.01f, h, cAccent, 4.5f)
        }
        cTrackOff = Ok.mix(cCard, cText, 0.20f)
    }

    private fun ripple(): android.graphics.drawable.Drawable? {
        val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val d = a.getDrawable(0); a.recycle(); return d
    }

    private fun rounded(color: Int, r: Int, stroke: Int = 0) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Settings.init(this)
        // CI-only test hooks: they run only if a marker file exists in the app's private folder, which no other app can create
        if (java.io.File(filesDir, "ci_hooks").exists()) applyDebugExtras()
        computePal()
        actionBar?.hide()
        window.statusBarColor = cBg; window.navigationBarColor = cBg
        if (android.os.Build.VERSION.SDK_INT >= 23 && !night) {
            var f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (android.os.Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = f
        }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cBg) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(12)) }
        head.addView(TextView(this).apply {
            text = "ψ"; textSize = 26f; gravity = Gravity.CENTER; setTextColor(cOnAccent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); background = rounded(cAccent, 14)
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(14) })
        head.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("PsiBoard", 26f, cText, true)); addView(text("Light. Fast. Yours - every pixel is adjustable.", 13f, cSub))
        })
        page.addView(head)
        val scrollBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), dp(24)) }
        // live preview card
        preview = KeyboardView(this).also { it.shift = 0 }
        val pv = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -2))
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(20).toFloat()) }
            }
        }
        scrollBody.addView(pv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val tryField = EditText(this).apply {
            hint = "Tap here and try your keyboard"; textSize = 15f; setTextColor(cText); setHintTextColor(cSub)
            background = rounded(cCard, 16, if (night) 0 else cLine); setPadding(dp(16), dp(12), dp(16), dp(12)); setSingleLine()
        }
        scrollBody.addView(tryField, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        statusCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(cCard, 20, if (night) 0 else cLine); setPadding(dp(16), dp(14), dp(16), dp(14)) }
        scrollBody.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val search = EditText(this).apply {
            hint = "Search ${Settings.all.size} settings"; setSingleLine(); textSize = 15f; setTextColor(cText); setHintTextColor(cSub)
            background = rounded(cCard, 16, if (night) 0 else cLine); setPadding(dp(16), dp(12), dp(16), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s.toString().trim().lowercase(); build() }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        scrollBody.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollBody.addView(list)
        page.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(scrollBody) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
        if (!collapsedInit) {
            collapsedInit = true
            Settings.all.map { it.section }.distinct().forEach { collapsed.add(it) }
            collapsed.remove(Settings.all.first().section)
        }
        build(); refreshPreview(); refreshStatus()
    }

    private lateinit var statusCard: LinearLayout

    /** Debug builds only (used by CI screenshot tests): psi_reset, psi_preset, psi_kv="key=value,key=value". */
    private fun applyDebugExtras() {
        if (intent.getBooleanExtra("psi_reset", false)) Settings.resetAll()
        intent.getStringExtra("psi_clip")?.let { v ->
            val st = ClipboardStore(this)
            v.split(',').filter { it.isNotBlank() }.forEach { st.add(it.replace('_', ' ')) }
        }
        intent.getStringExtra("psi_preset")?.let { Presets.apply(it); Settings.sp.edit().putString("preset", it).apply() }
        intent.getStringExtra("psi_kv")?.split(',')?.forEach { kv ->
            val i = kv.indexOf('='); if (i < 0) return@forEach
            val k = kv.substring(0, i); val v = kv.substring(i + 1)
            val e = Settings.sp.edit()
            when (Settings.all.firstOrNull { it.key == k }) {
                is SwitchS -> e.putBoolean(k, v.toBoolean())
                is SliderS -> e.putInt(k, v.toInt())
                is ColorS -> e.putInt(k, v.toLong(16).toInt())
                is ChoiceS, is TextS -> e.putString(k, v)
                null -> {}
            }
            e.apply()
        }
    }

    private fun refreshStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(packageName) == true
        statusCard.removeAllViews()
        statusCard.addView(text("Setup", 16f, cText, true))
        fun step(done: Boolean, label: String, action: String, go: () -> Unit) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
            r.addView(text(if (done) "✓" else "○", 18f, if (done) cAccent else cSub, true), LinearLayout.LayoutParams(dp(28), -2))
            r.addView(text(label, 14f, cText), LinearLayout.LayoutParams(0, -2, 1f))
            if (!done) r.addView(TextView(this).apply {
                text = action; textSize = 13f; setTextColor(cOnAccent); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = rounded(cAccent, 20); setPadding(dp(18), dp(9), dp(18), dp(9)); setOnClickListener { go() }
            })
            statusCard.addView(r)
        }
        step(enabled, "Enable PsiBoard in system keyboards", "Enable") { startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) }
        step(current, "Select PsiBoard as your keyboard", "Select") { imm.showInputMethodPicker() }
    }

    override fun onResume() { super.onResume(); Settings.sp.registerOnSharedPreferenceChangeListener(this); if (::statusCard.isInitialized) refreshStatus() }
    override fun onPause() { Settings.sp.unregisterOnSharedPreferenceChangeListener(this); super.onPause() }
    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = refreshPreview()

    private fun refreshPreview() {
        preview.applyTheme(Theme(this))
        val lang = Settings.str("default_lang")
        preview.lang = lang
        preview.spaceLabel = if (lang == "fa") "فارسی" else "English"
        preview.symLabel = if (lang == "fa") "۱۲۳" else "?123"
        preview.submitRows(Layouts.build(lang, 0, false))
    }

    /** After a theme change: recompute the settings chrome so it matches the new palette. */
    private fun refreshChrome() = recreate()

    private fun build() {
        Settings.refreshDerived(this)
        list.removeAllViews()
        if (query.isEmpty()) {
            val card = card()
            listOf<Pair<String, () -> Unit>>(
                "Export settings (copy JSON)" to {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("psiboard", Settings.exportJson()))
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show() },
                "Import settings (paste JSON)" to { importDialog() },
                "Forget learned words" to { Suggester(this).forgetAll(); Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show() },
                "Reset everything to defaults" to {
                    AlertDialog.Builder(this).setMessage("Reset all settings?").setPositiveButton("Reset") { _, _ -> Settings.resetAll(); build() }.setNegativeButton("Cancel", null).show() }
            ).forEachIndexed { i, (l, f) -> if (i > 0) card.addView(divider()); card.addView(actionRow(l, f)) }
            list.addView(sectionTitle("Tools", false, null)); list.addView(card)
        }
        var card: LinearLayout? = null
        var lastSection = ""
        for (s in Settings.all) {
            if (query.isNotEmpty() && !s.title.lowercase().contains(query) && !s.section.lowercase().contains(query)) continue
            if (s.section != lastSection) {
                lastSection = s.section
                val sec = s.section
                val closed = collapsed.contains(sec) && query.isEmpty()
                list.addView(sectionTitle(sec, closed) { if (!collapsed.remove(sec)) collapsed.add(sec); build() })
                card = card(); if (!closed) list.addView(card)
            }
            if (query.isEmpty() && collapsed.contains(s.section)) continue
            if (card!!.childCount > 0) card!!.addView(divider())
            card!!.addView(row(s))
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = rounded(cCard, 20, if (night) 0 else cLine); clipToOutline = true
    }
    private fun divider() = View(this).apply { setBackgroundColor(cLine); layoutParams = LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(16) } }
    private fun sectionTitle(name: String, closed: Boolean, on: (() -> Unit)?) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(18), dp(6), dp(8))
        addView(text(name.uppercase(), 12f, cAccent, true).apply { letterSpacing = 0.08f }, LinearLayout.LayoutParams(0, -2, 1f))
        if (on != null) { addView(text(if (closed) "▸" else "▾", 14f, cSub)); setOnClickListener { on() } }
    }
    private fun actionRow(label: String, on: () -> Unit) = text(label, 15f, cText).apply { background = ripple(); setPadding(dp(16), dp(15), dp(16), dp(15)); setOnClickListener { on() } }

    private fun importDialog() {
        val et = EditText(this).apply { hint = "Paste JSON"; minLines = 4 }
        AlertDialog.Builder(this).setView(et).setPositiveButton("Import") { _, _ ->
            try { Settings.importJson(et.text.toString()); build() } catch (e: Exception) { Toast.makeText(this, "Invalid JSON", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun valueLabel(s: SliderS, v: Int) = if (s.min == -1 && v == -1) "Auto" else "$v${s.unit}"

    private fun tintSwitch(sw: Switch) {
        if (android.os.Build.VERSION.SDK_INT < 23) return
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(st, intArrayOf(cOnAccent, cSub))
        sw.trackTintList = ColorStateList(st, intArrayOf(cAccent, cTrackOff))
    }

    private fun row(s: S): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13)) }
        when (s) {
            is SwitchS -> {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                line.addView(text(s.title, 15f, cText), LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(Switch(this).apply { tintSwitch(this); isChecked = Settings.bool(s.key); setOnCheckedChangeListener { _, c -> Settings.sp.edit().putBoolean(s.key, c).apply() } })
                box.addView(line)
            }
            is SliderS -> {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val value = text(valueLabel(s, Settings.int(s.key)), 13f, cAccent, true)
                line.addView(text(s.title, 15f, cText), LinearLayout.LayoutParams(0, -2, 1f)); line.addView(value)
                val sb = SeekBar(this).apply {
                    max = s.max - s.min; progress = Settings.int(s.key) - s.min
                    progressTintList = ColorStateList.valueOf(cAccent); thumbTintList = ColorStateList.valueOf(cAccent); progressBackgroundTintList = ColorStateList.valueOf(cTrackOff)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) {
                            value.text = valueLabel(s, p + s.min)
                            if (user) Settings.sp.edit().putInt(s.key, p + s.min).apply()
                        }
                        override fun onStartTrackingTouch(b: SeekBar?) {}
                        override fun onStopTrackingTouch(b: SeekBar?) {}
                    })
                }
                box.addView(line); box.addView(sb)
            }
            is ColorS -> {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                line.addView(text(s.title, 15f, cText), LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(text(if (Settings.sp.contains(s.key)) "Custom" else "Auto", 12f, cSub), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
                line.addView(View(this).apply { background = rounded(Settings.int(s.key), 10, cLine) }, LinearLayout.LayoutParams(dp(44), dp(28)))
                box.addView(line); box.background = ripple(); box.setOnClickListener { colorDialog(s) }
            }
            is ChoiceS -> {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val v = text(Settings.str(s.key), 14f, cAccent, true)
                line.addView(text(s.title, 15f, cText), LinearLayout.LayoutParams(0, -2, 1f)); line.addView(v)
                box.addView(line); box.background = ripple()
                box.setOnClickListener {
                    AlertDialog.Builder(this).setTitle(s.title).setItems(s.options.toTypedArray()) { _, i ->
                        if (s.key == "preset") { Presets.apply(s.options[i]); Settings.sp.edit().putString("preset", s.options[i]).apply(); refreshChrome() }
                        else { Settings.sp.edit().putString(s.key, s.options[i]).apply(); v.text = s.options[i] }
                    }.show()
                }
            }
            is TextS -> {
                val cur = Settings.str(s.key)
                box.addView(text(s.title, 15f, cText))
                box.addView(text(if (cur.isBlank()) "Default" else cur.lines().first().take(48), 12f, cSub))
                box.background = ripple(); box.setOnClickListener { textDialog(s) { build() } }
            }
        }
        if (s.hint.isNotEmpty() && s !is TextS) box.addView(text(s.hint, 11f, cSub))
        return box
    }

    private fun textDialog(s: TextS, done: () -> Unit) {
        val et = EditText(this).apply {
            setText(Settings.str(s.key)); hint = s.hint.ifEmpty { "Default" }
            inputType = if (s.multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
            minLines = if (s.multiline) 6 else 1; gravity = Gravity.TOP
        }
        AlertDialog.Builder(this).setTitle(s.title).setView(et)
            .setPositiveButton("Save") { _, _ -> Settings.sp.edit().putString(s.key, et.text.toString()).apply(); done() }
            .setNeutralButton("Default") { _, _ -> Settings.sp.edit().remove(s.key).apply(); done() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun colorDialog(s: ColorS) {
        var c = Settings.int(s.key)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), 0) }
        val hex = EditText(this).apply { setSingleLine(); setText(String.format("#%08X", c)) }
        val bars = ArrayList<SeekBar>()
        var syncing = false
        fun apply(newC: Int, fromHex: Boolean) {
            c = newC; Settings.sp.edit().putInt(s.key, c).apply()
            syncing = true
            if (!fromHex) hex.setText(String.format("#%08X", c))
            val v = intArrayOf(Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c))
            bars.forEachIndexed { i, b -> b.progress = v[i] }
            syncing = false
        }
        listOf("Alpha", "Red", "Green", "Blue").forEachIndexed { i, n ->
            box.addView(TextView(this).apply { text = n; textSize = 12f })
            val sb = SeekBar(this).apply {
                max = 255
                progress = intArrayOf(Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c))[i]
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) {
                        if (!user || syncing) return
                        val a = bars[0].progress; val r = bars[1].progress; val g = bars[2].progress; val bl = bars[3].progress
                        apply(Color.argb(a, r, g, bl), false)
                    }
                    override fun onStartTrackingTouch(b: SeekBar?) {}
                    override fun onStopTrackingTouch(b: SeekBar?) {}
                })
            }
            bars.add(sb); box.addView(sb)
        }
        hex.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) {
                if (syncing) return
                try { apply(Color.parseColor(e.toString().trim()), true) } catch (_: Exception) {}
            }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
        box.addView(hex)
        AlertDialog.Builder(this).setTitle(s.title).setView(box)
            .setPositiveButton("OK") { _, _ -> if (s.key == "seed_color") refreshChrome() else build() }
            .setNeutralButton("Auto") { _, _ -> Settings.sp.edit().remove(s.key).apply(); if (s.key == "seed_color") refreshChrome() else build() }
            .setOnCancelListener { if (s.key == "seed_color") refreshChrome() else build() }.show()
    }
}
