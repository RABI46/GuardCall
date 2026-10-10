package com.guardcall.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.guardcall.util.PhoneNumberNormalizer

class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class DecodedBlocklist(val payload: BlocklistPayload, val migrated: Boolean)

/** Explicit decoding: Gson must never inject null into non-null Kotlin DTO fields. */
object BlocklistCodec {
    fun decode(json: String): DecodedBlocklist {
        try {
            val root = JsonParser.parseString(json)
            require(root.isJsonObject) { "Le fichier doit contenir un objet JSON." }
            val obj = root.asJsonObject
            val version = obj.get("schemaVersion")?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "Version invalide." }
                it.asString.toInt()
            } ?: 1
            require(version in 1..2) { "Version de stockage non prise en charge : $version." }
            fun number(entry: JsonObject): String = PhoneNumberNormalizer.normalize(entry.string("phoneNumber"))
                ?: throw IllegalArgumentException("Numéro invalide dans le fichier.")
            val blocked = obj.entries("blocked").map { BlockedEntry(number(it), it.optionalString("label")) }
            val identified = obj.entries("identified").map { IdentificationEntry(number(it), it.string("label")) }
            val allowed = obj.entries("allowed").map { AllowedEntry(number(it), it.optionalString("label")) }
            val rules = obj.entries("rules").map {
                val prefix = it.string("prefix")
                val lengthElement = it.get("length") ?: error("Longueur de plage absente.")
                require(lengthElement.isJsonPrimitive && lengthElement.asJsonPrimitive.isNumber)
                val length = lengthElement.asString.toInt()
                require(prefix.isNotEmpty() && prefix.all { digit -> digit in '0'..'9' } && !prefix.startsWith("0"))
                require(length in PhoneNumberNormalizer.MIN_LENGTH..PhoneNumberNormalizer.MAX_LENGTH && prefix.length <= length)
                BlockRule(prefix, length, it.optionalString("label"), it.optionalString("source"))
            }
            val settingsElement = obj.get("settings")
            val settings = if (settingsElement == null) ProtectionSettings() else {
                require(settingsElement.isJsonObject) { "Paramètres invalides." }
                val settingsObj = settingsElement.asJsonObject
                ProtectionSettings(settingsObj.boolean("enabled", true), settingsObj.boolean("historyEnabled", true))
            }
            return DecodedBlocklist(BlocklistPayload(blocked, identified, rules, allowed, settings).sanitized(), version == 1)
        } catch (e: Exception) {
            throw StorageException("Liste illisible : ${e.message}. Le fichier original est conservé.", e)
        }
    }

    fun encode(payload: BlocklistPayload): String = Gson().toJson(payload.normalized())

    private fun JsonObject.entries(name: String): List<JsonObject> {
        val value = get(name) ?: return emptyList()
        require(value.isJsonArray) { "Le champ $name doit être une liste." }
        return value.asJsonArray.map {
            require(it.isJsonObject) { "Entrée invalide dans $name." }
            it.asJsonObject
        }
    }

    private fun JsonObject.string(name: String): String {
        val value = get(name) ?: error("Champ $name absent.")
        return value.strictString(name)
    }

    private fun JsonObject.optionalString(name: String): String? = get(name)?.let {
        if (it.isJsonNull) null else it.strictString(name)
    }

    private fun JsonElement.strictString(name: String): String {
        require(isJsonPrimitive && asJsonPrimitive.isString) { "Le champ $name doit être une chaîne." }
        return asString
    }

    private fun JsonObject.boolean(name: String, default: Boolean): Boolean = get(name)?.let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "Le champ $name doit être un booléen." }
        it.asBoolean
    } ?: default
}
