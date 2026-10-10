package com.guardcall.data

import com.guardcall.util.PhoneNumberNormalizer

/** Entrée bloquée. `phoneNumber` est un E.164 normalisé (chiffres seuls). */
data class BlockedEntry(
    val phoneNumber: String,
    val label: String? = null
)

data class AllowedEntry(val phoneNumber: String, val label: String? = null)

data class ProtectionSettings(
    val enabled: Boolean = true,
    val historyEnabled: Boolean = true
)

/** Numéro identifié (étiqueté) mais non bloqué. */
data class IdentificationEntry(
    val phoneNumber: String,
    val label: String
)

/**
 * Plage de numéros bloqués — modèle de la liste Begone (`+33162######`).
 *
 * `#` est le joker Begone : il vaut pour un chiffre quelconque. Une règle ne
 * retient donc qu'un **préfixe E.164 et une longueur totale** : les dizaines de
 * millions de numéros couverts par Begone-FR ne sont jamais matérialisés, ni en
 * mémoire ni dans `blocklist.json` (18 règles suffisent).
 *
 * Le test d'appartenance est exact (`length` + `startsWith`) : contrairement à
 * l'ancienne correspondance par suffixe, bloquer la plage `33162` (11 chiffres)
 * ne bloque aucun numéro d'une autre longueur ni d'un autre préfixe.
 */
data class BlockRule(
    val prefix: String,
    val length: Int,
    val label: String? = null,
    val source: String? = null
) {
    /** Clé de déduplication — propriété de corps : exclue d'`equals`/`copy`. */
    val key: String get() = "$prefix:$length"

    /** Vrai si `e164` (déjà normalisé) appartient à la plage. */
    fun matches(e164: String): Boolean = e164.length == length && e164.startsWith(prefix)
}

data class BlocklistPayload(
    val blocked: List<BlockedEntry> = emptyList(),
    val identified: List<IdentificationEntry> = emptyList(),
    val rules: List<BlockRule> = emptyList(),
    val allowed: List<AllowedEntry> = emptyList(),
    val settings: ProtectionSettings = ProtectionSettings(),
    val schemaVersion: Int = 2
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
        val rulesOrder = compareBy<BlockRule>({ it.length }, { it.prefix })
        return copy(
            blocked = blocked.distinctBy { it.phoneNumber }.sortedWith(numericOrder),
            identified = identified.distinctBy { it.phoneNumber }.sortedWith(identifiedOrder),
            rules = effectiveRules.distinctBy { it.key }.sortedWith(rulesOrder),
            allowed = allowed.distinctBy { it.phoneNumber }
                .sortedWith(compareBy({ it.phoneNumber.length }, { it.phoneNumber }))
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
        },
        rules = effectiveRules,
        allowed = allowed.mapNotNull { entry ->
            PhoneNumberNormalizer.normalize(entry.phoneNumber)?.let { entry.copy(phoneNumber = it) }
        },
        settings = settings
    ).sanitized()

    /**
     * Règles de plage, **jamais null**.
     *
     * Gson n'appelle pas forcément le constructeur Kotlin : un `blocklist.json`
     * écrit avant l'introduction des règles laisse le champ à `null`, ce qui
     * lèverait un NPE sur un type déclaré non nullable. `orEmpty()` couvre les
     * deux chemins de désérialisation.
     */
    val effectiveRules: List<BlockRule> get() = rules.orEmpty()

    val isEmpty: Boolean get() = blocked.isEmpty() && identified.isEmpty() && effectiveRules.isEmpty() && allowed.isEmpty()
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
