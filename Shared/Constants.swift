import Foundation

/// Identifiants et clés partagés entre l'app, l'extension et les tests.
/// Toute valeur sensible au build est définie ici et **nulle part ailleurs** :
/// `project.yml` reste la seule autre source (bundle IDs), vérifiée par un test.
enum Constants {
    /// App Group commun à l'app et à l'extension Call Directory.
    static let appGroupIdentifier = "group.com.guardcall.shared"

    /// Bundle ID de l'extension — doit être préfixé par le bundle ID de l'app
    /// (`com.guardcall.app`) suivi d'un point, conformément aux exigences Apple.
    static let extensionIdentifier = "com.guardcall.app.calldirectory"

    /// Indicatif pays appliqué aux numéros saisis au format national.
    static let defaultCountryCallingCode = "33"

    enum Storage {
        /// Fichier JSON de la liste, dans le conteneur App Group.
        static let blocklistFileName = "blocklist.json"

        /// Fichier JSON de l'instantané du dernier annuaire appliqué à CallKit,
        /// utilisé pour calculer les deltas en rechargement incrémental.
        static let snapshotFileName = "directory_snapshot.json"

        /// Clé UserDefaults (miroir de secours).
        static let blocklistKey = "guardcall.blocklist.v1"

        /// Clé UserDefaults de l'instantané d'annuaire (miroir de secours).
        static let snapshotKey = "guardcall.directory.snapshot.v1"

        /// Fichier de la dernière erreur remontée par l'extension.
        static let lastErrorFileName = "last_calldirectory_error.json"

        /// Clé de la dernière erreur remontée par l'extension.
        static let lastErrorKey = "guardcall.lastError"
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
