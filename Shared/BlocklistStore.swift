import Foundation

/// Persistance de la liste des numéros dans le conteneur App Group partagé entre
/// l'app et l'extension Call Directory.
///
/// Le **fichier JSON est la seule source de vérité** ; UserDefaults (App Group)
/// n'est qu'un miroir de secours écrit à chaque enregistrement. Les échecs
/// d'écriture ne sont jamais avalés : ils remontent sous forme de
/// `BlocklistError`, ce qui évite le désynchronisme UI ↔ extension.
final class BlocklistStore {

    private let fileManager: FileManager

    init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
    }

    // MARK: - Emplacements

    var containerURL: URL? {
        fileManager.containerURL(forSecurityApplicationGroupIdentifier: Constants.appGroupIdentifier)
    }

    private var blocklistURL: URL? {
        containerURL?.appendingPathComponent(Constants.Storage.blocklistFileName)
    }

    private var snapshotURL: URL? {
        containerURL?.appendingPathComponent(Constants.Storage.snapshotFileName)
    }

    private var groupDefaults: UserDefaults? {
        UserDefaults(suiteName: Constants.appGroupIdentifier)
    }

    // MARK: - Lecture

    func load() -> BlocklistPayload {
        if let payload = readPayload(from: blocklistURL) { return payload }
        if let defaults = groupDefaults,
           let data = defaults.data(forKey: Constants.Storage.blocklistKey),
           let payload = try? JSONDecoder().decode(BlocklistPayload.self, from: data) {
            return payload.sanitized
        }
        #if DEBUG
        // Simulateur sans entitlement App Group provisionné : on retombe sur les
        // defaults standard pour rester testable. Chemin absent des builds Release.
        if let data = UserDefaults.standard.data(forKey: Constants.Storage.blocklistKey),
           let payload = try? JSONDecoder().decode(BlocklistPayload.self, from: data) {
            return payload.sanitized
        }
        #endif
        return .empty
    }

    // MARK: - Écriture

    @discardableResult
    func save(_ payload: BlocklistPayload) throws -> BlocklistPayload {
        let sanitized = payload.sanitized
        let data: Data
        do {
            data = try JSONEncoder().encode(sanitized)
        } catch {
            throw BlocklistError.encodingFailed
        }

        try write(data: data, to: blocklistURL, fallbackKey: Constants.Storage.blocklistKey)
        return sanitized
    }

    /// Ajoute un numéro à bloquer. Renvoie `false` si le numéro était déjà présent.
    @discardableResult
    func addBlocked(phoneNumber: Int64, label: String? = nil) throws -> Bool {
        var payload = load()
        guard !payload.blocked.contains(where: { $0.phoneNumber == phoneNumber }) else { return false }
        payload.blocked.append(BlockedEntry(phoneNumber: phoneNumber, label: label))
        try save(payload)
        return true
    }

    func removeBlocked(phoneNumber: Int64) throws {
        var payload = load()
        payload.blocked.removeAll { $0.phoneNumber == phoneNumber }
        try save(payload)
    }

    func addIdentification(phoneNumber: Int64, label: String) throws {
        var payload = load()
        payload.identified.removeAll { $0.phoneNumber == phoneNumber }
        payload.identified.append(IdentificationEntry(phoneNumber: phoneNumber, label: label))
        try save(payload)
    }

    func removeIdentification(phoneNumber: Int64) throws {
        var payload = load()
        payload.identified.removeAll { $0.phoneNumber == phoneNumber }
        try save(payload)
    }

    /// Remplace toute la liste — utilisé par les tests pour repartir d'un état connu.
    func reset() throws {
        try save(.empty)
        try saveSnapshot(.empty)
    }

    // MARK: - Instantané d'annuaire (rechargement incrémental)

    func loadSnapshot() -> DirectorySnapshot {
        if let url = snapshotURL,
           let data = try? Data(contentsOf: url),
           let snapshot = try? JSONDecoder().decode(DirectorySnapshot.self, from: data) {
            return snapshot
        }
        if let defaults = groupDefaults,
           let data = defaults.data(forKey: Constants.Storage.snapshotKey),
           let snapshot = try? JSONDecoder().decode(DirectorySnapshot.self, from: data) {
            return snapshot
        }
        #if DEBUG
        if let data = UserDefaults.standard.data(forKey: Constants.Storage.snapshotKey),
           let snapshot = try? JSONDecoder().decode(DirectorySnapshot.self, from: data) {
            return snapshot
        }
        #endif
        return .empty
    }

    func saveSnapshot(_ snapshot: DirectorySnapshot) throws {
        let data: Data
        do {
            data = try JSONEncoder().encode(snapshot)
        } catch {
            throw BlocklistError.encodingFailed
        }
        guard let url = snapshotURL else {
            #if DEBUG
            groupDefaults?.set(data, forKey: Constants.Storage.snapshotKey)
            UserDefaults.standard.set(data, forKey: Constants.Storage.snapshotKey)
            return
            #else
            throw BlocklistError.containerUnavailable
            #endif
        }
        groupDefaults?.set(data, forKey: Constants.Storage.snapshotKey)
        try writeAtomically(data: data, to: url)
    }

    // MARK: - Private

    private func readPayload(from url: URL?) -> BlocklistPayload? {
        guard let url else { return nil }
        guard let data = try? Data(contentsOf: url) else { return nil }
        guard let payload = try? JSONDecoder().decode(BlocklistPayload.self, from: data) else { return nil }
        return payload.sanitized
    }

    private func write(data: Data, to url: URL?, fallbackKey: String) throws {
        // Miroir UserDefaults d'abord : c'est l'écriture la plus robuste.
        groupDefaults?.set(data, forKey: fallbackKey)
        #if DEBUG
        UserDefaults.standard.set(data, forKey: fallbackKey)
        #endif

        guard let url else {
            #if DEBUG
            return
            #else
            throw BlocklistError.containerUnavailable
            #endif
        }
        try writeAtomically(data: data, to: url)
    }

    private func writeAtomically(data: Data, to url: URL) throws {
        do {
            try fileManager.createDirectory(at: url.deletingLastPathComponent(),
                                            withIntermediateDirectories: true)
            try data.write(to: url, options: [.atomic])
        } catch {
            throw BlocklistError.writeFailed(error.localizedDescription)
        }
    }
}
