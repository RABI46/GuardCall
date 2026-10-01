package com.guardcall.data

import kotlinx.serialization.Serializable

@Serializable
data class BlockedEntry(
    val phoneNumber: String, // E.164 ou digits, ex "14085555555"
    val label: String? = null
)

@Serializable
data class IdentificationEntry(
    val phoneNumber: String,
    val label: String
)

@Serializable
data class BlocklistPayload(
    val blocked: List<BlockedEntry> = emptyList(),
    val identified: List<IdentificationEntry> = emptyList()
) {
    companion object {
        val DEFAULT = BlocklistPayload(
            blocked = listOf(
                BlockedEntry("14085555555", "Spam"),
                BlockedEntry("14085551234", "Démarchage")
            ),
            identified = listOf(
                BlockedEntry("18775555555", "Spam suspecté").let { IdentificationEntry(it.phoneNumber, it.label!!) },
                IdentificationEntry("18005550199", "GuardCall — Test")
            )
        )
    }
}
