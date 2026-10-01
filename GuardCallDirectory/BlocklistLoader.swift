import Foundation
import CallKit

struct BlockedEntry: Codable, Hashable {
    let phoneNumber: Int64
    let label: String?
}

struct IdentificationEntry: Codable, Hashable {
    let phoneNumber: Int64
    let label: String
}

struct BlocklistPayload: Codable {
    var blocked: [BlockedEntry]
    var identified: [IdentificationEntry]

    static let fallback = BlocklistPayload(
        blocked: [BlockedEntry(phoneNumber: 1_408_555_5555, label: "Spam")],
        identified: [IdentificationEntry(phoneNumber: 1_877_555_5555, label: "Spam")]
    )
}

enum BlocklistLoader {
    static let appGroupID = "group.com.guardcall.shared"
    static let fileName = "blocklist.json"
    static let userDefaultsKey = "guardcall.blocklist.v1"

    static func load() -> BlocklistPayload {
        // 1) Fichier App Group
        if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID)?
            .appendingPathComponent(fileName),
           let data = try? Data(contentsOf: url),
           let payload = try? JSONDecoder().decode(BlocklistPayload.self, from: data) {
            return payload
        }
        // 2) UserDefaults App Group
        if let defaults = UserDefaults(suiteName: appGroupID),
           let data = defaults.data(forKey: userDefaultsKey),
           let payload = try? JSONDecoder().decode(BlocklistPayload.self, from: data) {
            return payload
        }
        // 3) UserDefaults standard (fallback simu)
        if let data = UserDefaults.standard.data(forKey: userDefaultsKey),
           let payload = try? JSONDecoder().decode(BlocklistPayload.self, from: data) {
            return payload
        }
        return .fallback
    }
}
