package com.guardcall.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.guardcall.domain.CallAction
import com.guardcall.domain.CallHistory
import com.guardcall.domain.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Private local log, bounded independently of the rules; never delays a call response. */
class HistoryStore(private val file: File) {
    private val lock = Any()
    private fun read(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        require(file.length() <= JsonFileRepository.MAX_FILE_BYTES) { "Historique trop volumineux." }
        return JsonParser.parseString(file.readText()).asJsonArray.map {
            val o = it.asJsonObject
            HistoryEntry(
                o.get("id").asString, o.get("timestamp").asLong,
                o.get("number")?.takeUnless { n -> n.isJsonNull }?.asString,
                CallAction.valueOf(o.get("action").asString), o.get("reason").asString
            )
        }.take(CallHistory.MAX_ENTRIES)
    }
    fun appendNow(entry: HistoryEntry) = synchronized(lock) {
        JsonFileRepository.atomicWrite(file, Gson().toJson(CallHistory.append(read(), entry)))
    }
    suspend fun load(): List<HistoryEntry> = withContext(Dispatchers.IO) { synchronized(lock) { read() } }
    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) { JsonFileRepository.atomicWrite(file, "[]") }
    }
    companion object { const val FILE_NAME = "call_history.json" }
}
