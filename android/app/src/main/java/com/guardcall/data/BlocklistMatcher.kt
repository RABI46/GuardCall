package com.guardcall.data

import com.guardcall.util.PhoneNumberNormalizer

/**
 * Correspondance appel entrant ↔ liste.
 *
 * La comparaison est une **égalité stricte sur le E.164 normalisé**. Une
 * précédente version utilisait une correspondance par suffixe bidirectionnelle
 * (`a.endsWith(b) || b.endsWith(a)`) : bloquer « 1234 » bloquait alors tous les
 * numéros se terminant par 1234.
 *
 * Objet sans dépendance Android → testable en JVM pur.
 */
object BlocklistMatcher {

    fun isBlocked(incoming: String?, payload: BlocklistPayload): Boolean {
        val normalized = PhoneNumberNormalizer.normalize(incoming) ?: return false
        return payload.blocked.any { PhoneNumberNormalizer.normalize(it.phoneNumber) == normalized }
    }

    fun identificationLabel(incoming: String?, payload: BlocklistPayload): String? {
        val normalized = PhoneNumberNormalizer.normalize(incoming) ?: return null
        return payload.identified
            .firstOrNull { PhoneNumberNormalizer.normalize(it.phoneNumber) == normalized }
            ?.label
    }
}
