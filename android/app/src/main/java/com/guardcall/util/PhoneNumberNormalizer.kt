package com.guardcall.util

/**
 * Normalisation E.164 des numéros de téléphone.
 *
 * GuardCall stocke et compare des E.164 (chiffres seuls,
 * indicatif pays inclus, jamais de 0 initial). Un numéro national saisi
 * (« 01 40 85 55 12 ») est converti avec l'indicatif pays par défaut.
 */
object PhoneNumberNormalizer {

    const val DEFAULT_COUNTRY_CALLING_CODE = "33"

    /** Longueur totale minimale d'un E.164 (indicatif pays + numéro). */
    const val MIN_LENGTH = 8

    /** Longueur totale maximale d'un E.164 (recommandation UIT-T E.164). */
    const val MAX_LENGTH = 15

    /** Ne conserve que les chiffres ASCII. */
    fun digitsOnly(raw: String?): String = raw?.filter { it in '0'..'9' }.orEmpty()

    /**
     * Renvoie le numéro normalisé E.164 (chiffres seuls), ou `null` si la saisie
     * est invalide. Ne lève jamais d'exception.
     */
    @JvmOverloads
    fun normalize(raw: String?, countryCode: String = DEFAULT_COUNTRY_CALLING_CODE): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val digits = digitsOnly(trimmed)
        if (digits.isEmpty()) return null

        val international = toInternational(digits, hasPlusPrefix = trimmed.contains('+'), countryCode = countryCode)

        if (international.length < MIN_LENGTH || international.length > MAX_LENGTH) return null
        if (international.startsWith("0")) return null
        if (international.toLongOrNull() == null) return null
        return international
    }

    /** Version explicite : renvoie la raison du rejet pour affichage utilisateur. */
    fun validate(raw: String?, countryCode: String = DEFAULT_COUNTRY_CALLING_CODE): Result {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return Result.Invalid("Saisissez un numéro de téléphone.")

        val digits = digitsOnly(trimmed)
        if (digits.isEmpty()) return Result.Invalid("Saisissez un numéro de téléphone.")

        val international = toInternational(digits, hasPlusPrefix = trimmed.contains('+'), countryCode = countryCode)

        return when {
            international.length < MIN_LENGTH -> Result.Invalid(
                "Numéro trop court (${international.length} chiffres) : indicatif pays inclus, il en faut au moins $MIN_LENGTH."
            )
            international.length > MAX_LENGTH -> Result.Invalid(
                "Numéro trop long (${international.length} chiffres) : un E.164 en compte au plus $MAX_LENGTH."
            )
            international.startsWith("0") -> Result.Invalid(
                "Numéro invalide « $international » : un E.164 ne commence jamais par 0. Utilisez le format international."
            )
            else -> Result.Valid(international)
        }
    }

    /** Formatage lisible : `33140855512` → `+33 14 08 55 51 2`. Cosmétique uniquement. */
    fun formatForDisplay(number: String): String {
        val digits = digitsOnly(number)
        if (digits.length <= 4) return "+$digits"
        val groups = mutableListOf("+" + digits.take(2))
        var rest = digits.drop(2)
        while (rest.isNotEmpty()) {
            groups += rest.take(2)
            rest = rest.drop(2)
        }
        return groups.joinToString(" ")
    }

    private fun toInternational(digits: String, hasPlusPrefix: Boolean, countryCode: String): String {
        var value = digits

        // Préfixe international écrit « 00 » (convention européenne).
        if (!hasPlusPrefix && value.startsWith("00")) {
            value = value.drop(2)
        }

        // Numéro national : on remplace le 0 de tête par l'indicatif pays.
        if (!hasPlusPrefix && value.startsWith("0") && !value.startsWith("00")) {
            value = countryCode + value.drop(1)
        }

        return value
    }

    sealed class Result {
        data class Valid(val e164: String) : Result()
        data class Invalid(val message: String) : Result()

        val normalizedOrNull: String?
            get() = (this as? Valid)?.e164
    }
}
