package com.raad.browser.store

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** عنصر واحد في السجل أو العلامات المرجعية */
class StoreEntry(val url: String, val title: String, val time: Long)

/**
 * مخزن JSON بسيط داخل ملفات التطبيق الداخلية.
 * يستخدم للسجل والعلامات المرجعية بدون الحاجة لقاعدة بيانات.
 */
abstract class JsonEntryStore(private val fileName: String, private val maxEntries: Int) {

    private lateinit var file: File

    val entries = mutableStateListOf<StoreEntry>()

    fun init(context: Context) {
        file = File(context.filesDir, fileName)
        load()
    }

    private fun load() {
        if (!::file.isInitialized || !file.exists()) return
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                entries.add(StoreEntry(o.getString("url"), o.optString("title"), o.getLong("time")))
            }
        } catch (_: Exception) {
            // ملف تالف: نبدأ من جديد
        }
    }

    fun add(url: String, title: String) {
        if (url.isBlank() || url == "about:blank") return
        if (entries.firstOrNull()?.url == url) return // تجنب التكرار المتتالي
        entries.add(0, StoreEntry(url, title, System.currentTimeMillis()))
        while (entries.size > maxEntries) entries.removeAt(entries.size - 1)
        persist()
    }

    fun remove(entry: StoreEntry) {
        entries.remove(entry)
        persist()
    }

    fun clear() {
        entries.clear()
        persist()
    }

    protected fun persist() {
        try {
            val arr = JSONArray()
            entries.forEach {
                arr.put(
                    JSONObject()
                        .put("url", it.url)
                        .put("title", it.title)
                        .put("time", it.time)
                )
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}

/** سجل التصفح - آخر 500 صفحة */
object HistoryStore : JsonEntryStore("history.json", 500)

/** العلامات المرجعية - بدون حد عملي */
object BookmarkStore : JsonEntryStore("bookmarks.json", 10000) {

    fun contains(url: String): Boolean = entries.any { it.url == url }

    fun toggle(url: String, title: String) {
        val existing = entries.firstOrNull { it.url == url }
        if (existing != null) remove(existing) else add(url, title)
    }
}
