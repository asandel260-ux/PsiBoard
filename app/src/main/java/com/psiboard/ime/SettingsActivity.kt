package com.psiboard.ime

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
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
    private val collapsed = HashSet<String>()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Settings.init(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preview = KeyboardView(this).also { it.shift = 0; it.spaceLabel = "Preview"; it.isEnabled = false }
        root.addView(preview, LinearLayout.LayoutParams(-1, -2))
        val search = EditText(this).apply {
            hint = "Search ${Settings.all.size} settings…"; setSingleLine(); setPadding(dp(14), dp(8), dp(14), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s.toString().trim().lowercase(); build() }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        Settings.all.map { it.section }.distinct().drop(0).forEach { collapsed.add(it) }
        collapsed.remove(Settings.all.first().section)
        build(); refreshPreview()
    }

    override fun onResume() { super.onResume(); Settings.sp.registerOnSharedPreferenceChangeListener(this) }
    override fun onPause() { Settings.sp.unregisterOnSharedPreferenceChangeListener(this); super.onPause() }
    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = refreshPreview()

    private fun refreshPreview() {
        preview.applyTheme(Theme(this))
        preview.submitRows(Layouts.build(Settings.str("default_lang"), 0, false))
    }

    private fun build() {
        list.removeAllViews()
        if (query.isEmpty()) {
            list.addView(actionRow("Enable keyboard in system settings") { startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) })
            list.addView(actionRow("Switch to PsiBoard (input method picker)") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })
            list.addView(actionRow("Export all settings → copy JSON to clipboard") {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("psiboard", Settings.exportJson()))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            })
            list.addView(actionRow("Import settings (paste JSON)") { importDialog() })
            list.addView(actionRow("Forget learned words") { Suggester(this).forgetAll(); Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show() })
            list.addView(actionRow("Reset everything to defaults") {
                AlertDialog.Builder(this).setMessage("Reset all settings?").setPositiveButton("Reset") { _, _ -> Settings.resetAll(); build() }.setNegativeButton("Cancel", null).show()
            })
        }
        var lastSection = ""
        for (s in Settings.all) {
            if (query.isNotEmpty() && !s.title.lowercase().contains(query) && !s.section.lowercase().contains(query)) continue
            if (s.section != lastSection) {
                lastSection = s.section
                val sec = s.section
                list.addView(TextView(this).apply {
                    text = (if (collapsed.contains(sec) && query.isEmpty()) "▸ " else "▾ ") + sec.uppercase()
                    textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(14), dp(18), dp(14), dp(6))
                    setOnClickListener { if (!collapsed.remove(sec)) collapsed.add(sec); build() }
                })
            }
            if (query.isEmpty() && collapsed.contains(s.section)) continue
            list.addView(row(s))
        }
    }

    private fun actionRow(label: String, on: () -> Unit) = TextView(this).apply {
        text = label; textSize = 15f; setPadding(dp(14), dp(12), dp(14), dp(12)); setOnClickListener { on() }
    }

    private fun importDialog() {
        val et = EditText(this).apply { hint = "Paste JSON"; minLines = 4 }
        AlertDialog.Builder(this).setView(et).setPositiveButton("Import") { _, _ ->
            try { Settings.importJson(et.text.toString()); build() } catch (e: Exception) { Toast.makeText(this, "Invalid JSON", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun row(s: S): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8)) }
        fun title(v: String = s.title) = TextView(this).apply { text = v; textSize = 15f }
        when (s) {
            is SwitchS -> {
                val sw = Switch(this).apply { text = s.title; textSize = 15f; isChecked = Settings.bool(s.key)
                    setOnCheckedChangeListener { _, c -> Settings.sp.edit().putBoolean(s.key, c).apply() } }
                box.addView(sw)
            }
            is SliderS -> {
                val tv = title("${s.title}: ${Settings.int(s.key)}${s.unit}")
                val sb = SeekBar(this).apply {
                    max = s.max - s.min; progress = Settings.int(s.key) - s.min
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) {
                            tv.text = "${s.title}: ${p + s.min}${s.unit}"
                            if (user) Settings.sp.edit().putInt(s.key, p + s.min).apply()
                        }
                        override fun onStartTrackingTouch(b: SeekBar?) {}
                        override fun onStopTrackingTouch(b: SeekBar?) {}
                    })
                }
                box.addView(tv); box.addView(sb)
            }
            is ColorS -> {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val sw = View(this).apply { setBackgroundColor(Settings.int(s.key)) }
                line.addView(title(), LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(sw, LinearLayout.LayoutParams(dp(44), dp(28)))
                box.addView(line); box.setOnClickListener { colorDialog(s) }
            }
            is ChoiceS -> {
                val tv = title("${s.title}: ${Settings.str(s.key)}")
                box.addView(tv)
                box.setOnClickListener {
                    AlertDialog.Builder(this).setTitle(s.title).setItems(s.options.toTypedArray()) { _, i ->
                        if (s.key == "preset") { Presets.apply(s.options[i]); Settings.sp.edit().putString("preset", s.options[i]).apply(); build() }
                        else { Settings.sp.edit().putString(s.key, s.options[i]).apply(); tv.text = "${s.title}: ${s.options[i]}" }
                    }.show()
                }
            }
            is TextS -> {
                val cur = Settings.str(s.key)
                box.addView(title())
                box.addView(TextView(this).apply { text = if (cur.isBlank()) "(default)" else cur.lines().first().take(48); textSize = 12f; alpha = 0.6f })
                box.setOnClickListener { textDialog(s) { build() } }
            }
        }
        if (s.hint.isNotEmpty() && s !is TextS) box.addView(TextView(this).apply { text = s.hint; textSize = 11f; alpha = 0.6f })
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
            .setPositiveButton("OK") { _, _ -> build() }
            .setNeutralButton("Default") { _, _ -> Settings.sp.edit().remove(s.key).apply(); build() }
            .setOnCancelListener { build() }.show()
    }
}
