package com.guardcall.data

import com.guardcall.util.PhoneNumberNormalizer
import java.io.InputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Lecture des listes au format **Begone** — un `.plist` Apple :
 *
 * ```xml
 * <plist version="1.0"><array>
 *   <dict>
 *     <key>number</key><string>+33162######</string>
 *     <key>title</key><string>Anti démarchage FR</string>
 *   </dict>
 * </array></plist>
 * ```
 *
 * Deux choix structurants :
 *
 * 1. **Parsing DOM (`javax.xml`) plutôt qu'`android.util.Xml`** : l'API est
 *    présente sur la plateforme Android *et* sur la JVM des tests unitaires, ce
 *    qui rend le parseur vérifiable sans Robolectric.
 * 2. **Aucune résolution de DTD** : le fichier déclare une DTD distante
 *    (`apple.com`). Un `EntityResolver` renvoyant une source vide garantit
 *    qu'aucun accès réseau n'est déclenché au filtrage d'appel.
 *
 * La clé `addNational` du format est délibérément ignorée : l'application
 * stocke et compare exclusivement des E.164, une seule forme suffit.
 */
object BegoneListParser {

    /** Nom du fichier liste embarqué dans `src/main/assets`. */
    const val SOURCE_BEGONE_FR = "Begone-FR.xml"

    /**
     * Parse le flux et renvoie les règles exploitables, dédupliquées et triées.
     * Les motifs inexploitables sont ignorés (jamais d'exception).
     */
    fun parse(stream: InputStream, source: String = SOURCE_BEGONE_FR): List<BlockRule> {
        val rules = mutableListOf<BlockRule>()
        val builder = newDocumentBuilder()
        val document = stream.use { builder.parse(it) }
        document.documentElement?.normalize()
        document.documentElement?.let { collectRules(it, rules, source) }
        return rules.distinctBy { it.key }.sortedBy { it.prefix }
    }

    /**
     * Convertit un motif Begone en règle de plage.
     *
     * - `+33162######` → préfixe `33162`, longueur 11 (6 jokers).
     * - `0162######`   → même règle, le `0` national étant converti avec
     *   l'indicatif pays par défaut, comme à la saisie manuelle.
     *
     * Renvoie `null` si le motif ne décrit pas une plage exploitable
     * (absence de joker, joker suivi de chiffres, longueur hors E.164…).
     */
    fun toBlockRule(pattern: String, label: String? = null, source: String? = null): BlockRule? {
        val prefix = StringBuilder()
        var wildcards = 0
        var sawWildcard = false
        var isInternational = false

        for (character in pattern) {
            when {
                character == '+' -> {
                    // L'indicatif international ne peut apparaître qu'en tête.
                    if (sawWildcard || prefix.isNotEmpty()) return null
                    isInternational = true
                }

                character.isWhitespace() || character == '.' || character == '-' || character == '(' || character == ')' -> {
                    // Séparateurs de lisibilité : ignorés, mais interdits après un joker
                    // (ils feraient partie de la plage).
                    if (sawWildcard) return null
                }

                character == '#' -> {
                    wildcards++
                    sawWildcard = true
                }

                character.isDigit() -> {
                    // Un chiffre après un joker rend le motif ambigu : on l'écarte
                    // plutôt que de deviner.
                    if (sawWildcard) return null
                    prefix.append(character)
                }

                else -> return null
            }
        }

        if (wildcards == 0 || prefix.isEmpty()) return null

        val digits = prefix.toString()
        val e164Prefix = when {
            isInternational -> digits
            digits.startsWith("0") ->
                PhoneNumberNormalizer.DEFAULT_COUNTRY_CALLING_CODE + digits.drop(1)
            else -> digits
        }
        if (e164Prefix.startsWith("0")) return null

        val length = e164Prefix.length + wildcards
        if (length < PhoneNumberNormalizer.MIN_LENGTH || length > PhoneNumberNormalizer.MAX_LENGTH) return null

        return BlockRule(
            prefix = e164Prefix,
            length = length,
            // `ifBlank` exigerait un lambda non nul : `takeIf` est la forme sûre ici.
            label = label?.takeIf { it.isNotBlank() },
            source = source
        )
    }

    // MARK: - Interne

    private fun newDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        // Durcissement : limite l'expansion d'entités (billion laughs) sur une
        // liste importée depuis un fichier tiers.
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
    }.newDocumentBuilder().apply {
        // La DTD du plist pointe vers apple.com : on la résout localement par une
        // source vide, sinon le parseur tenterait un accès réseau.
        setEntityResolver { _, _ -> InputSource(StringReader("")) }
    }

    private fun collectRules(node: Node, out: MutableList<BlockRule>, source: String) {
        if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == "dict") {
            readEntry(node, source)?.let(out::add)
            return
        }
        val children = node.childNodes ?: return
        for (index in 0 until children.length) {
            children.item(index)?.let { collectRules(it, out, source) }
        }
    }

    /** Lit un `<dict>` : paires `<key>`/valeur, la valeur suivant immédiatement la clé. */
    private fun readEntry(dict: Node, source: String): BlockRule? {
        val children = dict.childNodes ?: return null
        var number: String? = null
        var title: String? = null
        var category: String? = null
        var index = 0

        while (index < children.length) {
            val keyNode = children.item(index)
            index++
            if (keyNode == null || keyNode.nodeType != Node.ELEMENT_NODE || keyNode.nodeName != "key") continue

            val key = keyNode.textContent?.trim().orEmpty()
            var valueNode: Node? = null
            while (index < children.length) {
                val candidate = children.item(index)
                index++
                if (candidate != null && candidate.nodeType == Node.ELEMENT_NODE) {
                    valueNode = candidate
                    break
                }
            }
            val value = valueNode?.textContent?.trim().orEmpty()

            when (key) {
                "number" -> number = value
                "title" -> title = value
                "category" -> category = value
            }
        }

        val pattern = number ?: return null
        // Titre privilégié ; à défaut d'un titre renseigné, on retombe sur la
        // catégorie Begone pour que l'appelant puisse afficher un motif.
        val resolvedLabel = title?.takeIf { it.isNotBlank() } ?: category
        return toBlockRule(pattern, label = resolvedLabel, source = source)
    }
}
