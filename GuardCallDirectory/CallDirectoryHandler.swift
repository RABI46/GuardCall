import Foundation
import CallKit
import os

/// Extension Call Directory : publie dans CallKit les numéros à bloquer et ceux
/// à identifier, à partir de la liste persistée dans l'App Group.
///
/// Deux modes sont gérés :
/// - **rechargement complet** : on republie tout, tri croissant + déduplication ;
/// - **rechargement incrémental** (`context.isIncremental`) : on calcule le delta
///   entre l'instantané du dernier annuaire appliqué et l'état courant, puis on
///   applique les suppressions et les ajouts. Sans cet instantané, les
///   suppressions seraient silencieusement perdues.
final class CallDirectoryHandler: CXCallDirectoryProvider {

    private let logger = Logger(subsystem: Constants.extensionIdentifier, category: "CallDirectory")
    private let store = BlocklistStore()

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        do {
            let payload = store.load()

            if context.isIncremental {
                try applyIncremental(payload, to: context)
            } else {
                applyFull(payload, to: context)
            }

            // L'instantané n'est mis à jour qu'après une application réussie :
            // en cas d'échec, le prochain rechargement repartira d'un delta juste.
            try store.saveSnapshot(DirectorySnapshot(payload: payload))

            context.completeRequest()
            let blocked = payload.blockingNumbersAscending.count
            let identified = payload.identificationEntriesAscending.count
            logger.info("GuardCall: annuaire appliqué — \(blocked) bloqués, \(identified) identifiés, "
                        + "incremental=\(context.isIncremental)")
        } catch {
            logger.error("GuardCall: échec beginRequest — \(error.localizedDescription, privacy: .public)")
            context.cancelRequest(withError: error)
        }
    }

    // MARK: - Rechargement complet

    private func applyFull(_ payload: BlocklistPayload, to context: CXCallDirectoryExtensionContext) {
        for number in payload.blockingNumbersAscending {
            context.addBlockingEntry(withNextSequentialPhoneNumber: number)
        }
        for entry in payload.identificationEntriesAscending {
            context.addIdentificationEntry(withNextSequentialPhoneNumber: entry.phoneNumber, label: entry.label)
        }
    }

    // MARK: - Rechargement incrémental

    private func applyIncremental(_ payload: BlocklistPayload,
                                  to context: CXCallDirectoryExtensionContext) throws {
        let previous = store.loadSnapshot()
        let current = DirectorySnapshot(payload: payload)
        let delta = DirectoryDelta.between(previous: previous, current: current)

        guard !delta.isEmpty else {
            logger.info("GuardCall: rechargement incrémental sans changement.")
            return
        }

        for number in delta.blockedToRemove {
            context.removeBlockingEntry(withPhoneNumber: number)
        }
        for number in delta.identifiedToRemove {
            context.removeIdentificationEntry(withPhoneNumber: number)
        }
        for number in delta.blockedToAdd {
            context.addBlockingEntry(withNextSequentialPhoneNumber: number)
        }
        for number in delta.identifiedToAdd {
            let label = payload.identified.first { $0.phoneNumber == number }?.label ?? "Spam suspecté"
            context.addIdentificationEntry(withNextSequentialPhoneNumber: number, label: label)
        }

        logger.info("GuardCall: delta incrémental — "
                    + "+\(delta.blockedToAdd.count)/-\(delta.blockedToRemove.count) bloqués, "
                    + "+\(delta.identifiedToAdd.count)/-\(delta.identifiedToRemove.count) identifiés")
    }
}

// MARK: - CXCallDirectoryExtensionContextDelegate

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {

    /// CallKit signale ici tout échec (numéros non triés, doublons, E.164
    /// invalide…). L'erreur est republiée dans l'App Group pour que l'app puisse
    /// l'afficher au lieu de laisser l'utilisateur devant un statut figé.
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        logger.error("GuardCall: requestFailed — code=\((error as NSError).code) \(error.localizedDescription, privacy: .public)")

        let message = CallDirectoryFailureMessage(
            date: ISO8601DateFormatter().string(from: Date()),
            code: (error as NSError).code,
            message: error.localizedDescription
        )

        let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: Constants.appGroupIdentifier)
        if let data = try? JSONEncoder().encode(message),
           let url = container?.appendingPathComponent(Constants.Storage.lastErrorFileName) {
            try? data.write(to: url, options: [.atomic])
        }

        UserDefaults(suiteName: Constants.appGroupIdentifier)?
            .set(message.message, forKey: Constants.Storage.lastErrorKey)
    }
}

/// Erreur d'extension sérialisée pour affichage côté app.
struct CallDirectoryFailureMessage: Codable {
    let date: String
    let code: Int
    let message: String
}
