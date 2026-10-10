package com.guardcall.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** All users share one Application-scoped instance. Reads use an immutable cached snapshot. */
class JsonFileRepository(private val file: File) {
    private val lock = Any()
    @Volatile private var snapshot: BlocklistPayload? = null

    fun load(): BlocklistPayload = snapshot ?: synchronized(lock) {
        snapshot ?: read().payload.also { snapshot = it }
    }

    private fun read(): DecodedBlocklist {
        if (!file.exists()) return DecodedBlocklist(BlocklistPayload.EMPTY, false)
        if (file.length() > MAX_FILE_BYTES) throw StorageException("Liste trop volumineuse. Le fichier est conservé.")
        return BlocklistCodec.decode(file.readText())
    }

    fun update(transform: (BlocklistPayload) -> BlocklistPayload): MutationResult = synchronized(lock) {
        try {
            // Re-read before each mutation: never replace a corrupt/future-schema file with defaults.
            val decoded = read()
            val next = transform(decoded.payload).normalized()
            val encoded = BlocklistCodec.encode(next)
            require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "Liste trop volumineuse." }
            if (decoded.migrated) {
                val backup = File(file.parentFile, "blocklist.v1.bak")
                if (!backup.exists()) atomicWrite(backup, file.readText())
            }
            atomicWrite(file, encoded)
            snapshot = next
            MutationResult.Success(next)
        } catch (e: Exception) {
            MutationResult.StorageFailure(e.message ?: "Enregistrement impossible. Le fichier original est conservé.")
        }
    }

    companion object {
        const val MAX_FILE_BYTES = 2 * 1024 * 1024

        /** Same-directory atomic replacement. Failure leaves the old file intact. API 26+. */
        fun atomicWrite(target: File, text: String) {
            val temp = File(target.parentFile, "${target.name}.tmp")
            try {
                FileOutputStream(temp).use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                    it.fd.sync()
                }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temp.delete()
            }
        }
    }
}
