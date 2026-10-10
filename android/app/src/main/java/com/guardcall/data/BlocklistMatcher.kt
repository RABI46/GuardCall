package com.guardcall.data

import com.guardcall.domain.CallAction
import com.guardcall.domain.RuleEngine
import com.guardcall.util.PhoneNumberNormalizer

/**
 * Correspondance appel entrant ↔ liste.
 *
 * Deux mécanismes complémentaires :
 * 1. **Égalité stricte sur l'E.164 normalisé** pour les numéros saisis à la
 *    main. Une précédente version utilisait une correspondance par suffixe
 *    bidirectionnelle (`a.endsWith(b) || b.endsWith(a)`) : bloquer « 1234 »
 *    bloquait alors tous les numéros se terminant par 1234.
 * 2. **Appartenance à une plage** ([BlockRule], format Begone) : test exact sur
 *    la longueur totale puis sur le préfixe. Une plage ne peut donc pas
 *    déborder sur un numéro d'une autre longueur.
 *
 * Objet sans dépendance Android → testable en JVM pur.
 */
object BlocklistMatcher {

    fun isBlocked(incoming: String?, payload: BlocklistPayload): Boolean {
        return RuleEngine(payload.normalized()).evaluate(incoming).action == CallAction.BLOCK
    }

    fun identificationLabel(incoming: String?, payload: BlocklistPayload): String? {
        val normalized = PhoneNumberNormalizer.normalize(incoming) ?: return null
        return payload.identified
            .firstOrNull { PhoneNumberNormalizer.normalize(it.phoneNumber) == normalized }
            ?.label
    }

    /**
     * Étiquette de la règle ou de l'entrée responsable du blocage — `null` si
     * l'appelant n'est pas bloqué. Sert à l'affichage et au journal.
     */
    fun blockingLabel(incoming: String?, payload: BlocklistPayload): String? {
        if (!isBlocked(incoming, payload)) return null
        val normalized = PhoneNumberNormalizer.normalize(incoming) ?: return null
        return payload.blocked
            .firstOrNull { PhoneNumberNormalizer.normalize(it.phoneNumber) == normalized }
            ?.label
            ?: matchingRule(normalized, payload.effectiveRules)?.label
    }

    /** Règle de plage couvrant [incoming], ou `null`. */
    fun matchingRule(incoming: String?, rules: List<BlockRule>): BlockRule? {
        val normalized = PhoneNumberNormalizer.normalize(incoming) ?: return null
        return rules.firstOrNull { it.matches(normalized) }
    }

    private fun matchesRule(e164: String, rules: List<BlockRule>): Boolean =
        rules.any { it.matches(e164) }
}
