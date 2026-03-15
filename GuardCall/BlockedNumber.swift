import Foundation

struct BlockedNumber: Identifiable, Codable, Hashable {
    let id: UUID
    var phoneNumber: Int64
    var label: String
    var category: BlockCategory
    var dateAdded: Date
    var isActive: Bool

    enum BlockCategory: String, Codable, CaseIterable {
        case spam      = "Spam"
        case scam      = "Arnaque"
        case telemark  = "Démarchage"
        case official  = "Base officielle FR"
        case custom    = "Personnalisé"

        var icon: String {
            switch self {
            case .spam:     return "exclamationmark.bubble.fill"
            case .scam:     return "exclamationmark.triangle.fill"
            case .telemark: return "phone.arrow.down.left.fill"
            case .official: return "building.columns.fill"
            case .custom:   return "person.fill.xmark"
            }
        }
        var color: String {
            switch self {
            case .spam:     return "orange"
            case .scam:     return "red"
            case .telemark: return "yellow"
            case .official: return "blue"
            case .custom:   return "purple"
            }
        }
    }

    init(phoneNumber: Int64, label: String, category: BlockCategory) {
        self.id          = UUID()
        self.phoneNumber = phoneNumber
        self.label       = label
        self.category    = category
        self.dateAdded   = Date()
        self.isActive    = true
    }
}
