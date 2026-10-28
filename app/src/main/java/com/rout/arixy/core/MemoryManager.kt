package com.rout.arixy.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-controlled long-term memory. Entries are stored on-device (encrypted prefs)
 * and are always viewable, editable and deletable from the Memory tab.
 * Encode/decode are pure functions for unit tests.
 */
object MemoryManager {
    private const val KEY = "memories_json"
    private const val KEY_ENABLED = "memory_enabled"

    fun encode(entries: List<MemoryEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("text", e.text)
                put("createdAt", e.createdAt)
            })
        }
        return arr.toString()
    }

    fun decode(json: String): List<MemoryEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString("text", "").trim()
                if (text.isEmpty()) return@mapNotNull null
                MemoryEntry(
                    id = o.optLong("id", System.currentTimeMillis() + i),
                    text = text,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun prefs(context: Context) = KeyStore.raw(context)

    fun list(context: Context): List<MemoryEntry> =
        decode(prefs(context).getString(KEY, "[]") ?: "[]")

    fun add(context: Context, text: String): MemoryEntry? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val entry = MemoryEntry(System.currentTimeMillis(), t.take(500), System.currentTimeMillis())
        val all = list(context) + entry
        prefs(context).edit().putString(KEY, encode(all)).apply()
        return entry
    }

    fun update(context: Context, id: Long, newText: String): Boolean {
        val t = newText.trim()
        if (t.isEmpty()) return false
        val all = list(context).map { if (it.id == id) it.copy(text = t.take(500)) else it }
        prefs(context).edit().putString(KEY, encode(all)).apply()
        return true
    }

    fun delete(context: Context, id: Long) {
        val all = list(context).filterNot { it.id == id }
        prefs(context).edit().putString(KEY, encode(all)).apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().putString(KEY, "[]").apply()
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** Memories injected into the system prompt (empty when disabled). */
    fun activeMemories(context: Context): List<String> =
        if (isEnabled(context)) list(context).map { it.text } else emptyList()
}
