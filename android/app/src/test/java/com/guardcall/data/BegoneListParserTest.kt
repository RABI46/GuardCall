package com.guardcall.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du parseur de listes Begone.
 *
 * Aucune dépendance Android : le parseur repose sur `javax.xml`, disponible
 * aussi bien sur la plateforme que sur la JVM des tests.
 */
class BegoneListParserTest {

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <array>
        	<dict>
        		<key>addNational</key>
        		<string>true</string>
        		<key>category</key>
        		<string>0</string>
        		<key>number</key>
        		<string>+33162######</string>
        		<key>title</key>
        		<string>Anti démarchage FR</string>
        	</dict>
        	<dict>
        		<key>number</key>
        		<string>+337588#####</string>
        		<key>title</key>
        		<string>OnOff Suspicieux</string>
        	</dict>
        </array>
        </plist>
    """.trimIndent()

    private fun parse(xml: String): List<BlockRule> =
        BegoneListParser.parse(ByteArrayInputStream(xml.toByteArray()))

    @Test
    fun `le plist est lu sans accéder au réseau malgré la DTD distante`() {
        // Le fichier déclare une DTD sur apple.com : sans EntityResolver le
        // parseur tenterait une requête réseau et échouerait hors ligne.
        val rules = parse(sample)
        assertEquals(2, rules.size)
    }

    @Test
    fun `les motifs sont convertis en préfixe et longueur`() {
        val rules = parse(sample)
        assertEquals(
            listOf(
                BlockRule(prefix = "33162", length = 11, label = "Anti démarchage FR", source = "Begone-FR.xml"),
                BlockRule(prefix = "337588", length = 11, label = "OnOff Suspicieux", source = "Begone-FR.xml")
            ),
            rules
        )
    }

    @Test
    fun `les règles sont triées par préfixe`() {
        val prefixes = parse(sample).map { it.prefix }
        assertEquals(prefixes.sorted(), prefixes)
    }

    @Test
    fun `un motif national est converti avec l indicatif pays`() {
        val rule = BegoneListParser.toBlockRule("0162######")
        assertNotNull(rule)
        assertEquals("33162", rule!!.prefix)
        assertEquals(11, rule.length)
    }

    @Test
    fun `un motif sans indicatif international déjà complet est conservé`() {
        val rule = BegoneListParser.toBlockRule("33162######")
        assertNotNull(rule)
        assertEquals("33162", rule!!.prefix)
    }

    @Test
    fun `les séparateurs de lisibilité sont ignorés`() {
        val rule = BegoneListParser.toBlockRule("+33 1 62 ######")
        assertNotNull(rule)
        assertEquals("33162", rule!!.prefix)
        assertEquals(11, rule.length)
    }

    @Test
    fun `un numéro précis sans joker n est pas une plage`() {
        assertNull(BegoneListParser.toBlockRule("+33140855512"))
    }

    @Test
    fun `un joker suivi de chiffres est rejeté`() {
        assertNull(BegoneListParser.toBlockRule("+33162##12##"))
    }

    @Test
    fun `un motif réduit à des jokers est rejeté`() {
        assertNull(BegoneListParser.toBlockRule("######"))
    }

    @Test
    fun `un motif trop long pour un E 164 est rejeté`() {
        assertNull(BegoneListParser.toBlockRule("+3316234567890123####"))
    }

    @Test
    fun `les caractères inattendus sont rejetés`() {
        assertNull(BegoneListParser.toBlockRule("+33162abc###"))
    }

    @Test
    fun `un dict sans numéro est ignoré`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0"><array>
              <dict><key>title</key><string>Sans numéro</string></dict>
              <dict><key>number</key><string>+33948######</string><key>title</key><string>OK</string></dict>
            </array></plist>
        """.trimIndent()
        val rules = parse(xml)
        assertEquals(1, rules.size)
        assertEquals("33948", rules.single().prefix)
    }

    @Test
    fun `un doublon est dédupliqué`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0"><array>
              <dict><key>number</key><string>+33162######</string></dict>
              <dict><key>number</key><string>0162######</string></dict>
            </array></plist>
        """.trimIndent()
        assertEquals(1, parse(xml).size)
    }

    @Test
    fun `l étiquette retombe sur la catégorie si le titre est vide`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0"><array>
              <dict><key>number</key><string>+33949######</string>
                    <key>title</key><string>  </string>
                    <key>category</key><string>0</string></dict>
            </array></plist>
        """.trimIndent()
        assertEquals("0", parse(xml).single().label)
    }

    @Test
    fun `les 18 plages de la liste Begone FR sont toutes exploitables`() {
        // Motifs exacts du fichier `src/main/assets/Begone-FR.xml` : 12 plages
        // ARCEP anti-démarchage + 6 plages « OnOff ». Aucune ne doit être
        // écartée par la validation, et la liste reste dédupliquée.
        val patterns = listOf(
            "+33162######", "+33163######", "+33270######", "+33271######",
            "+33377######", "+33378######", "+33424######", "+33425######",
            "+33568######", "+33569######", "+33948######", "+33949######",
            "+337588#####", "+337589#####",
            "+3364466####", "+3364467####", "+3364468####", "+3364469####"
        )
        val rules = patterns.mapNotNull { BegoneListParser.toBlockRule(it) }

        assertEquals("Aucune des 18 plages ne doit être écartée", 18, rules.size)
        assertTrue("Toutes les plages françaises couvrent un E.164 de 11 chiffres", rules.all { it.length == 11 })
        assertTrue(rules.all { it.prefix.startsWith("33") })
        assertEquals(18, rules.distinctBy { it.key }.size)
    }
}
