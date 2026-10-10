package com.guardcall.domain

import com.guardcall.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MigrationTest {
    @get:Rule val folder = TemporaryFolder()
    private val v1 = """{"blocked":[{"phoneNumber":"0162123456","label":"Manuel"}],"identified":[{"phoneNumber":"0612345678","label":"Suspect"}],"rules":[{"prefix":"33162","length":11,"label":"Begone","source":"Begone-FR.xml"}]}"""
    private fun file() = File(folder.root, "blocklist.json")
    @Test fun `V1 entries and origins survive migration`() {
        val decoded = BlocklistCodec.decode(v1)
        assertTrue(decoded.migrated)
        assertEquals(2, decoded.payload.schemaVersion)
        assertEquals("33162123456", decoded.payload.blocked.single().phoneNumber)
        assertEquals("33612345678", decoded.payload.identified.single().phoneNumber)
        assertEquals("Begone-FR.xml", decoded.payload.rules.single().source)
        assertTrue(decoded.payload.allowed.isEmpty())
        assertEquals(ProtectionSettings(), decoded.payload.settings)
    }
    @Test fun `old payload without ranges is accepted`() {
        val p = BlocklistCodec.decode("""{"blocked":[],"identified":[]}""").payload
        assertTrue(p.isEmpty)
    }
    @Test fun `V1 migration backs up original byte content on first mutation`() {
        val target = file().apply { writeText(v1) }
        val repo = JsonFileRepository(target)
        assertEquals(v1, target.readText())
        val result = repo.update { it.copy(allowed = listOf(AllowedEntry("33162123456"))) }
        assertTrue(result is MutationResult.Success)
        assertEquals(v1, File(folder.root, "blocklist.v1.bak").readText())
        assertFalse(BlocklistCodec.decode(target.readText()).migrated)
        assertEquals(CallAction.ALLOW, RuleEngine(repo.load()).evaluate("0162123456").action)
    }
    @Test fun `V2 roundtrip preserves switches whitelist and metadata`() {
        val original = BlocklistCodec.decode(v1).payload.copy(
            allowed = listOf(AllowedEntry("33162123456", "Famille")), settings = ProtectionSettings(false, false))
        val decoded = BlocklistCodec.decode(BlocklistCodec.encode(original))
        assertFalse(decoded.migrated)
        assertEquals(original, decoded.payload)
    }
    @Test fun `future schema file is not overwritten`() {
        val text = """{"schemaVersion":3,"blocked":[]}"""
        val target = file().apply { writeText(text) }
        val result = JsonFileRepository(target).update { BlocklistPayload.EMPTY }
        assertTrue(result is MutationResult.StorageFailure)
        assertEquals(text, target.readText())
    }
    @Test fun `malformed or explicitly null fields fail clearly without data loss`() {
        val invalid = listOf("{", "null", "[]", """{"blocked":null}""", """{"blocked":[null]}""",
            """{"settings":null}""", """{"identified":[{"phoneNumber":"0612345678"}]}""",
            """{"blocked":[{"phoneNumber":123}]}""", """{"settings":{"enabled":"false"}}""",
            """{"rules":[{"prefix":"33162","length":4}]}""", """{"schemaVersion":1.5}""")
        invalid.forEach { text ->
            val target = file().apply { writeText(text) }
            val repo = JsonFileRepository(target)
            try { repo.load(); fail("Should fail: $text") } catch (expected: StorageException) { }
            assertTrue(repo.update { BlocklistPayload.EMPTY } is MutationResult.StorageFailure)
            assertEquals(text, target.readText())
        }
    }
    @Test fun `missing file starts empty without demo blocks`() {
        assertEquals(BlocklistPayload.EMPTY, JsonFileRepository(file()).load())
    }
    @Test fun `cache does not mask disk corruption on mutation`() {
        val target = file().apply { writeText(v1) }
        val repo = JsonFileRepository(target)
        repo.load()
        target.writeText("{")
        assertTrue(repo.update { it.copy(blocked = emptyList()) } is MutationResult.StorageFailure)
        assertEquals("{", target.readText())
    }
    @Test fun `parallel mutations are serialized and no additions are lost`() {
        val repo = JsonFileRepository(file())
        val executor = Executors.newFixedThreadPool(4)
        val tasks = (0..39).map { index -> executor.submit {
            val result = repo.update { it.copy(blocked = it.blocked + BlockedEntry("33162${index.toString().padStart(6, '0')}")) }
            check(result is MutationResult.Success)
        } }
        tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()
        assertEquals(40, repo.load().blocked.size)
    }
    @Test fun `atomic write cleans temporary file and retains previous file on failure`() {
        val target = file().apply { mkdir() }
        File(target, "child").writeText("preserved")
        val repo = JsonFileRepository(target)
        assertTrue(repo.update { BlocklistPayload.EMPTY } is MutationResult.StorageFailure)
        assertEquals("preserved", File(target, "child").readText())
        assertFalse(File(folder.root, "blocklist.json.tmp").exists())
    }
    @Test fun `oversized file is not decoded or overwritten`() {
        val target = file().apply { writeText(" ".repeat(JsonFileRepository.MAX_FILE_BYTES + 1)) }
        assertTrue(JsonFileRepository(target).update { BlocklistPayload.EMPTY } is MutationResult.StorageFailure)
        assertEquals((JsonFileRepository.MAX_FILE_BYTES + 1).toLong(), target.length())
    }
    @Test fun `migration and normalization are idempotent`() {
        val p = BlocklistCodec.decode(v1).payload
        assertEquals(p, p.normalized().normalized())
        assertEquals(p, BlocklistCodec.decode(BlocklistCodec.encode(p)).payload)
    }
}
