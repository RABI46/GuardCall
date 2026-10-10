package com.guardcall.domain

import com.guardcall.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class RuleEngineTest {
    private val number = "33162123456"
    private val base = BlocklistPayload(
        blocked = listOf(BlockedEntry(number, "Manuel")),
        identified = listOf(IdentificationEntry(number, "Suspect")),
        rules = listOf(BlockRule("33162", 11, "Plage"))
    )
    @Test fun `whitelist beats every blocking and silence rule`() {
        val result = RuleEngine(base.copy(allowed = listOf(AllowedEntry(number)))).evaluate("0162123456")
        assertEquals(CallAction.ALLOW, result.action)
        assertEquals("Liste blanche", result.reason)
    }
    @Test fun `pause beats all rules`() {
        assertEquals(CallAction.ALLOW, RuleEngine(base.copy(settings = ProtectionSettings(enabled = false))).evaluate(number).action)
    }
    @Test fun `manual block reason wins over range`() {
        assertEquals("Manuel", RuleEngine(base).evaluate(number).reason)
    }
    @Test fun `range still wins over V1 identification`() {
        assertEquals(CallAction.BLOCK, RuleEngine(base.copy(blocked = emptyList())).evaluate(number).action)
    }
    @Test fun `most specific range supplies reason regardless of order`() {
        val rules = listOf(BlockRule("33", 11, "Pays"), BlockRule("33162", 11, "Précis"))
        assertEquals("Précis", RuleEngine(BlocklistPayload(rules = rules)).evaluate(number).reason)
        assertEquals("Précis", RuleEngine(BlocklistPayload(rules = rules.reversed())).evaluate(number).reason)
    }
    @Test fun `identification alone silences`() {
        assertEquals(CallAction.SILENCE, RuleEngine(base.copy(blocked = emptyList(), rules = emptyList())).evaluate(number).action)
    }
    @Test fun `invalid and withheld numbers fail open`() {
        listOf(null, "", "masqué", "123", "+0000000000").forEach {
            val result = RuleEngine(base).evaluate(it)
            assertEquals(CallAction.ALLOW, result.action)
            assertNull(result.number)
        }
    }
    @Test fun `ranges require exact length and never use suffix`() {
        val engine = RuleEngine(BlocklistPayload(rules = base.rules))
        listOf("+3316212345", "+331621234567", "+44331621234", "+33163123456").forEach {
            assertEquals(CallAction.ALLOW, engine.evaluate(it).action)
        }
    }
    @Test fun `empty rules are never blocking`() {
        assertEquals(CallAction.ALLOW, RuleEngine(BlocklistPayload.EMPTY).evaluate(number).action)
    }
}

/** Cover every combination of rule membership and protection switch. */
@RunWith(Parameterized::class)
class RulePriorityMatrixTest(private val flags: Int, private val raw: String) {
    @Test fun `priority matrix`() {
        val n = "33162123456"
        val whitelist = flags and 1 != 0
        val manual = flags and 2 != 0
        val range = flags and 4 != 0
        val silence = flags and 8 != 0
        val enabled = flags and 16 != 0
        val payload = BlocklistPayload(
            allowed = if (whitelist) listOf(AllowedEntry(n)) else emptyList(),
            blocked = if (manual) listOf(BlockedEntry(n)) else emptyList(),
            rules = if (range) listOf(BlockRule("33162", 11)) else emptyList(),
            identified = if (silence) listOf(IdentificationEntry(n, "Spam")) else emptyList(),
            settings = ProtectionSettings(enabled = enabled)
        )
        val expected = when {
            !enabled || whitelist -> CallAction.ALLOW
            manual || range -> CallAction.BLOCK
            silence -> CallAction.SILENCE
            else -> CallAction.ALLOW
        }
        assertEquals(expected, RuleEngine(payload).evaluate(raw).action)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "flags={0}, number={1}")
        fun cases(): List<Array<Any>> = (0..31).flatMap { flags ->
            listOf("+33162123456", "0162123456", "0033162123456").map { arrayOf<Any>(flags, it) }
        }
    }
}
