package com.guardcall.domain

import com.guardcall.data.BlocklistPayload
import com.guardcall.util.PhoneNumberNormalizer

enum class CallAction { ALLOW, BLOCK, SILENCE }

data class CallDecision(val action: CallAction, val reason: String, val number: String?)

/** Pure, deterministic rules. A whitelist entry always wins, including over silence. */
class RuleEngine(payload: BlocklistPayload) {
    private val settings = payload.settings
    private val allowed = payload.allowed.associateBy { it.phoneNumber }
    private val blocked = payload.blocked.associateBy { it.phoneNumber }
    private val identified = payload.identified.associateBy { it.phoneNumber }
    private val ranges = payload.effectiveRules.sortedByDescending { it.prefix.length }

    fun evaluate(raw: String?): CallDecision {
        val number = PhoneNumberNormalizer.normalize(raw)
        if (!settings.enabled) return CallDecision(CallAction.ALLOW, "Protection en pause", number)
        if (number == null) return CallDecision(CallAction.ALLOW, "Numéro inconnu ou masqué", null)
        allowed[number]?.let {
            return CallDecision(CallAction.ALLOW, it.label ?: "Liste blanche", number)
        }
        blocked[number]?.let {
            return CallDecision(CallAction.BLOCK, it.label ?: "Numéro bloqué", number)
        }
        // Preserve V1 precedence: a blocked range wins over an identification entry.
        ranges.firstOrNull { it.matches(number) }?.let {
            return CallDecision(CallAction.BLOCK, it.label ?: "Plage bloquée", number)
        }
        identified[number]?.let { return CallDecision(CallAction.SILENCE, it.label, number) }
        return CallDecision(CallAction.ALLOW, "Aucune règle", number)
    }
}
