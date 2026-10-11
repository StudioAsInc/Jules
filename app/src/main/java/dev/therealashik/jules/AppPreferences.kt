package dev.therealashik.jules

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("jules_preferences", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    fun theme(): String = prefs.getString("theme", "SYSTEM") ?: "SYSTEM"
    fun setTheme(value: String) { prefs.edit().putString("theme", value).apply() }
    fun pageSize(): Int = prefs.getInt("page_size", 30).coerceIn(10, 100)
    fun setPageSize(value: Int) { prefs.edit().putInt("page_size", value.coerceIn(10, 100)).apply() }
    fun prompts(): List<SavedPrompt> = try { json.decodeFromString(prefs.getString("saved_prompts", "[]") ?: "[]") } catch (_: Exception) { emptyList() }
    fun setPrompts(value: List<SavedPrompt>) { prefs.edit().putString("saved_prompts", json.encodeToString(value)).apply() }
}
