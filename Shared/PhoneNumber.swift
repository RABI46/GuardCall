import Foundation

/// Normalisation E.164 des numéros de téléphone.
///
/// CallKit n'accepte que des numéros au format E.164 (indicatif pays inclus,
/// chiffres uniquement). Un numéro invalide fait échouer **tout** le
/// rechargement de l'annuaire, jamais seulement l'entrée fautive : la validation
/// est donc obligatoire avant persistance.
enum PhoneNumber {

    /// Longueur totale minimale d'un E.164 (indicatif pays + numéro).
    static let minLength = 8
    /// Longueur totale maximale d'un E.164 (recommandation UIT-T E.164).
    static let maxLength = 15

    enum Failure: Equatable {
        case empty
        case tooShort(String)
        case tooLong(String)
        case leadingZero(String)

        var message: String {
            switch self {
            case .empty:
                return "Saisissez un numéro de téléphone."
            case .tooShort(let digits):
                return "Numéro trop court (\(digits.count) chiffres) : indicatif pays inclus, "
                    + "il en faut au moins \(PhoneNumber.minLength)."
            case .tooLong(let digits):
                return "Numéro trop long (\(digits.count) chiffres) : un E.164 en compte au plus \(PhoneNumber.maxLength)."
            case .leadingZero(let digits):
                return "Numéro invalide « \(digits) » : un E.164 ne commence jamais par 0. Utilisez le format international."
            }
        }
    }

    /// Résultat explicite : soit un E.164 valide, soit la raison du rejet.
    static func validate(
        _ raw: String,
        defaultCountryCallingCode countryCode: String = Constants.defaultCountryCallingCode
    ) -> Result<Int64, Failure> {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return .failure(.empty) }

        let digits = digitsOnly(trimmed)
        guard !digits.isEmpty else { return .failure(.empty) }

        let international = normalized(digits: digits, hasPlusPrefix: trimmed.contains("+"), countryCode: countryCode)

        guard international.count >= minLength else { return .failure(.tooShort(international)) }
        guard international.count <= maxLength else { return .failure(.tooLong(international)) }
        guard !international.hasPrefix("0") else { return .failure(.leadingZero(international)) }
        guard let value = Int64(international) else { return .failure(.tooLong(international)) }

        return .success(value)
    }

    /// Version compacte pour les contextes où l'on veut simplement `nil`.
    static func normalizeE164(
        _ raw: String,
        defaultCountryCallingCode countryCode: String = Constants.defaultCountryCallingCode
    ) -> Int64? {
        if case .success(let value) = validate(raw, defaultCountryCallingCode: countryCode) { return value }
        return nil
    }

    /// Ne conserve que les chiffres.
    static func digitsOnly(_ raw: String) -> String {
        raw.filter(\.isNumber).filter(\.isASCII).map(String.init).joined()
    }

    /// Formatage lisible pour l'affichage : `33140855512` → `+33 14 08 55 51 2`
    /// (indicatif pays sur 2 chiffres puis groupes de 2). Purement cosmétique :
    /// aucune valeur métier ni aucune comparaison n'en dépend.
    static func display(_ number: Int64) -> String {
        let digits = String(number)
        guard digits.count > 4 else { return "+\(digits)" }

        var groups: [String] = ["+\(digits.prefix(2))"]
        var rest = digits.dropFirst(2)
        while !rest.isEmpty {
            groups.append(String(rest.prefix(2)))
            rest = rest.dropFirst(2)
        }
        return groups.joined(separator: " ")
    }

    // MARK: - Private

    private static func normalized(digits: String, hasPlusPrefix: Bool, countryCode: String) -> String {
        var value = digits

        // Préfixe international écrit « 00 » (convention européenne).
        if !hasPlusPrefix, value.hasPrefix("00") {
            value = String(value.dropFirst(2))
        }

        // Numéro national : on remplace le 0 de tête par l'indicatif pays.
        if !hasPlusPrefix, value.hasPrefix("0"), !value.hasPrefix("00") {
            value = countryCode + value.dropFirst()
        }

        return value
    }
}
