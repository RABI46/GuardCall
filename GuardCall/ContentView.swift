import SwiftUI
import CallKit

/// État fonctionnel de l'extension. L'UI est pilotée par cet `enum` et **non**
/// par les chaînes localisées : changer un libellé ne peut plus casser l'icône
/// ni la couleur.
enum ExtensionStatus: Equatable {
    case unknown
    case checking
    case enabled
    case disabled
    case failed(String)

    var title: String {
        switch self {
        case .unknown, .failed:
            return Constants.Localization.extensionUnknown
        case .checking:
            return Constants.Localization.statusChecking
        case .enabled:
            return Constants.Localization.extensionEnabled
        case .disabled:
            return Constants.Localization.extensionDisabled
        }
    }

    var systemImage: String {
        switch self {
        case .enabled: return "checkmark.shield.fill"
        case .disabled: return "xmark.shield.fill"
        case .failed: return "exclamationmark.triangle.fill"
        case .unknown, .checking: return "shield.lefthalf.filled"
        }
    }

    var tint: Color {
        switch self {
        case .enabled: return .green
        case .disabled: return .orange
        case .failed: return .red
        case .unknown, .checking: return .secondary
        }
    }

    var showsSettingsShortcut: Bool {
        switch self {
        case .disabled, .failed: return true
        default: return false
        }
    }
}

struct ContentView: View {
    @State private var status: ExtensionStatus = .checking
    @State private var isReloading = false
    @State private var statusMessage: String?
    @State private var blocklist = BlocklistPayload.empty
    @State private var newNumberText = ""
    @State private var showAddSheet = false
    @State private var formError: String?

    private let store = BlocklistStore()

    var body: some View {
        NavigationStack {
            List {
                headerSection
                statusSection
                blocklistSection
                helpSection
            }
            .navigationTitle(Constants.Localization.appName)
            .task { await refreshAll() }
            .refreshable { await refreshAll() }
            .sheet(isPresented: $showAddSheet) { addNumberSheet }
        }
        .accessibilityIdentifier("GuardCall.ContentView")
    }

    // MARK: - Sections

