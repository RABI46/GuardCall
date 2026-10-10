package com.guardcall.data

import com.guardcall.util.PhoneNumberNormalizer

/** Entrée bloquée. `phoneNumber` est un E.164 normalisé (chiffres seuls). */
data class BlockedEntry(
    val phoneNumber: String,
    val label: String? = null
)

/** Numéro identifié (étiqueté) mais non bloqué. */
data class IdentificationEntry(
    val phoneNumber: String,
    val label: String
)

data class BlocklistPayload(
    val blocked: List<BlockedEntry> = emptyList(),
    val identified: List<IdentificationEntry> = emptyList()
) {
    companion object {
        /**
         * Liste vide : l'application ne bloque **aucun** numéro par défaut.
         * (Une précédente version embarquait des numéros de démonstration qui
         * étaient réellement bloqués au premier lancement.)
         */
        val EMPTY = BlocklistPayload()
    }

    /**
     * Applique l'invariant : tri croissant + déduplication. Idempotent.
     *
     * Le tri compare d'abord la longueur puis les chiffres : pour des chaînes de
     * chiffres seuls, c'est exactement l'ordre numérique (un tri lexicographique
     * simple placerait « 99999999 » après « 100000000 »).
     */
    fun sanitized(): BlocklistPayload {
        val numericOrder = compareBy<BlockedEntry>({ it.phoneNumber.length }, { it.phoneNumber })
        val identifiedOrder = compareBy<IdentificationEntry>({ it.phoneNumber.length }, { it.phoneNumber })
        return copy(
            blocked = blocked.distinctBy { it.phoneNumber }.sortedWith(numericOrder),
            identified = identified.distinctBy { it.phoneNumber }.sortedWith(identifiedOrder)
        )
    }

    /**
     * Renormalise chaque numéro en E.164 (les données écrites par une version
     * antérieure pouvaient être au format national), écarte les entrées
     * invalides, puis applique [sanitized].
     */
    fun normalized(): BlocklistPayload = BlocklistPayload(
        blocked = blocked.mapNotNull { entry ->
            PhoneNumberNormalizer.normalize(entry.phoneNumber)?.let { entry.copy(phoneNumber = it) }
        },
        identified = identified.mapNotNull { entry ->
            PhoneNumberNormalizer.normalize(entry.phoneNumber)?.let { entry.copy(phoneNumber = it) }
        }
    ).sanitized()

    val isEmpty: Boolean get() = blocked.isEmpty() && identified.isEmpty()
}

/** Résultat explicite d'une mutation de la liste — jamais d'erreur silencieuse. */
sealed class MutationResult {
    data class Success(val payload: BlocklistPayload) : MutationResult()
    data class Duplicate(val payload: BlocklistPayload) : MutationResult()
    data class InvalidNumber(val message: String) : MutationResult()
    data class StorageFailure(val message: String) : MutationResult()

    val payloadOrNull: BlocklistPayload?
        get() = when (this) {
            is Success -> payload
            is Duplicate -> payload
            else -> null
        }

    val errorMessageOrNull: String?
        get() = when (this) {
            is InvalidNumber -> message
            is StorageFailure -> message
            else -> null
        }
}
