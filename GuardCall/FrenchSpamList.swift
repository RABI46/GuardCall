import CallKit

// Source : ARCEP — tranches officielles démarchage téléphonique France 2025
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

    static let surtaxedPrefixes: [Int64] = [
        330899000000, 330898000000, 330897000000
    ]

    static func allNumbers() -> [CXCallDirectoryPhoneNumber] {
        var numbers: [CXCallDirectoryPhoneNumber] = []
        for range in telemarketingRanges {
            var n = range.start
            while n <= range.end {
                numbers.append(n)
                n += 1000
            }
        }
        numbers.append(contentsOf: surtaxedPrefixes)
        return numbers.sorted()
    }
}
