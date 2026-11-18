package com.psiboard.ime

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun roundBg(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r }

private fun headerButton(c: Context, t: Theme, label: String, onClick: () -> Unit) = TextView(c).apply {
    text = label; setTextColor(t.stripIcon); textSize = 14f; gravity = Gravity.CENTER
    setPadding(c.dp(12), 0, c.dp(12), 0); setOnClickListener { onClick() }
}

class ClipboardPanel(ctx: Context, private val store: ClipboardStore, private val onPaste: (String) -> Unit,
                     private val onBack: () -> Unit, private val onFeedback: () -> Unit) : LinearLayout(ctx) {
    private var showSnippets = false
    private val list = LinearLayout(ctx).apply { orientation = VERTICAL }
    private val header = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(list) }
    var th = Theme(ctx)

    init { orientation = VERTICAL; addView(header, LayoutParams(-1, ctx.dp(40))); addView(scroll, LayoutParams(-1, 0, 1f)) }

    fun refresh(t: Theme) {
        th = t; setBackgroundColor(t.bg)
        header.removeAllViews()
        header.addView(headerButton(context, t, "ABC") { onBack() }, LayoutParams(-2, -1))
        header.addView(headerButton(context, t, if (showSnippets) "Snippets ●" else "Snippets") { showSnippets = true; refresh(th) }, LayoutParams(-2, -1))
        header.addView(headerButton(context, t, if (!showSnippets) "History ●" else "History") { showSnippets = false; refresh(th) }, LayoutParams(-2, -1))
        header.addView(View(context), LayoutParams(0, 1, 1f))
        if (!showSnippets) header.addView(headerButton(context, t, "Clear") { clearAll() }, LayoutParams(-2, -1))
        list.removeAllViews()
        val cols = Settings.int("clip_columns")
        val gap = context.dp(Settings.int("clip_card_gap"))
        val items: List<ClipItem> = if (showSnippets)
            Settings.str("clip_snippets").lines().filter { it.isNotBlank() }.mapIndexed { i, s -> ClipItem(-1L - i, s, true, 0L) }
        else store.list()
        if (items.isEmpty()) {
            list.addView(TextView(context).apply { text = if (showSnippets) "Add snippets in Settings → Clipboard" else "Nothing copied yet"; setTextColor(Settings.int("c_clip_time")); gravity = Gravity.CENTER; setPadding(0, context.dp(30), 0, 0) })
            return
        }
        var row: LinearLayout? = null
        items.forEachIndexed { i, it ->
            if (i % cols == 0) { row = LinearLayout(context).apply { orientation = HORIZONTAL; setPadding(gap, gap / 2, gap, gap / 2) }; list.addView(row) }
            row!!.addView(card(it, gap), LayoutParams(0, -2, 1f).apply { setMargins(gap / 2, 0, gap / 2, 0) })
        }
        // fill empty cells so the last row keeps column widths
        val rem = (cols - items.size % cols) % cols
        repeat(rem) { row!!.addView(View(context), LayoutParams(0, 1, 1f)) }
    }

    private fun card(item: ClipItem, gap: Int): View {
        val c = context
        val box = LinearLayout(c).apply {
            orientation = HORIZONTAL
            background = roundBg(Settings.int("c_clip_card"), Settings.int("clip_card_radius") * c.resources.displayMetrics.density)
            setPadding(c.dp(10), c.dp(8), c.dp(6), c.dp(8))
            setOnClickListener { onFeedback(); onPaste(item.text) }
        }
        val col = LinearLayout(c).apply { orientation = VERTICAL }
        col.addView(TextView(c).apply {
            text = item.text; setTextColor(Settings.int("c_clip_text")); textSize = Settings.int("clip_text_size").toFloat()
            maxLines = Settings.int("clip_lines"); ellipsize = TextUtils.TruncateAt.END
        })
        if (Settings.bool("clip_show_time") && item.time > 0) col.addView(TextView(c).apply {
            text = ago(item.time); setTextColor(Settings.int("c_clip_time")); textSize = 10f
        })
        box.addView(col, LayoutParams(0, -2, 1f))
        if (item.id >= 0) {
            val side = LinearLayout(c).apply { orientation = VERTICAL }
            side.addView(TextView(c).apply {
                text = if (item.pinned) "★" else "☆"; textSize = 16f; setPadding(c.dp(6), 0, c.dp(6), c.dp(4))
                setTextColor(if (item.pinned) Settings.int("c_clip_pin") else Settings.int("c_clip_time"))
                setOnClickListener { _ -> store.setPinned(item.id, !item.pinned); refresh(th) }
            })
            side.addView(TextView(c).apply {
                text = "✕"; textSize = 14f; setTextColor(Settings.int("c_clip_time")); setPadding(c.dp(6), 0, c.dp(6), 0)
                setOnClickListener { _ -> store.delete(item.id); refresh(th) }
            })
            box.addView(side, LayoutParams(-2, -2))
        }
        return box
    }

    private var confirmAt = 0L
    private fun clearAll() {
        if (Settings.bool("clip_confirm_clear") && System.currentTimeMillis() - confirmAt > 3000) {
            confirmAt = System.currentTimeMillis()
            Toast.makeText(context, "Tap Clear again to delete unpinned items", Toast.LENGTH_SHORT).show(); return
        }
        store.clearUnpinned(); refresh(th)
    }

    private fun ago(t: Long): String {
        val m = (System.currentTimeMillis() - t) / 60000
        return when { m < 1 -> "now"; m < 60 -> "${m}m"; m < 1440 -> "${m / 60}h"; else -> "${m / 1440}d" }
    }
}

