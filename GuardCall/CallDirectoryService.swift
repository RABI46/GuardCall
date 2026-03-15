import Foundation
import CallKit
import Combine

@MainActor
class CallDirectoryService: ObservableObject {

    @Published var extensionStatus: String = "Inconnu"
    @Published var isEnabled: Bool         = false
    @Published var blockedNumbers: [BlockedNumber] = []
    @Published var totalBlocked: Int       = 0
    @Published var lastUpdate: Date?

    private let defaults = UserDefaults(suiteName: "group.com.guardcall.shared")

    var lastUpdateString: String {
        guard let d = lastUpdate else { return "Jamais" }
        let f = DateFormatter()
        f.dateStyle = .short; f.timeStyle = .short
        return f.string(from: d)
    }

    var customBlockedCount: Int {
        blockedNumbers.filter { $0.category == .custom && $0.isActive }.count
    }

    init() { loadBlockedNumbers() }

    func checkStatus() {
        CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(
            withIdentifier: "com.guardcall.GuardCallDirectory"
        ) { status, _ in
            DispatchQueue.main.async {
                switch status {
                case .disabled: self.extensionStatus = "Désactivé"; self.isEnabled = false
                case .enabled:  self.extensionStatus = "Activé";    self.isEnabled = true
                default:        self.extensionStatus = "Inconnu"
                }
            }
        }
    }

    func openSettings() {
        CXCallDirectoryManager.sharedInstance.openSettings { _ in }
    }

    func reloadExtension() {
        saveBlockedNumbers()
        CXCallDirectoryManager.sharedInstance.reloadExtension(
            withIdentifier: "com.guardcall.GuardCallDirectory"
        ) { _ in
            DispatchQueue.main.async {
                self.lastUpdate = Date()
                self.checkStatus()
            }
        }
    }

    func loadOfficialFrenchDatabase() {
        for range in FrenchSpamList.telemarketingRanges {
            let n = BlockedNumber(phoneNumber: range.start, label: range.label, category: .official)
            if !blockedNumbers.contains(where: { $0.phoneNumber == n.phoneNumber }) {
                blockedNumbers.append(n)
            }
        }
        totalBlocked = blockedNumbers.filter { $0.isActive }.count
        reloadExtension()
    }

    func addNumber(_ phoneString: String, label: String, category: BlockedNumber.BlockCategory) {
        var cleaned = phoneString
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: "+33", with: "33")
        if cleaned.hasPrefix("0") { cleaned = "33" + cleaned.dropFirst() }
        guard let number = Int64(cleaned), number > 0 else { return }
        let blocked = BlockedNumber(phoneNumber: number, label: label, category: category)
        blockedNumbers.append(blocked)
        blockedNumbers.sort { $0.phoneNumber < $1.phoneNumber }
        totalBlocked = blockedNumbers.filter { $0.isActive }.count
        reloadExtension()
    }

    func removeNumber(_ number: BlockedNumber) {
        blockedNumbers.removeAll { $0.id == number.id }
        totalBlocked = blockedNumbers.filter { $0.isActive }.count
        reloadExtension()
    }

    func toggleNumber(_ number: BlockedNumber) {
        if let i = blockedNumbers.firstIndex(where: { $0.id == number.id }) {
            blockedNumbers[i].isActive.toggle()
            totalBlocked = blockedNumbers.filter { $0.isActive }.count
            reloadExtension()
        }
    }

    private func saveBlockedNumbers() {
        if let encoded = try? JSONEncoder().encode(blockedNumbers) {
            defaults?.set(encoded, forKey: "blockedNumbers")
        }
    }

    private func loadBlockedNumbers() {
        if let data = defaults?.data(forKey: "blockedNumbers"),
           let decoded = try? JSONDecoder().decode([BlockedNumber].self, from: data) {
            blockedNumbers = decoded
            totalBlocked   = blockedNumbers.filter { $0.isActive }.count
        }
    }
}
