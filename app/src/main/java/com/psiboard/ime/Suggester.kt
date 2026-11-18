package com.psiboard.ime

import android.content.Context
import java.io.File

/**
 * Tiny prediction engine: sorted word array + binary search (no trie objects -> low memory).
 * Dictionary files (assets/dict_<lang>.txt) list one word per line, most frequent first.
 * Drop a bigger list in to improve quality; nothing else changes.
 */
class Suggester(private val ctx: Context) {
    private class Dict(val words: Array<String>, val rank: IntArray)
    private val dicts = HashMap<String, Dict>()
    private val user = HashMap<String, Int>()
    private val bigram = HashMap<String, HashMap<String, Int>>()
    private val file = File(ctx.filesDir, "user_words.txt")
    private var dirty = 0

    init { loadUser() }

    private fun dict(lang: String): Dict = dicts.getOrPut(lang) {
        val list = try { ctx.assets.open("dict_$lang.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() } } catch (_: Exception) { emptyList() }
        val idx = list.indices.sortedBy { list[it] }
        Dict(Array(idx.size) { list[idx[it]] }, IntArray(idx.size) { idx[it] })
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
            var i = lower(d.words, w); var seen = 0
            while (i < d.words.size && d.words[i].startsWith(w) && seen < 400) {
                cands[d.words[i]] = 500f - d.rank[i] * 0.5f - (d.words[i].length - w.length); i++; seen++
            }
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
