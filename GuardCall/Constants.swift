import Foundation

enum Constants {
    static let appGroupIdentifier = "group.com.guardcall.shared"
    static let extensionIdentifier = "com.guardcall.app.calldirectory"

    enum Blocklist {
        static let fileName = "blocklist.json"
        static let userDefaultsKey = "guardcall.blocklist.v1"
    }

    enum Localization {
        static let appName = String(localized: "guardcall.appName", defaultValue: "GuardCall")
        static let statusChecking = String(localized: "guardcall.status.checking", defaultValue: "Vérification…")
        static let extensionEnabled = String(localized: "guardcall.status.enabled", defaultValue: "Protection active")
        static let extensionDisabled = String(localized: "guardcall.status.disabled", defaultValue: "Extension désactivée")
        static let extensionUnknown = String(localized: "guardcall.status.unknown", defaultValue: "Statut inconnu")
        static let reload = String(localized: "guardcall.action.reload", defaultValue: "Recharger l’extension")
        static let openSettings = String(localized: "guardcall.action.openSettings", defaultValue: "Ouvrir Réglages")
    }
}
