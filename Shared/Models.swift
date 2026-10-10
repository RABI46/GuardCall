import Foundation

// MARK: - Modèles partagés
//
// Ces types sont compilés à la fois dans la cible app (`GuardCall`) et dans la
// cible extension (`GuardCallDirectory`) via le dossier `Shared/` déclaré dans
// `project.yml`. Il n'existe donc qu'UNE seule définition, ce qui interdit toute
// dérive de schéma JSON entre l'app et l'extension.

/// Numéro bloqué. `phoneNumber` est toujours un E.164 normalisé (chiffres seuls,
/// indicatif pays inclus, sans `0` initial) — voir `PhoneNumber.normalizeE164`.
struct BlockedEntry: Codable, Hashable, Identifiable {
    var id: Int64 { phoneNumber }

    let phoneNumber: Int64
    let label: String?

    init(phoneNumber: Int64, label: String? = nil) {
        self.phoneNumber = phoneNumber
        self.label = label
    }
}

/// Numéro identifié (étiqueté) mais non bloqué.
struct IdentificationEntry: Codable, Hashable, Identifiable {
    var id: Int64 { phoneNumber }

    let phoneNumber: Int64
    let label: String
}

/// Contenu complet de l'annuaire, tel que persisté dans l'App Group.
struct BlocklistPayload: Codable, Equatable {
    var blocked: [BlockedEntry]
    var identified: [IdentificationEntry]

    init(blocked: [BlockedEntry] = [], identified: [IdentificationEntry] = []) {
        self.blocked = blocked
        self.identified = identified
    }

    /// Payload vide : aucun blocage par défaut. On ne bloque jamais un numéro
    /// que l'utilisateur n'a pas explicitement ajouté.
    static let empty = BlocklistPayload()

    /// Applique l'invariant CallKit : tri strictement croissant + déduplication.
    /// `sanitized` est idempotent (`payload.sanitized.sanitized == payload.sanitized`).
    var sanitized: BlocklistPayload {
        BlocklistPayload(
            blocked: Self.dedupeSorted(blocked),
            identified: Self.dedupeSortedIdentified(identified)
        )
    }

    /// Numéros bloqués triés croissants, sans doublon — prêts pour
    /// `addBlockingEntry(withNextSequentialPhoneNumber:)`.
    var blockingNumbersAscending: [Int64] {
        var seen = Set<Int64>()
        return blocked
            .map(\.phoneNumber)
            .sorted()
            .filter { seen.insert($0).inserted }
    }

    /// Entrées d'identification triées croissantes, sans doublon — prêtes pour
    /// `addIdentificationEntry(withNextSequentialPhoneNumber:label:)`.
    var identificationEntriesAscending: [IdentificationEntry] {
        var seen = Set<Int64>()
        return identified
            .sorted { $0.phoneNumber < $1.phoneNumber }
            .filter { seen.insert($0.phoneNumber).inserted }
    }

    // MARK: - Private

    private static func dedupeSorted(_ entries: [BlockedEntry]) -> [BlockedEntry] {
        var seen = Set<Int64>()
        return entries
            .sorted { $0.phoneNumber < $1.phoneNumber }
            .filter { seen.insert($0.phoneNumber).inserted }
    }

    private static func dedupeSortedIdentified(_ entries: [IdentificationEntry]) -> [IdentificationEntry] {
        var seen = Set<Int64>()
        return entries
            .sorted { $0.phoneNumber < $1.phoneNumber }
            .filter { seen.insert($0.phoneNumber).inserted }
    }
}

/// Erreurs métier de la persistance, localisées pour affichage direct dans l'UI.
enum BlocklistError: LocalizedError {
    case encodingFailed
    case decodingFailed
    case containerUnavailable
    case writeFailed(String)

    var errorDescription: String? {
        switch self {
        case .encodingFailed:
            return "Impossible d'encoder la liste des numéros."
        case .decodingFailed:
            return "La liste des numéros est illisible et a été ignorée."
        case .containerUnavailable:
            return "Le conteneur partagé (App Group) est indisponible."
        case .writeFailed(let reason):
            return "Échec de l'enregistrement : \(reason)"
        }
    }
}
