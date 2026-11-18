package com.psiboard.ime

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ClipItem(val id: Long, val text: String, val pinned: Boolean, val time: Long)

class ClipboardStore(ctx: Context) : SQLiteOpenHelper(ctx, "clip.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE clips(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, pinned INTEGER DEFAULT 0, time INTEGER)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    /** Returns true if stored. All filtering is driven by settings. */
    fun add(raw: String): Boolean {
        var t = raw
        if (Settings.bool("clip_trim")) t = t.trim()
        if (t.length < Settings.int("clip_min_len") || t.length > Settings.int("clip_max_len") || t.isBlank()) return false
        val db = writableDatabase
        if (Settings.bool("clip_dedupe")) {
            val c = db.rawQuery("SELECT id,pinned FROM clips WHERE text=?", arrayOf(t))
            val found = if (c.moveToFirst()) c.getLong(0) else -1L
            c.close()
            if (found >= 0) { db.execSQL("UPDATE clips SET time=? WHERE id=?", arrayOf(System.currentTimeMillis(), found)); return true }
        }
        db.insert("clips", null, ContentValues().apply { put("text", t); put("time", System.currentTimeMillis()) })
        prune()
        return true
    }

    fun list(): List<ClipItem> {
        val order = if (Settings.bool("clip_pinned_first")) "pinned DESC, time DESC" else "time DESC"
        val c = readableDatabase.rawQuery("SELECT id,text,pinned,time FROM clips ORDER BY $order", null)
        val out = ArrayList<ClipItem>()
        while (c.moveToNext()) out.add(ClipItem(c.getLong(0), c.getString(1), c.getInt(2) == 1, c.getLong(3)))
        c.close(); return out
    }

    fun setPinned(id: Long, p: Boolean) { writableDatabase.execSQL("UPDATE clips SET pinned=? WHERE id=?", arrayOf(if (p) 1 else 0, id)) }
    fun delete(id: Long) { writableDatabase.delete("clips", "id=?", arrayOf(id.toString())) }
    fun clearUnpinned() { writableDatabase.delete("clips", "pinned=0", null) }

    fun prune() {
        val db = writableDatabase
        val hours = Settings.int("clip_hours")
        if (hours > 0) db.delete("clips", "pinned=0 AND time<?", arrayOf((System.currentTimeMillis() - hours * 3600_000L).toString()))
        db.execSQL("DELETE FROM clips WHERE pinned=0 AND id NOT IN (SELECT id FROM clips WHERE pinned=0 ORDER BY time DESC LIMIT ${Settings.int("clip_max")})")
    }
}
