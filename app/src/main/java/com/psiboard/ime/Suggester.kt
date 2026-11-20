package com.psiboard.ime

import android.content.Context
import java.io.File

/**
 * Tiny prediction engine: sorted word array + binary search (no trie objects -> low memory).
 * Dictionary files (assets/dict_<lang>.txt) list one word per line, most frequent first.
 * CI swaps in bigger lists at build time (.github/scripts/fetch_dicts.sh); nothing else changes.
 * Lists load on a background thread when the keyboard starts, so the first key press never waits for disk.
 */
class Suggester(private val ctx: Context) {
    private class Dict(val words: Array<String>, val rank: IntArray)
    private val dicts = HashMap<String, Dict>()
    private val user = HashMap<String, Int>()
    private val bigram = HashMap<String, HashMap<String, Int>>()
    private val file = File(ctx.filesDir, "user_words.txt")
    private var dirty = 0

    init { loadUser() }

    /** Load the enabled languages' word lists off the main thread (called once by the keyboard service). */
    fun preload() {
        Thread {
            try { for (l in Settings.langs) if (Settings.bool("lang_$l")) dict(l) } catch (_: Throwable) {}
        }.apply { priority = Thread.MIN_PRIORITY; isDaemon = true; start() }
    }

    private fun dict(lang: String): Dict = synchronized(dicts) { dicts.getOrPut(lang) { load(lang) } }

    private fun load(lang: String): Dict {
        val list = try {
            ctx.assets.open("dict_$lang.txt").bufferedReader().use { r -> r.readLines().map { it.trim() }.filter { it.isNotEmpty() } }
        } catch (_: Exception) { emptyList() }
        val idx = list.indices.sortedBy { list[it] }
        return Dict(Array(idx.size) { list[idx[it]] }, IntArray(idx.size) { idx[it] })
    }

    private fun lower(bound: Array<String>, p: String): Int {
        var lo = 0; var hi = bound.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (bound[m] < p) lo = m + 1 else hi = m }
        return lo
    }

    fun isKnown(lang: String, w: String): Boolean {
        val l = w.lowercase(); val d = dict(lang)
        val i = lower(d.words, l)
        return (i < d.words.size && d.words[i] == l) || (user[l] ?: 0) >= 2
    }

    fun suggest(lang: String, wordIn: String, prev: String?, n: Int): List<String> {
        val w = wordIn.lowercase()
        val cands = HashMap<String, Float>()
        if (w.isEmpty()) {
            if (prev != null && Settings.bool("next_word")) bigram[prev.lowercase()]?.forEach { (k, v) -> cands[k] = 1000f + v }
        } else {
            val d = dict(lang)
            // every word that starts with the typed prefix is one contiguous block of the sorted array; keep the best few
            val top = 12
            val bw = arrayOfNulls<String>(top); val bs = FloatArray(top) { Float.NEGATIVE_INFINITY }
            var i = lower(d.words, w)
            while (i < d.words.size && d.words[i].startsWith(w)) {
                val c = d.words[i]
                val sc = 500f - d.rank[i] * 0.5f - (c.length - w.length)
                if (sc > bs[top - 1]) {
                    var j = top - 1
                    while (j > 0 && bs[j - 1] < sc) { bs[j] = bs[j - 1]; bw[j] = bw[j - 1]; j-- }
                    bs[j] = sc; bw[j] = c
                }
                i++
            }
            for (j in 0 until top) { val c = bw[j] ?: break; cands[c] = bs[j] }
            for ((k, v) in user) if (k.startsWith(w) && k != w) cands[k] = (cands[k] ?: 0f) + 600f + v
            if (prev != null) bigram[prev.lowercase()]?.forEach { (k, v) -> if (k.startsWith(w)) cands[k] = (cands[k] ?: 0f) + 300f + v }
        }
        val cap = wordIn.isNotEmpty() && wordIn[0].isUpperCase()
        val all = wordIn.length > 1 && wordIn.all { it.isUpperCase() }
        return cands.entries.sortedByDescending { it.value }.take(n).map { if (all) it.key.uppercase() else if (cap) it.key.replaceFirstChar { c -> c.uppercase() } else it.key }
    }

    /** Edit-distance-1 correction for unknown words; null if nothing confident. */
    fun correct(lang: String, wordIn: String): String? {
        val w = wordIn.lowercase()
        if (w.length < 3 || isKnown(lang, w)) return null
        val d = dict(lang); var best: String? = null; var bestRank = Int.MAX_VALUE
        for (i in d.words.indices) {
            val c = d.words[i]
            if (kotlin.math.abs(c.length - w.length) > 1 || c[0] != w[0]) continue
            if (d.rank[i] < bestRank && within1(w, c)) { best = c; bestRank = d.rank[i] }
        }
        return best?.let { if (wordIn[0].isUpperCase()) it.replaceFirstChar { c -> c.uppercase() } else it }
    }

    private fun within1(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length == b.length) {
            var diff = 0; var i = 0
            while (i < a.length) { if (a[i] != b[i]) { diff++; if (diff == 1 && i + 1 < a.length && a[i] == b[i + 1] && a[i + 1] == b[i]) { i++ } else if (diff > 1) return false }; i++ }
            return true
        }
        val (s, l) = if (a.length < b.length) a to b else b to a
        var i = 0; var j = 0; var skipped = false
        while (i < s.length && j < l.length) { if (s[i] == l[j]) { i++; j++ } else { if (skipped) return false; skipped = true; j++ } }
        return true
    }

    fun learn(lang: String, word: String, prev: String?) {
        if (!Settings.bool("learn_words") || Settings.bool("incognito") || word.length < 2) return
        val w = word.lowercase()
        user[w] = (user[w] ?: 0) + 1
        if (prev != null) { val m = bigram.getOrPut(prev.lowercase()) { HashMap() }; m[w] = (m[w] ?: 0) + 1 }
        if (++dirty >= 20) save()
    }

    fun forgetAll() { user.clear(); bigram.clear(); file.delete() }

    fun save() {
        dirty = 0
        try {
            file.bufferedWriter().use { o ->
                user.entries.sortedByDescending { it.value }.take(5000).forEach { o.write("u\t${it.key}\t${it.value}\n") }
                var n = 0
                for ((p, m) in bigram) for ((k, v) in m) { if (n++ < 8000) o.write("b\t$p\t$k\t$v\n") }
            }
        } catch (_: Exception) {}
    }

    private fun loadUser() {
        try {
            if (!file.exists()) return
            file.forEachLine { l ->
                val t = l.split('\t')
                if (t[0] == "u" && t.size == 3) user[t[1]] = t[2].toIntOrNull() ?: 1
                else if (t[0] == "b" && t.size == 4) bigram.getOrPut(t[1]) { HashMap() }[t[2]] = t[3].toIntOrNull() ?: 1
            }
        } catch (_: Exception) {}
    }
}
