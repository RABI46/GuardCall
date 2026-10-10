package com.guardcall.data

import android.content.Context
import com.guardcall.util.PhoneNumberNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BlocklistStore(context: Context) {
    private val appContext = context.applicationContext
    private val repository = JsonFileRepository(File(appContext.filesDir, BLOCKLIST_FILE))

    fun loadNow(): BlocklistPayload = repository.load()
    suspend fun load(): BlocklistPayload = withContext(Dispatchers.IO) { loadNow() }

    private suspend fun mutate(transform: (BlocklistPayload) -> BlocklistPayload): MutationResult =
        withContext(Dispatchers.IO) { repository.update(transform) }

    private suspend fun withNumber(raw: String, transform: (BlocklistPayload, String) -> BlocklistPayload): MutationResult =
        when (val validation = PhoneNumberNormalizer.validate(raw)) {
            is PhoneNumberNormalizer.Result.Invalid -> MutationResult.InvalidNumber(validation.message)
            is PhoneNumberNormalizer.Result.Valid -> mutate { transform(it, validation.e164) }
        }

    suspend fun addBlocked(rawNumber: String, label: String? = null): MutationResult = withNumber(rawNumber) { p, n ->
        p.copy(blocked = p.blocked.filterNot { it.phoneNumber == n } + BlockedEntry(n, label))
    }
    suspend fun removeBlocked(number: String): MutationResult = mutate { p ->
        p.copy(blocked = p.blocked.filterNot { it.phoneNumber == number })
    }
    suspend fun addAllowed(rawNumber: String, label: String? = null): MutationResult = withNumber(rawNumber) { p, n ->
        p.copy(allowed = p.allowed.filterNot { it.phoneNumber == n } + AllowedEntry(n, label))
    }
    suspend fun removeAllowed(number: String): MutationResult = mutate { p ->
        p.copy(allowed = p.allowed.filterNot { it.phoneNumber == number })
    }
    suspend fun addIdentification(rawNumber: String, label: String): MutationResult = withNumber(rawNumber) { p, n ->
        p.copy(identified = p.identified.filterNot { it.phoneNumber == n } + IdentificationEntry(n, label))
    }
    suspend fun removeIdentification(number: String): MutationResult = mutate { p ->
        p.copy(identified = p.identified.filterNot { it.phoneNumber == number })
    }
    suspend fun setProtection(enabled: Boolean): MutationResult = mutate { it.copy(settings = it.settings.copy(enabled = enabled)) }
    suspend fun setHistory(enabled: Boolean): MutationResult = mutate { it.copy(settings = it.settings.copy(historyEnabled = enabled)) }

    suspend fun addRange(pattern: String, label: String?): MutationResult {
        val rule = BegoneListParser.toBlockRule(pattern, label)
            ?: return MutationResult.InvalidNumber("Utilisez un préfixe international suivi de #, par exemple +33162######.")
        return mutate { p ->
            val existing = p.effectiveRules.firstOrNull { it.key == rule.key }
            p.copy(rules = p.effectiveRules.filterNot { it.key == rule.key } + rule.copy(source = existing?.source))
        }
    }
    suspend fun removeRange(key: String): MutationResult = mutate { p -> p.copy(rules = p.effectiveRules.filterNot { it.key == key }) }

    suspend fun importBegoneList(): MutationResult = withContext(Dispatchers.IO) {
        val parsed = try {
            appContext.assets.open(BegoneListParser.SOURCE_BEGONE_FR).use { BegoneListParser.parse(it) }
        } catch (e: Exception) {
            return@withContext MutationResult.StorageFailure("Liste Begone illisible : ${e.message}")
        }
        if (parsed.isEmpty()) return@withContext MutationResult.StorageFailure("Aucune plage exploitable.")
        // Existing custom labels/source win on collisions, making removal predictable.
        repository.update { it.copy(rules = (it.effectiveRules + parsed).distinctBy { r -> r.key }) }
    }
    suspend fun clearBegoneList(): MutationResult = mutate { p ->
        p.copy(rules = p.effectiveRules.filterNot { it.source == BegoneListParser.SOURCE_BEGONE_FR })
    }

    companion object { const val BLOCKLIST_FILE = "blocklist.json" }
}
