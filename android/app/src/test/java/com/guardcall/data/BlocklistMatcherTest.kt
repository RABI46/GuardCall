package com.guardcall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la correspondance appel entrant ↔ liste, sans dépendance Android.
 */
class BlocklistMatcherTest {

    private val payload = BlocklistPayload(
        blocked = listOf(
            BlockedEntry("33140855512", "Démarchage"),
            BlockedEntry("14085555555")
        ),
        identified = listOf(
            IdentificationEntry("33612345678", "Spam suspecté")
        )
    )

    @Test
    fun `un numéro bloqué est détecté quel que soit son format d arrivée`() {
        assertTrue(BlocklistMatcher.isBlocked("+33140855512", payload))
        assertTrue(BlocklistMatcher.isBlocked("0140855512", payload))
        assertTrue(BlocklistMatcher.isBlocked("0033140855512", payload))
        assertTrue(BlocklistMatcher.isBlocked("33140855512", payload))
    }

    @Test
    fun `un numéro non listé n est pas bloqué`() {
        assertFalse(BlocklistMatcher.isBlocked("+33140855513", payload))
        assertFalse(BlocklistMatcher.isBlocked("+33699999999", payload))
    }

    @Test
    fun `régression - un suffixe commun ne bloque plus par erreur`() {
        // Ancien comportement : `endsWith` bidirectionnel → « 5512 » bloquait
        // tout numéro se terminant par 5512.
        val shortList = BlocklistPayload(blocked = listOf(BlockedEntry("33140855512")))
        assertFalse(BlocklistMatcher.isBlocked("+44140855512", shortList))
        assertFalse(BlocklistMatcher.isBlocked("+33940855512", shortList))

        val fourDigits = BlocklistPayload(blocked = listOf(BlockedEntry("5512")))
        assertFalse(
            "Bloquer 5512 ne doit pas bloquer tous les numéros finissant par 5512",
            BlocklistMatcher.isBlocked("+33140855512", fourDigits)
        )
    }

    @Test
    fun `entrée inconnue ou numéro masqué ignorés`() {
        assertFalse(BlocklistMatcher.isBlocked(null, payload))
        assertFalse(BlocklistMatcher.isBlocked("", payload))
        assertFalse(BlocklistMatcher.isBlocked("numéro masqué", payload))
        assertNull(BlocklistMatcher.identificationLabel(null, payload))
    }

    @Test
    fun `étiquette d identification renvoyée pour un numéro listé`() {
        assertEquals("Spam suspecté", BlocklistMatcher.identificationLabel("0612345678", payload))
        assertNull(BlocklistMatcher.identificationLabel("+33699999999", payload))
    }

    @Test
    fun `un numéro bloqué n a pas d étiquette d identification`() {
        assertNull(BlocklistMatcher.identificationLabel("+33140855512", payload))
    }

    @Test
    fun `liste vide ne bloque rien`() {
        assertFalse(BlocklistMatcher.isBlocked("+33140855512", BlocklistPayload.EMPTY))
    }
}

class BlocklistPayloadTest {

    @Test
    fun `sanitized trie par ordre croissant et déduplique`() {
        val payload = BlocklistPayload(
            blocked = listOf(
                BlockedEntry("33612345678"),
                BlockedEntry("33140855512"),
                BlockedEntry("33612345678")
            ),
            identified = listOf(
                IdentificationEntry("18775555555", "Spam"),
                IdentificationEntry("18005550199", "Test"),
                IdentificationEntry("18775555555", "Doublon")
            )
        ).sanitized()

        assertEquals(listOf("33140855512", "33612345678"), payload.blocked.map { it.phoneNumber })
        assertEquals(listOf("18005550199", "18775555555"), payload.identified.map { it.phoneNumber })
    }

    @Test
    fun `sanitized est idempotent`() {
        val payload = BlocklistPayload(
            blocked = listOf(BlockedEntry("33300000000"), BlockedEntry("33100000000"))
        ).sanitized()
        assertEquals(payload, payload.sanitized())
    }

    @Test
    fun `la liste par défaut ne bloque aucun numéro`() {
        assertTrue(
            "L'app ne doit bloquer aucun numéro par défaut",
            BlocklistPayload.EMPTY.blocked.isEmpty()
        )
    }
}

/** Tests des plages Begone — complètent les tests d'égalité stricte. */
class BlockRuleTest {

    private val payload = BlocklistPayload(
        rules = listOf(
            BlockRule(
                prefix = "33162",
                length = 11,
                label = "Anti démarchage FR",
                source = BegoneListParser.SOURCE_BEGONE_FR
            ),
            BlockRule(
                prefix = "337588",
                length = 11,
                label = "OnOff Suspicieux",
                source = BegoneListParser.SOURCE_BEGONE_FR
            )
        )
    )

    @Test
    fun `un numéro de la plage est bloqué quel que soit son format`() {
        assertTrue(BlocklistMatcher.isBlocked("+33162123456", payload))
        assertTrue(BlocklistMatcher.isBlocked("0162123456", payload))
        assertTrue(BlocklistMatcher.isBlocked("0033162123456", payload))
    }

    @Test
    fun `un numéro hors plage n est pas bloqué`() {
        assertFalse(BlocklistMatcher.isBlocked("+33163123456", payload))
        assertFalse(BlocklistMatcher.isBlocked("+33612345678", payload))
    }

    @Test
    fun `régression - la longueur exacte est exigée`() {
        // Trop court ni trop long : une plage ne couvre qu'une longueur d'E.164.
        assertFalse(BlocklistMatcher.isBlocked("+3316212345", payload))
        assertFalse(BlocklistMatcher.isBlocked("+331621234567", payload))
    }

    @Test
    fun `régression - un préfixe ne déborde pas sur un autre indicatif`() {
        val shortPrefix = BlocklistPayload(rules = listOf(BlockRule(prefix = "33162", length = 11)))
        assertFalse(
            "« 33162 » ne doit pas bloquer un numéro britannique le contenant",
            BlocklistMatcher.isBlocked("+44331621234", shortPrefix)
        )
        assertFalse(BlocklistMatcher.isBlocked("+3316200000", shortPrefix))
    }

    @Test
    fun `l étiquette de la plage bloquante est renvoyée`() {
        assertEquals("Anti démarchage FR", BlocklistMatcher.blockingLabel("+33162123456", payload))
        assertNull(BlocklistMatcher.blockingLabel("+33612345678", payload))
    }

    @Test
    fun `une entrée manuelle est prioritaire sur une plage pour l étiquette`() {
        val withManual = payload.copy(
            blocked = listOf(BlockedEntry("33162123456", "Ajout manuel"))
        )
        assertEquals("Ajout manuel", BlocklistMatcher.blockingLabel("+33162123456", withManual))
    }

    @Test
    fun `les plages sont triées et dédupliquées par sanitized`() {
        val sanitized = BlocklistPayload(
            rules = listOf(
                BlockRule(prefix = "33949", length = 11),
                BlockRule(prefix = "33162", length = 11),
                BlockRule(prefix = "33162", length = 11)
            )
        ).sanitized()
        assertEquals(listOf("33162", "33949"), sanitized.effectiveRules.map { it.prefix })
    }

    @Test
    fun `un payload sans règles ne bloque rien et n est jamais null`() {
        // Gson peut laisser `rules` à null sur un JSON ancien : l'accesseur
        // `effectiveRules` doit absorber le cas sans NPE.
        assertTrue(BlocklistPayload.EMPTY.effectiveRules.isEmpty())
        assertFalse(BlocklistMatcher.isBlocked("+33162123456", BlocklistPayload.EMPTY))
    }
}