class EmojiPanel(ctx: Context, private val onEmoji: (String) -> Unit, private val onBack: () -> Unit, private val onDelete: () -> Unit) : LinearLayout(ctx) {
    private val cats = listOf(
        "🕘" to null,
        "😀" to listOf(0x1F600..0x1F64F),
        "👍" to listOf(0x1F446..0x1F450, 0x1F44A..0x1F44F, 0x1F64B..0x1F64F, 0x1F9D1..0x1F9DD),
        "🐶" to listOf(0x1F400..0x1F43E, 0x1F980..0x1F9AE),
        "🍔" to listOf(0x1F345..0x1F37F, 0x1F950..0x1F96B),
        "⚽" to listOf(0x1F3A0..0x1F3CA, 0x1F680..0x1F6A4),
        "💡" to listOf(0x1F4A1..0x1F4FF),
        "❤" to listOf(0x1F493..0x1F49F, 0x2763..0x2764, 0x2600..0x2650, 0x2B50..0x2B55)
    )
    private val grid = LinearLayout(ctx).apply { orientation = VERTICAL }
    private val tabs = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(grid) }
    private val probe = Paint()
    private var cur = 1

    init { orientation = VERTICAL; addView(tabs, LayoutParams(-1, ctx.dp(40))); addView(scroll, LayoutParams(-1, 0, 1f)) }

    private fun recent(): List<String> = Settings.sp.getString("emoji_recent_list", "")!!.split(' ').filter { it.isNotEmpty() }

    fun noteUsed(e: String) {
        val l = (listOf(e) + recent().filter { it != e }).take(Settings.int("emoji_recent"))
        Settings.sp.edit().putString("emoji_recent_list", l.joinToString(" ")).apply()
    }

    fun refresh(t: Theme) {
        setBackgroundColor(t.bg)
        tabs.removeAllViews()
        tabs.addView(headerButton(context, t, "ABC") { onBack() }, LayoutParams(-2, -1))
        cats.forEachIndexed { i, c ->
            tabs.addView(TextView(context).apply {
                text = c.first; textSize = 18f; gravity = Gravity.CENTER; alpha = if (i == cur) 1f else 0.5f
                setOnClickListener { cur = i; refresh(t) }
            }, LayoutParams(0, -1, 1f))
        }
        tabs.addView(headerButton(context, t, "⌫") { onDelete() }, LayoutParams(-2, -1))
        grid.removeAllViews()
        val items = if (cats[cur].second == null) recent() else cats[cur].second!!.flatMap { r -> r.map { String(Character.toChars(it)) } }.filter { Build_hasGlyph(it) }
        val cols = Settings.int("emoji_cols"); val size = Settings.int("emoji_size").toFloat()
        var row: LinearLayout? = null
        items.forEachIndexed { i, e ->
            if (i % cols == 0) { row = LinearLayout(context).apply { orientation = HORIZONTAL }; grid.addView(row) }
            row!!.addView(TextView(context).apply {
                text = e; textSize = size; gravity = Gravity.CENTER; setPadding(0, context.dp(6), 0, context.dp(6))
                setOnClickListener { noteUsed(e); onEmoji(e) }
            }, LayoutParams(0, -2, 1f))
        }
    }

    private fun Build_hasGlyph(s: String) = if (android.os.Build.VERSION.SDK_INT >= 23) probe.hasGlyph(s) else true
}
