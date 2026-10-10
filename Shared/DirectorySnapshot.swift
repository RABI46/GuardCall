import Foundation

/// Instantané de l'annuaire **effectivement appliqué** à CallKit lors du dernier
/// rechargement réussi. Il permet de calculer les deltas (ajouts / suppressions)
/// lors d'un rechargement incrémental, sans quoi les suppressions seraient
/// silencieusement perdues.
struct DirectorySnapshot: Codable, Equatable {
    var blocked: [Int64]
    var identified: [Int64]

    init(blocked: [Int64] = [], identified: [Int64] = []) {
        self.blocked = blocked
        self.identified = identified
    }

    static let empty = DirectorySnapshot()

    init(payload: BlocklistPayload) {
        self.blocked = payload.blockingNumbersAscending
        self.identified = payload.identificationEntriesAscending.map(\.phoneNumber)
    }

    var isEmpty: Bool { blocked.isEmpty && identified.isEmpty }
}

/// Diff entre deux instantanés : ce qu'il faut retirer et ce qu'il faut ajouter.
struct DirectoryDelta: Equatable {
    var blockedToAdd: [Int64]
    var blockedToRemove: [Int64]
    var identifiedToAdd: [Int64]
    var identifiedToRemove: [Int64]

    static let empty = DirectoryDelta(blockedToAdd: [], blockedToRemove: [],
                                      identifiedToAdd: [], identifiedToRemove: [])

    var isEmpty: Bool {
        blockedToAdd.isEmpty && blockedToRemove.isEmpty &&
        identifiedToAdd.isEmpty && identifiedToRemove.isEmpty
    }

    /// Calcule le delta à appliquer pour passer de `previous` à `current`.
    /// Les listes d'ajout sont triées croissantes, comme l'exige CallKit.
    static func between(previous: DirectorySnapshot, current: DirectorySnapshot) -> DirectoryDelta {
        let previousBlocked = Set(previous.blocked)
        let currentBlocked = Set(current.blocked)
        let previousIdentified = Set(previous.identified)
        let currentIdentified = Set(current.identified)

        return DirectoryDelta(
            blockedToAdd: current.blocked.filter { !previousBlocked.contains($0) }.sorted(),
            blockedToRemove: previous.blocked.filter { !currentBlocked.contains($0) }.sorted(),
            identifiedToAdd: current.identified.filter { !previousIdentified.contains($0) }.sorted(),
            identifiedToRemove: previous.identified.filter { !currentIdentified.contains($0) }.sorted()
        )
    }
}
