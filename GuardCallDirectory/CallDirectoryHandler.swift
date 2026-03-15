import Foundation
import CallKit

class CallDirectoryHandler: CXCallDirectoryProvider {

    private let sharedDefaults = UserDefaults(suiteName: "group.com.guardcall.shared")

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self
        addAllBlockingNumbers(to: context)
        addAllIdentificationNumbers(to: context)
        context.completeRequest()
    }

    private func addAllBlockingNumbers(to context: CXCallDirectoryExtensionContext) {
        let userBlocked = loadUserBlockedNumbers()
        let frenchSpam  = FrenchSpamList.allNumbers()
        var seen = Set<CXCallDirectoryPhoneNumber>()
        let all  = (userBlocked + frenchSpam).sorted().filter { seen.insert($0).inserted }
        for number in all { context.addBlockingEntry(withNextSequentialPhoneNumber: number) }
    }

    private func addAllIdentificationNumbers(to context: CXCallDirectoryExtensionContext) {
        let identified = loadIdentifiedNumbers().sorted { $0.number < $1.number }
        for entry in identified {
            context.addIdentificationEntry(withNextSequentialPhoneNumber: entry.number, label: entry.label)
        }
    }

    private func loadUserBlockedNumbers() -> [CXCallDirectoryPhoneNumber] {
        guard let data    = sharedDefaults?.data(forKey: "blockedNumbers"),
              let decoded = try? JSONDecoder().decode([BlockedNumberShared].self, from: data)
        else { return [] }
        return decoded.filter { $0.isActive }.map { $0.phoneNumber }
    }

    private func loadIdentifiedNumbers() -> [(number: CXCallDirectoryPhoneNumber, label: String)] {
        guard let data    = sharedDefaults?.data(forKey: "identifiedNumbers"),
              let decoded = try? JSONDecoder().decode([IdentifiedNumberShared].self, from: data)
        else { return [] }
        return decoded.map { ($0.phoneNumber, $0.label) }
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        print("GuardCall Extension Error: \(error.localizedDescription)")
    }
}

struct BlockedNumberShared: Codable {
    let phoneNumber: CXCallDirectoryPhoneNumber
    let label: String
    let isActive: Bool
}

struct IdentifiedNumberShared: Codable {
    let phoneNumber: CXCallDirectoryPhoneNumber
    let label: String
}

// FrenchSpamList doit être inclus dans l'extension aussi
struct FrenchSpamList {
    static let telemarketingRanges: [(start: Int64, end: Int64, label: String)] = [
        (330162000000, 330162999999, "Démarchage FR"),
        (330163000000, 330163999999, "Démarchage FR"),
        (330270000000, 330270999999, "Démarchage FR"),
        (330271000000, 330271999999, "Démarchage FR"),
        (330377000000, 330377999999, "Démarchage FR"),
        (330378000000, 330378999999, "Démarchage FR"),
        (330424000000, 330424999999, "Démarchage FR"),
        (330425000000, 330425999999, "Démarchage FR"),
        (330568000000, 330568999999, "Démarchage FR"),
        (330569000000, 330569999999, "Démarchage FR"),
        (330948000000, 330948999999, "Démarchage FR"),
        (330949000000, 330949999999, "Démarchage FR"),
        (330947500000, 330947599999, "Démarchage Guadeloupe"),
        (330947600000, 330947699999, "Démarchage Guyane"),
        (330947700000, 330947799999, "Démarchage Martinique"),
        (330947800000, 330947899999, "Démarchage La Réunion"),
        (330947900000, 330947999999, "Démarchage Mayotte"),
    ]
    static let surtaxedPrefixes: [Int64] = [330899000000, 330898000000, 330897000000]
    static func allNumbers() -> [CXCallDirectoryPhoneNumber] {
        var numbers: [CXCallDirectoryPhoneNumber] = []
        for range in telemarketingRanges {
            var n = range.start
            while n <= range.end { numbers.append(n); n += 1000 }
        }
        numbers.append(contentsOf: surtaxedPrefixes)
        return numbers.sorted()
    }
}