    private var headerSection: some View {
        Section {
            VStack(spacing: 16) {
                Image(systemName: "shield.lefthalf.filled")
                    .imageScale(.large)
                    .foregroundStyle(.tint)
                    .font(.system(size: 60))
                    .accessibilityHidden(true)

                Text(Constants.Localization.appName)
                    .font(.title.bold())

                Text("Protection anti-spam pour vos appels")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 20, leading: 16, bottom: 12, trailing: 16))
        }
    }

    private var statusSection: some View {
        Section("Statut de l’extension") {
            VStack(alignment: .leading, spacing: 10) {
                Label(status.title, systemImage: status.systemImage)
                    .font(.subheadline)
                    .foregroundStyle(status.tint)
                    .accessibilityIdentifier("GuardCall.StatusLabel")

                if let statusMessage {
                    Text(statusMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("GuardCall.StatusError")
                }

                HStack(spacing: 12) {
                    Button {
                        Task { await reloadExtension() }
                    } label: {
                        if isReloading {
                            ProgressView().tint(.white)
                        } else {
                            Text(Constants.Localization.reload)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isReloading)
                    .accessibilityIdentifier("GuardCall.ReloadButton")

                    if status.showsSettingsShortcut {
                        Button(Constants.Localization.openSettings) { openSystemSettings() }
                            .buttonStyle(.bordered)
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }

    private var blocklistSection: some View {
        Section {
            ForEach(blocklist.blocked) { entry in
                HStack {
                    Text(PhoneNumber.display(entry.phoneNumber))
                        .font(.body.monospacedDigit())
                    Spacer()
                    if let label = entry.label {
                        Text(label).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)
            }
            .onDelete(perform: deleteBlocked)

            if blocklist.blocked.isEmpty {
                Text("Aucun numéro bloqué. Ajoutez-en un pour commencer.")
                    .foregroundStyle(.secondary)
            }

            Button {
                formError = nil
                showAddSheet = true
            } label: {
                Label("Ajouter un numéro", systemImage: "plus.circle.fill")
            }
            .accessibilityIdentifier("GuardCall.AddButton")
        } header: {
            Text("Numéros bloqués (\(blocklist.blocked.count))")
        } footer: {
            Text("Les numéros sont stockés dans l’App Group et lus par l’extension Call Directory. "
                 + "L’extension est rechargée automatiquement après chaque modification.")
        }
    }

    private var helpSection: some View {
        Section("Aide") {
            VStack(alignment: .leading, spacing: 8) {
                Text("Pour activer GuardCall :")
                    .font(.subheadline.bold())
                Label("Réglages → Téléphone → Blocage d’appels et identification", systemImage: "gear")
                    .font(.caption)
                Text("Activez GuardCall, puis revenez ici et touchez « Recharger l’extension ».")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Ouvrir les Réglages") { openSystemSettings() }
                    .font(.caption)
            }

            DisclosureGroup("Numéros d’identification") {
                ForEach(blocklist.identified) { entry in
                    HStack {
                        Text(PhoneNumber.display(entry.phoneNumber)).monospacedDigit()
                        Spacer()
                        Text(entry.label).foregroundStyle(.secondary)
                    }.font(.caption)
                }
            }
        }
    }

    private var addNumberSheet: some View {
        NavigationStack {
            Form {
                Section("Numéro à bloquer") {
                    TextField("Ex. 01 40 85 55 12 ou +33140855512", text: $newNumberText)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        .accessibilityIdentifier("GuardCall.NewNumberField")
                        .onChange(of: newNumberText) { _ in formError = nil }

                    if let normalizedPreview {
                        Text("Sera enregistré : \(PhoneNumber.display(normalizedPreview))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                if let formError {
                    Section {
                        Text(formError)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("GuardCall.FormError")
                    }
                }

                Section {
                    Text("Indicatif pays obligatoire : un numéro national (01…, 06…) est converti "
                         + "avec l’indicatif +\(Constants.defaultCountryCallingCode). CallKit rejette "
                         + "tout l’annuaire si un seul numéro n’est pas au format E.164.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Nouveau numéro")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuler") { showAddSheet = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Ajouter") { addCurrentNumber() }
                        .disabled(normalizedPreview == nil)
                }
            }
        }
    }

    // MARK: - Logique

    /// Numéro validé E.164 correspondant à la saisie courante, ou `nil`.
    private var normalizedPreview: Int64? {
        PhoneNumber.normalizeE164(newNumberText)
    }

    @MainActor
    private func refreshAll() async {
        blocklist = store.load()
        await checkExtensionStatus()
    }

    @MainActor
    private func checkExtensionStatus() async {
        status = .checking
        statusMessage = lastReportedExtensionError()

        do {
            let enabledStatus = try await CXCallDirectoryManager.sharedInstance
                .enabledStatus(forExtensionWithIdentifier: Constants.extensionIdentifier)
            switch enabledStatus {
            case .enabled:
                status = .enabled
            case .disabled:
                status = .disabled
            case .unknown:
                status = .unknown
            @unknown default:
                status = .unknown
            }
        } catch {
            status = .failed(error.localizedDescription)
            statusMessage = error.localizedDescription
        }
    }

    @MainActor
    private func reloadExtension() async {
        isReloading = true
        defer { isReloading = false }
        do {
            try await CXCallDirectoryManager.sharedInstance
                .reloadExtension(withIdentifier: Constants.extensionIdentifier)
            statusMessage = nil
            // Le rechargement est asynchrone côté système : on laisse un court
            // délai avant de relire le statut.
            try? await Task.sleep(nanoseconds: 700_000_000)
            await checkExtensionStatus()
        } catch {
            status = .failed(error.localizedDescription)
            statusMessage = error.localizedDescription
        }
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString),
              UIApplication.shared.canOpenURL(url) else { return }
        UIApplication.shared.open(url)
    }

    private func addCurrentNumber() {
        guard let number = normalizedPreview else {
            formError = PhoneNumber.validationMessage(for: newNumberText)
            return
        }
        do {
            try store.addBlocked(phoneNumber: number, label: "Manuel")
            blocklist = store.load()
            newNumberText = ""
            showAddSheet = false
            Task { await reloadExtension() }
        } catch {
            formError = error.localizedDescription
        }
    }

    private func deleteBlocked(at offsets: IndexSet) {
        var failures: [String] = []
        for index in offsets {
            let entry = blocklist.blocked[index]
            do {
                try store.removeBlocked(phoneNumber: entry.phoneNumber)
            } catch {
                failures.append("\(PhoneNumber.display(entry.phoneNumber)) : \(error.localizedDescription)")
            }
        }
        blocklist = store.load()
        statusMessage = failures.isEmpty ? nil : "Suppression impossible — \(failures.joined(separator: ", "))"
        Task { await reloadExtension() }
    }

    /// Dernière erreur publiée par l'extension dans l'App Group.
    private func lastReportedExtensionError() -> String? {
        UserDefaults(suiteName: Constants.appGroupIdentifier)?
            .string(forKey: Constants.Storage.lastErrorKey)
    }
}

// MARK: - Helpers

private extension PhoneNumber {
    /// Message d'erreur lisible pour un échec de validation, `nil` si valide.
    static func validationMessage(for raw: String) -> String? {
        switch validate(raw) {
        case .success: return nil
        case .failure(let failure): return failure.message
        }
    }
}

// MARK: - CallKit async

extension CXCallDirectoryManager {
    /// Variante `async` de `getEnabledStatusForExtension`, absente de l'API.
    func enabledStatus(forExtensionWithIdentifier identifier: String) async throws -> CXCallDirectoryEnabledStatus {
        try await withCheckedThrowingContinuation { continuation in
            getEnabledStatusForExtension(withIdentifier: identifier) { status, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: status)
                }
            }
        }
    }
}

/// `#Preview` exige iOS 17 ; la cible du projet est iOS 16, d'où un
/// `PreviewProvider` classique (déprécié mais disponible depuis iOS 13).
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
