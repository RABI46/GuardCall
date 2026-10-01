package com.guardcall.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "guardcall_prefs")

class BlocklistStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val KEY_BLOCKLIST = stringPreferencesKey("guardcall.blocklist.v1")
    private val blocklistFile: File get() = File(context.filesDir, "blocklist.json")

    suspend fun load(): BlocklistPayload {
        // 1) DataStore
        val fromPrefs = context.dataStore.data.map { it[KEY_BLOCKLIST] }.first()
        if (fromPrefs != null) {
            try { return json.decodeFromString<BlocklistPayload>(fromPrefs) } catch (_: Exception) {}
        }
        // 2) Fichier JSON (utilisé aussi par le service en background)
        if (blocklistFile.exists()) {
            try { return json.decodeFromString(blocklistFile.readText()) } catch (_: Exception) {}
        }
        // 3) fallback
        return BlocklistPayload.DEFAULT
    }

    /** Version synchrone pour CallScreeningService (pas de coroutines). */
    fun loadSync(): BlocklistPayload {
        // Lecture fichier d'abord (la plus rapide en synchrone)
        if (blocklistFile.exists()) {
            try { return json.decodeFromString(blocklistFile.readText()) } catch (_: Exception) {}
        }
        // DataStore est async, on tente lecture directe SharedPreferences fallback
        val prefs = context.getSharedPreferences("guardcall_prefs_fallback", Context.MODE_PRIVATE)
        prefs.getString("guardcall.blocklist.json", null)?.let {
            try { return json.decodeFromString(it) } catch (_: Exception) {}
        }
        return BlocklistPayload.DEFAULT
    }

    suspend fun save(payload: BlocklistPayload) {
        val encoded = json.encodeToString(payload)
        context.dataStore.edit { it[KEY_BLOCKLIST] = encoded }
        // miroir fichier + prefs fallback pour service synchrone
        try { blocklistFile.writeText(encoded) } catch (_: Exception) {}
        context.getSharedPreferences("guardcall_prefs_fallback", Context.MODE_PRIVATE)
            .edit().putString("guardcall.blocklist.json", encoded).apply()
    }

    suspend fun addBlocked(phoneNumber: String, label: String? = null) {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.isBlank()) return
        val current = load()
        if (current.blocked.any { it.phoneNumber == digits }) return
        val updated = current.copy(
            blocked = (current.blocked + BlockedEntry(digits, label)).sortedBy { it.phoneNumber }
        )
        save(updated)
    }

    suspend fun removeBlocked(phoneNumber: String) {
        val current = load()
        save(current.copy(blocked = current.blocked.filterNot { it.phoneNumber == phoneNumber }))
    }

    suspend fun addIdentification(phoneNumber: String, label: String) {
        val digits = phoneNumber.filter { it.isDigit() }
        val current = load()
        val updated = current.copy(
            identified = (current.identified.filterNot { it.phoneNumber == digits } + IdentificationEntry(digits, label))
                .sortedBy { it.phoneNumber }
        )
        save(updated)
    }

    /** Normalise un numéro entrant pour comparaison (supprime espaces, +, -). */
    fun normalize(incoming: String?): String? {
        if (incoming.isNullOrBlank()) return null
        val digits = incoming.filter { it.isDigit() }
        // Garde les 10-15 derniers chiffres (gère indicatifs)
        return if (digits.length in 4..15) digits.takeLast(15) else digits.ifBlank { null }
    }

    fun isBlocked(incoming: String?, payload: BlocklistPayload = loadSync()): Boolean {
        val norm = normalize(incoming) ?: return false
        return payload.blocked.any { norm.endsWith(it.phoneNumber) || it.phoneNumber.endsWith(norm) }
    }

    fun identificationLabel(incoming: String?, payload: BlocklistPayload = loadSync()): String? {
        val norm = normalize(incoming) ?: return null
        return payload.identified.firstOrNull { norm.endsWith(it.phoneNumber) || it.phoneNumber.endsWith(norm) }?.label
    }
}
