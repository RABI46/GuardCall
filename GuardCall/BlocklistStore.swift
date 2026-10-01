import Foundation

/// Modèle partagé entre l'app et l'extension via App Group.
struct BlockedEntry: Codable, Hashable, Identifiable {
    var id: String { phoneNumber }
    let phoneNumber: Int64
    let label: String?

    init(phoneNumber: Int64, label: String? = nil) {
        self.phoneNumber = phoneNumber
        self.label = label
    }
}

struct IdentificationEntry: Codable, Hashable {
    let phoneNumber: Int64
    let label: String
}

struct BlocklistPayload: Codable {
    var blocked: [BlockedEntry]
    var identified: [IdentificationEntry]

    static let `default` = BlocklistPayload(
        blocked: [
            BlockedEntry(phoneNumber: 1_408_555_5555, label: "Spam"),
            BlockedEntry(phoneNumber: 1_408_555_1234, label: "Démarchage")
        ],
        identified: [
            IdentificationEntry(phoneNumber: 1_877_555_5555, label: "Spam suspecté"),
            IdentificationEntry(phoneNumber: 1_800_555_0199, label: "GuardCall — Test")
        ]
    )
}

/// Persistance via App Group : UserDefaults + fichier JSON (l'extension lit le fichier, plus fiable).
final class BlocklistStore {
    private let appGroupID = Constants.appGroupIdentifier
    private var groupDefaults: UserDefaults? { UserDefaults(suiteName: appGroupID) }

    private var groupContainerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID)
    }

    private var blocklistFileURL: URL? {
        groupContainerURL?.appendingPathComponent(Constants.Blocklist.fileName)
    }

    // MARK: - Public

    func load() -> BlocklistPayload {
        // 1) Essaye fichier
        if let url = blocklistFileURL, let data = try? Data(contentsOf: url),
           let decoded = try? JSONDecoder().decode(BlocklistPayload.self, from: data) {
            return decoded
        }
        // 2) Fallback UserDefaults
        if let defaults = groupDefaults, let data = defaults.data(forKey: Constants.Blocklist.userDefaultsKey),
           let decoded = try? JSONDecoder().decode(BlocklistPayload.self, from: data) {
            return decoded
        }
        // 3) Fallback UserDefaults standard (simulateur sans App Group)
        if let data = UserDefaults.standard.data(forKey: Constants.Blocklist.userDefaultsKey),
           let decoded = try? JSONDecoder().decode(BlocklistPayload.self, from: data) {
            return decoded
        }
        return .default
    }

    func save(_ payload: BlocklistPayload) throws {
        let data = try JSONEncoder().encode(payload)

        // UserDefaults (App Group + standard fallback)
        groupDefaults?.set(data, forKey: Constants.Blocklist.userDefaultsKey)
        UserDefaults.standard.set(data, forKey: Constants.Blocklist.userDefaultsKey)

        // Fichier partagé
        guard let url = blocklistFileURL else { return }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                withIntermediateDirectories: true)
        try data.write(to: url, options: [.atomic])
    }

    /// Ajoute un numéro à bloquer, en gardant l'ordre croissant et sans doublon.
    func addBlocked(phoneNumber: Int64, label: String? = nil) throws {
        var payload = load()
        guard !payload.blocked.contains(where: { $0.phoneNumber == phoneNumber }) else { return }
        payload.blocked.append(BlockedEntry(phoneNumber: phoneNumber, label: label))
        payload.blocked.sort { $0.phoneNumber < $1.phoneNumber }
        try save(payload)
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
        payload.identified.sort { $0.phoneNumber < $1.phoneNumber }
        try save(payload)
    }
}
