import Foundation
import CallKit
import os.log

class CallDirectoryHandler: CXCallDirectoryProvider {

    private let logger = Logger(subsystem: "com.guardcall.app.calldirectory", category: "CallDirectory")

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        // iOS 18.4+ : vérifier que le delegate est bien posé pour capter requestFailed
        context.delegate = self

        let payload = BlocklistLoader.load()
        logger.info("GuardCall: chargement blocklist — \(payload.blocked.count) bloqués, \(payload.identified.count) identifiés. incremental=\(context.isIncremental)")

        do {
            if context.isIncremental {
                // En mode incrémental, on ne ré-ajoute que les deltas.
                // Stratégie simple pour ce squelette : on ne supporte que l'ajout.
                // Pour une suppression, il faudrait persister un journal de diffs.
                // Ici on log et on ne fait rien de destructif.
                logger.warning("GuardCall: incremental reload demandé — aucune suppression appliquée (journal de diffs non implémenté).")
                // Option : tenter d'ajouter quand même les numéros manquants sans doublon
                // Le système lèvera duplicateEntries si doublon, on l'intercepte.
                try addBlockingIncremental(payload.blocked, to: context)
                try addIdentificationIncremental(payload.identified, to: context)
            } else {
                addAllBlockingPhoneNumbers(payload.blocked, to: context)
                addAllIdentificationPhoneNumbers(payload.identified, to: context)
            }
            context.completeRequest()
            logger.info("GuardCall: completeRequest OK")
        } catch {
            logger.error("GuardCall: échec beginRequest — \(error.localizedDescription, privacy: .public)")
            context.cancelRequest(withError: error)
        }
    }

    // MARK: - Full reload

    private func addAllBlockingPhoneNumbers(_ entries: [BlockedEntry], to context: CXCallDirectoryExtensionContext) {
        // Les numéros DOIVENT être strictement croissants
        let sorted = entries.map(\.phoneNumber).sorted()
        // Vérif doublon
        var seen = Set<Int64>()
        for number in sorted {
            guard seen.insert(number).inserted else {
                logger.warning("GuardCall: doublon bloqué ignoré \(number)")
                continue
            }
            context.addBlockingEntry(withNextSequentialPhoneNumber: number)
        }
    }

    private func addAllIdentificationPhoneNumbers(_ entries: [IdentificationEntry], to context: CXCallDirectoryExtensionContext) {
        let sorted = entries.sorted { $0.phoneNumber < $1.phoneNumber }
        var seen = Set<Int64>()
        for entry in sorted {
            guard seen.insert(entry.phoneNumber).inserted else {
                logger.warning("GuardCall: doublon identification ignoré \(entry.phoneNumber)")
                continue
            }
            context.addIdentificationEntry(withNextSequentialPhoneNumber: entry.phoneNumber, label: entry.label)
        }
    }

    // MARK: - Incremental (best-effort)

    private func addBlockingIncremental(_ entries: [BlockedEntry], to context: CXCallDirectoryExtensionContext) throws {
        // En incrémental, addBlockingEntry lève si doublon. On tente et on log.
        let sorted = entries.map(\.phoneNumber).sorted()
        for number in sorted {
            // On ne peut pas savoir côté extension ce qui est déjà présent.
            // On tente ; en cas d'erreur duplicate, on l'ignore et on continue.
            // CXCallDirectory ne permet pas de try/catch fin ; on doit laisser l'OS lever.
            // Donc on ne peut pas vraiment faire du best-effort sans journal.
            // On choisit de ne rien ajouter en incrémental pour éviter duplicateEntries.
            // Le log ci-dessus explique la limitation.
            _ = number
        }
    }

    private func addIdentificationIncremental(_ entries: [IdentificationEntry], to context: CXCallDirectoryExtensionContext) throws {
        // Même limitation que ci-dessus
        _ = entries
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        let logger = Logger(subsystem: "com.guardcall.app.calldirectory", category: "CallDirectory")
        logger.error("GuardCall: requestFailed — \(error.localizedDescription, privacy: .public)")

        // Persiste l'erreur dans l'App Group pour affichage côté app
        let payload: [String: Any] = [
            "date": ISO8601DateFormatter().string(from: Date()),
            "error": error.localizedDescription,
            "code": (error as NSError).code
        ]
        if let data = try? JSONSerialization.data(withJSONObject: payload),
           let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: BlocklistLoader.appGroupID)?
            .appendingPathComponent("last_calldirectory_error.json") {
            try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? data.write(to: url, options: .atomic)
        }
        if let defaults = UserDefaults(suiteName: BlocklistLoader.appGroupID) {
            defaults.set(error.localizedDescription, forKey: "guardcall.lastError")
        }
    }
}
