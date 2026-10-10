package com.guardcall.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La normalisation E.164 est le socle commun à l'UI, au stockage et au service
 * de filtrage : un numéro mal normalisé n'est jamais bloqué (ou bloque à tort).
 */
class PhoneNumberNormalizerTest {

    @Test
    fun `numéro national français converti avec indicatif 33`() {
        assertEquals("33140855512", PhoneNumberNormalizer.normalize("01 40 85 55 12"))
        assertEquals("33612345678", PhoneNumberNormalizer.normalize("0612345678"))
    }

    @Test
    fun `formats internationaux acceptés`() {
        assertEquals("33140855512", PhoneNumberNormalizer.normalize("+33140855512"))
        assertEquals("33140855512", PhoneNumberNormalizer.normalize("0033140855512"))
        assertEquals("33140855512", PhoneNumberNormalizer.normalize("33140855512"))
        assertEquals("14085555555", PhoneNumberNormalizer.normalize("+1 (408) 555-5555"))
    }

    @Test
    fun `saisies invalides rejetées`() {
        assertNull(PhoneNumberNormalizer.normalize(null))
        assertNull(PhoneNumberNormalizer.normalize(""))
        assertNull(PhoneNumberNormalizer.normalize("abc"))
        assertNull(PhoneNumberNormalizer.normalize("123"))
        assertNull(PhoneNumberNormalizer.normalize("1234567890123456"))
    }

    @Test
    fun `normalisation idempotente`() {
        val once = PhoneNumberNormalizer.normalize("0140855512")
        assertEquals("33140855512", once)
        assertEquals(once, PhoneNumberNormalizer.normalize(once))
    }

    @Test
    fun `validate renvoie un message actionnable`() {
        val result = PhoneNumberNormalizer.validate("123")
        assertTrue(result is PhoneNumberNormalizer.Result.Invalid)
        assertTrue((result as PhoneNumberNormalizer.Result.Invalid).message.contains("trop court"))
    }

    @Test
    fun `validate accepte un numéro complet`() {
        val result = PhoneNumberNormalizer.validate("0140855512")
        assertEquals("33140855512", result.normalizedOrNull)
    }

    @Test
    fun `digitsOnly ne garde que les chiffres ascii`() {
        assertEquals("33140855512", PhoneNumberNormalizer.digitsOnly("+33 (1) 40-85 55 12"))
        assertEquals("", PhoneNumberNormalizer.digitsOnly("abc"))
    }

    @Test
    fun `formatForDisplay est cosmétique et réversible en chiffres`() {
        val formatted = PhoneNumberNormalizer.formatForDisplay("33140855512")
        assertEquals("+33 14 08 55 51 2", formatted)
        assertEquals("33140855512", PhoneNumberNormalizer.digitsOnly(formatted))
    }
}
