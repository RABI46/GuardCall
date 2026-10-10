package com.guardcall.domain

import java.util.UUID

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val number: String?,
    val action: CallAction,
    val reason: String
)

object CallHistory {
    const val MAX_ENTRIES = 500
    fun append(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        (listOf(entry) + entries).distinctBy { it.id }.take(MAX_ENTRIES)
}
