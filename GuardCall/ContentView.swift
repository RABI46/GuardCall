import SwiftUI
import CallKit

struct ContentView: View {
    @State private var statusMessage = Constants.Localization.statusChecking
    @State private var isReloading = false
    @State private var lastError: String?
    @State private var showSettingsLink = false

    // Demo blocklist UI state
    @State private var blocklist = BlocklistPayload.default
    @State private var newNumberText = ""
    @State private var showAddSheet = false

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
                Label(statusMessage, systemImage: statusIcon)
                    .font(.subheadline)
                    .foregroundStyle(statusColor)
                    .accessibilityIdentifier("GuardCall.StatusLabel")

                if let lastError {
                    Text(lastError)
                        .font(.caption)
                        .foregroundStyle(.red)
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

                    if showSettingsLink {
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
                    Text(formatted(entry.phoneNumber))
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
                Text("Aucun numéro bloqué. Ajoutez-en un pour tester.")
                    .foregroundStyle(.secondary)
            }

            Button {
                showAddSheet = true
            } label: {
                Label("Ajouter un numéro", systemImage: "plus.circle.fill")
            }
            .accessibilityIdentifier("GuardCall.AddButton")
        } header: {
            Text("Numéros bloqués (\(blocklist.blocked.count))")
        } footer: {
            Text("Les numéros sont stockés dans l’App Group et lus par l’extension Call Directory. Pensez à recharger l’extension après modification.")
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
                ForEach(blocklist.identified, id: \.phoneNumber) { e in
                    HStack {
                        Text(formatted(e.phoneNumber)).monospacedDigit()
                        Spacer()
                        Text(e.label).foregroundStyle(.secondary)
                    }.font(.caption)
                }
            }
        }
    }

    private var addNumberSheet: some View {
        NavigationStack {
            Form {
                Section("Numéro à bloquer") {
                    TextField("Ex. 014085551234", text: $newNumberText)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        .accessibilityIdentifier("GuardCall.NewNumberField")
                }
                Section {
                    Text("Saisissez un numéro complet (chiffres uniquement). Il sera trié automatiquement pour respecter l’ordre croissant exigé par CallKit.")
                        .font(.caption).foregroundStyle(.secondary)
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
                        .disabled(sanitizedNumber == nil)
                }
            }
        }
    }

    // MARK: - Logic

    private var statusIcon: String {
        if statusMessage == Constants.Localization.extensionEnabled { return "checkmark.shield.fill" }
        if statusMessage == Constants.Localization.extensionDisabled { return "xmark.shield.fill" }
        return "shield.lefthalf.filled"
    }

    private var statusColor: Color {
        if statusMessage == Constants.Localization.extensionEnabled { return .green }
        if statusMessage == Constants.Localization.extensionDisabled { return .orange }
        return .secondary
    }

    private var sanitizedNumber: Int64? {
        let digits = newNumberText.filter { $0.isNumber }
        return Int64(digits)
    }

    private func formatted(_ n: Int64) -> String {
        let s = String(n)
        // petit formatage lisible
        return s
    }

    @MainActor
    private func refreshAll() async {
        blocklist = store.load()
        await checkExtensionStatus()
    }

    @MainActor
    private func checkExtensionStatus() async {
        statusMessage = Constants.Localization.statusChecking
        showSettingsLink = false
        lastError = nil

        do {
            let status = try await CXCallDirectoryManager.sharedInstance
                .enabledStatusForExtension(withIdentifier: Constants.extensionIdentifier)
            switch status {
            case .enabled:
                statusMessage = Constants.Localization.extensionEnabled
            case .disabled:
                statusMessage = Constants.Localization.extensionDisabled
                showSettingsLink = true
            case .unknown:
                statusMessage = Constants.Localization.extensionUnknown
            @unknown default:
                statusMessage = Constants.Localization.extensionUnknown
            }
        } catch {
            lastError = error.localizedDescription
            statusMessage = "Erreur : \(error.localizedDescription)"
            showSettingsLink = true
        }
    }

    @MainActor
    private func reloadExtension() async {
        isReloading = true
        defer { isReloading = false }
        do {
            try await CXCallDirectoryManager.sharedInstance
                .reloadExtension(withIdentifier: Constants.extensionIdentifier)
            statusMessage = "Extension rechargée ✓"
            lastError = nil
            // re-vérifie le statut après un court délai (l’OS est asynchrone)
            try? await Task.sleep(nanoseconds: 700_000_000)
            await checkExtensionStatus()
        } catch {
            lastError = error.localizedDescription
            statusMessage = "Échec du rechargement"
            showSettingsLink = true
        }
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString),
              UIApplication.shared.canOpenURL(url) else { return }
        UIApplication.shared.open(url)
    }

    private func addCurrentNumber() {
        guard let n = sanitizedNumber else { return }
        do {
            try store.addBlocked(phoneNumber: n, label: "Manuel")
            blocklist = store.load()
            newNumberText = ""
            showAddSheet = false
            Task { await reloadExtension() }
        } catch {
            lastError = error.localizedDescription
        }
    }

    private func deleteBlocked(at offsets: IndexSet) {
        for idx in offsets {
            let entry = blocklist.blocked[idx]
            try? store.removeBlocked(phoneNumber: entry.phoneNumber)
        }
        blocklist = store.load()
        Task { await reloadExtension() }
    }
}

// MARK: - Async wrappers pour CallKit (iOS 16+ n’a pas encore les overloads async partout)

extension CXCallDirectoryManager {
    func enabledStatusForExtension(withIdentifier identifier: String) async throws -> CXCallDirectoryEnabledStatus {
        try await withCheckedThrowingContinuation { cont in
            getEnabledStatusForExtension(withIdentifier: identifier) { status, error in
                if let error { cont.resume(throwing: error) }
                else { cont.resume(returning: status) }
            }
        }
    }
}

#Preview {
    ContentView()
}
