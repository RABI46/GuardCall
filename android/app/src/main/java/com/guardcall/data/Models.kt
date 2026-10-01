package com.guardcall.data

data class BlockedEntry(
    val phoneNumber: String, // E.164 ou digits, ex "14085555555"
    val label: String? = null
)

data class IdentificationEntry(
    val phoneNumber: String,
    val label: String
)

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
                IdentificationEntry("18775555555", "Spam suspecté"),
                IdentificationEntry("18005550199", "GuardCall — Test")
            )
        )
    }
}
