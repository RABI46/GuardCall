package com.guardcall.util

object PhoneNumberUtils {
    fun sanitize(input: String): String? {
        val digits = input.filter { it.isDigit() }
        return digits.ifBlank { null }?.takeIf { it.length in 4..15 }
    }
    fun formatForDisplay(number: String): String = number // TODO: libphonenumber si besoin
}
