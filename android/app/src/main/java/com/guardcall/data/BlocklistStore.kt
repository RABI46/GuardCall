package com.guardcall.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistance de la liste des numéros.
 *
 * Le **fichier JSON est l'unique source de vérité**. Une précédente version
 * écrivait dans DataStore + fichier + SharedPreferences et lisait des sources
 * différentes selon l'appelant (`load()` lisait DataStore, `loadSync()` le
 * fichier) : l'UI et le service de filtrage pouvaient donc diverger. Les échecs
 * d'écriture ne sont plus avalés — ils remontent en [MutationResult.StorageFailure].
 *
 * Toutes les méthodes `suspend` déplacent l'E/S sur [Dispatchers.IO] ;
 * [loadNow] et [saveNow] sont synchrones et doivent être appelées hors thread
 * principal (c'est ce que fait le service de filtrage via son propre executor).
 */
class BlocklistStore(private val context: Context) {

    private val gson = Gson()

    private val blocklistFile: File
        get() = File(context.filesDir, BLOCKLIST_FILE)

    // MARK: - Lecture

    /** Synchrone — à n'appeler que hors thread principal. */
    fun loadNow(): BlocklistPayload {
        val file = blocklistFile
        if (!file.exists()) return BlocklistPayload.EMPTY

        val payload = try {
            gson.fromJson(file.readText(), BlocklistPayload::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Liste illisible ($file) — liste vide utilisée", e)
            null
        } ?: return BlocklistPayload.EMPTY

        // Les données héritées peuvent contenir des numéros au format national :
        // on les renormalise au chargement pour que la comparaison E.164 fonctionne.
        return payload.normalized()
    }

    suspend fun load(): BlocklistPayload = withContext(Dispatchers.IO) { loadNow() }

    // MARK: - Écriture

    /** Synchrone — à n'appeler que hors thread principal. */
    fun saveNow(payload: BlocklistPayload): MutationResult {
        val sanitized = payload.sanitized()
        val encoded = try {
            gson.toJson(sanitized)
        } catch (e: Exception) {
            Log.e(TAG, "Encodage JSON impossible", e)
            return MutationResult.StorageFailure("Encodage de la liste impossible : ${e.message}")
        }

        val target = blocklistFile
        val temp = File(target.parentFile, "${target.name}.tmp")
        return try {
            temp.writeText(encoded)
            if (target.exists() && !target.delete()) {
                Log.w(TAG, "Ancien fichier non supprimable, écriture directe")
                target.writeText(encoded)
            } else if (!temp.renameTo(target)) {
                target.writeText(encoded)
            }
            MutationResult.Success(sanitized)
        } catch (e: Exception) {
            Log.e(TAG, "Échec d'écriture de $target", e)
            runCatching { temp.delete() }
            MutationResult.StorageFailure("Enregistrement impossible : ${e.message}")
        }
    }

    suspend fun save(payload: BlocklistPayload): MutationResult =
        withContext(Dispatchers.IO) { saveNow(payload) }

    // MARK: - Mutations

    suspend fun addBlocked(rawNumber: String, label: String? = null): MutationResult =
        withContext(Dispatchers.IO) {
            when (val validation = com.guardcall.util.PhoneNumberNormalizer.validate(rawNumber)) {
                is com.guardcall.util.PhoneNumberNormalizer.Result.Invalid ->
                    MutationResult.InvalidNumber(validation.message)

                is com.guardcall.util.PhoneNumberNormalizer.Result.Valid -> {
                    val current = loadNow()
                    if (current.blocked.any { it.phoneNumber == validation.e164 }) {
                        return@withContext MutationResult.Duplicate(current)
                    }
                    saveNow(current.copy(blocked = current.blocked + BlockedEntry(validation.e164, label)))
                }
            }
        }

    suspend fun removeBlocked(e164: String): MutationResult = withContext(Dispatchers.IO) {
        val current = loadNow()
        saveNow(current.copy(blocked = current.blocked.filterNot { it.phoneNumber == e164 }))
    }

    suspend fun addIdentification(rawNumber: String, label: String): MutationResult =
        withContext(Dispatchers.IO) {
            when (val validation = com.guardcall.util.PhoneNumberNormalizer.validate(rawNumber)) {
                is com.guardcall.util.PhoneNumberNormalizer.Result.Invalid ->
                    MutationResult.InvalidNumber(validation.message)

                is com.guardcall.util.PhoneNumberNormalizer.Result.Valid -> {
                    val current = loadNow()
                    saveNow(
                        current.copy(
                            identified = current.identified.filterNot { it.phoneNumber == validation.e164 } +
                                IdentificationEntry(validation.e164, label)
                        )
                    )
                }
            }
        }

    companion object {
        private const val TAG = "BlocklistStore"
        const val BLOCKLIST_FILE = "blocklist.json"
    }
}
