package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.FloorLevelConfig
import com.example.util.TimeHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wolne_sale_prefs")

data class AppPreferences(
    val timetableUrl: String = SettingsDataStore.DEFAULT_TIMETABLE_URL,
    val substitutionsUrl: String = SettingsDataStore.DEFAULT_SUBSTITUTIONS_URL,
    val autoRefreshMinutes: Int = 5,
    val cleanedRoomsForToday: Set<String> = emptySet(),
    val floorLevels: List<FloorLevelConfig> = SettingsDataStore.DEFAULT_FLOOR_LEVELS,
    val githubRepo: String = "",
    val autoCheckUpdates: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    companion object {
        const val DEFAULT_TIMETABLE_URL = "https://plan.zsme.pl"
        const val DEFAULT_SUBSTITUTIONS_URL = "https://sekretariat.zsme.pl/tv.php"

        val DEFAULT_FLOOR_LEVELS: List<FloorLevelConfig> = listOf(
            FloorLevelConfig("basement", "Piwnica", listOf("1-9")),
            FloorLevelConfig("ground", "Parter", listOf("10-29")),
            FloorLevelConfig("first", "1. piętro", listOf("30-39")),
            FloorLevelConfig("second", "2. piętro", listOf("40-57")),
            FloorLevelConfig("third", "3. piętro", listOf("58-69")),
            FloorLevelConfig("workshops", "Warsztaty", listOf("W*"))
        )

        private val KEY_TIMETABLE_URL = stringPreferencesKey("timetable_url")
        private val KEY_SUBSTITUTIONS_URL = stringPreferencesKey("substitutions_url")
        private val KEY_AUTO_REFRESH_MINUTES = intPreferencesKey("auto_refresh_minutes")
        private val KEY_CLEANED_ROOMS_DATE = stringPreferencesKey("cleaned_rooms_date")
        private val KEY_CLEANED_ROOMS_SET = stringSetPreferencesKey("cleaned_rooms_set")
        private val KEY_FLOOR_LEVELS_JSON = stringPreferencesKey("floor_levels_json")
        private val KEY_GITHUB_REPO = stringPreferencesKey("github_repo")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val todayIso = TimeHelper.todayDateInfo().isoKey
        val savedDate = prefs[KEY_CLEANED_ROOMS_DATE]
        val cleanedRooms = if (savedDate == todayIso) {
            prefs[KEY_CLEANED_ROOMS_SET] ?: emptySet()
        } else {
            emptySet()
        }

        val floorLevels = prefs[KEY_FLOOR_LEVELS_JSON]
            ?.let { decodeFloorLevels(it) }
            ?: DEFAULT_FLOOR_LEVELS

        AppPreferences(
            timetableUrl = prefs[KEY_TIMETABLE_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_TIMETABLE_URL,
            substitutionsUrl = prefs[KEY_SUBSTITUTIONS_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_SUBSTITUTIONS_URL,
            autoRefreshMinutes = prefs[KEY_AUTO_REFRESH_MINUTES] ?: 5,
            cleanedRoomsForToday = cleanedRooms,
            floorLevels = floorLevels,
            githubRepo = prefs[KEY_GITHUB_REPO]?.trim().orEmpty(),
            autoCheckUpdates = prefs[KEY_AUTO_CHECK_UPDATES] ?: true
        )
    }

    suspend fun updateGithubSettings(repo: String, autoCheck: Boolean) {
        val normalized = com.example.update.GitHubUpdateManager.normalizeRepoSlug(repo) ?: repo.trim()
        context.dataStore.edit { prefs ->
            prefs[KEY_GITHUB_REPO] = normalized
            prefs[KEY_AUTO_CHECK_UPDATES] = autoCheck
        }
    }

    suspend fun updateUrls(timetableUrl: String, substitutionsUrl: String, autoRefreshMinutes: Int) {
        val cleanTimetable = normalizeUrl(timetableUrl, DEFAULT_TIMETABLE_URL)
        val cleanSubstitutions = normalizeUrl(substitutionsUrl, DEFAULT_SUBSTITUTIONS_URL)
        context.dataStore.edit { prefs ->
            prefs[KEY_TIMETABLE_URL] = cleanTimetable
            prefs[KEY_SUBSTITUTIONS_URL] = cleanSubstitutions
            prefs[KEY_AUTO_REFRESH_MINUTES] = autoRefreshMinutes
        }
    }

    suspend fun resetDefaultUrls() {
        context.dataStore.edit { prefs ->
            prefs[KEY_TIMETABLE_URL] = DEFAULT_TIMETABLE_URL
            prefs[KEY_SUBSTITUTIONS_URL] = DEFAULT_SUBSTITUTIONS_URL
        }
    }

    suspend fun toggleRoomCleaned(roomName: String) {
        val todayIso = TimeHelper.todayDateInfo().isoKey
        context.dataStore.edit { prefs ->
            val current = if (prefs[KEY_CLEANED_ROOMS_DATE] == todayIso) {
                prefs[KEY_CLEANED_ROOMS_SET]?.toMutableSet() ?: mutableSetOf()
            } else {
                mutableSetOf()
            }
            if (!current.add(roomName)) {
                current.remove(roomName)
            }
            prefs[KEY_CLEANED_ROOMS_DATE] = todayIso
            prefs[KEY_CLEANED_ROOMS_SET] = current
        }
    }

    suspend fun clearCleanedRooms() {
        val todayIso = TimeHelper.todayDateInfo().isoKey
        context.dataStore.edit { prefs ->
            prefs[KEY_CLEANED_ROOMS_DATE] = todayIso
            prefs[KEY_CLEANED_ROOMS_SET] = emptySet()
        }
    }

    suspend fun addFloorLevel(name: String, initialRules: List<String> = emptyList()) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        editFloorLevels { list ->
            list + FloorLevelConfig(
                id = "lvl_${UUID.randomUUID().toString().take(8)}",
                name = cleanName,
                rules = initialRules.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            )
        }
    }

    suspend fun renameFloorLevel(levelId: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return
        editFloorLevels { list ->
            list.map { if (it.id == levelId) it.copy(name = cleanName) else it }
        }
    }

    suspend fun deleteFloorLevel(levelId: String) {
        editFloorLevels { list ->
            list.filterNot { it.id == levelId }
        }
    }

    suspend fun addRuleToLevel(levelId: String, rawRule: String) {
        val tokens = rawRule.split(",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        editFloorLevels { list ->
            list.map { lvl ->
                if (lvl.id == levelId) {
                    val updated = (lvl.rules + tokens).distinctBy { it.lowercase() }
                    lvl.copy(rules = updated)
                } else {
                    lvl
                }
            }
        }
    }

    suspend fun removeRuleFromLevel(levelId: String, rule: String) {
        editFloorLevels { list ->
            list.map { lvl ->
                if (lvl.id == levelId) {
                    lvl.copy(rules = lvl.rules.filterNot { it.equals(rule, ignoreCase = true) })
                } else {
                    lvl
                }
            }
        }
    }

    suspend fun resetDefaultFloorLevels() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOOR_LEVELS_JSON] = encodeFloorLevels(DEFAULT_FLOOR_LEVELS)
        }
    }

    private suspend fun editFloorLevels(transform: (List<FloorLevelConfig>) -> List<FloorLevelConfig>) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FLOOR_LEVELS_JSON]
                ?.let { decodeFloorLevels(it) }
                ?: DEFAULT_FLOOR_LEVELS
            prefs[KEY_FLOOR_LEVELS_JSON] = encodeFloorLevels(transform(current))
        }
    }

    private fun encodeFloorLevels(levels: List<FloorLevelConfig>): String {
        val arr = JSONArray()
        for (lvl in levels) {
            val obj = JSONObject()
            obj.put("id", lvl.id)
            obj.put("name", lvl.name)
            val rulesArr = JSONArray()
            lvl.rules.forEach { rulesArr.put(it) }
            obj.put("rules", rulesArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeFloorLevels(json: String): List<FloorLevelConfig>? {
        return runCatching {
            val arr = JSONArray(json)
            val result = mutableListOf<FloorLevelConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id").ifBlank { "lvl_$i" }
                val name = obj.optString("name").trim()
                if (name.isBlank()) continue
                val rulesArr = obj.optJSONArray("rules") ?: JSONArray()
                val rules = mutableListOf<String>()
                for (j in 0 until rulesArr.length()) {
                    val r = rulesArr.optString(j).trim()
                    if (r.isNotEmpty()) rules.add(r)
                }
                result.add(FloorLevelConfig(id = id, name = name, rules = rules))
            }
            result
        }.getOrNull()
    }

    private fun normalizeUrl(raw: String, fallback: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return fallback
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
