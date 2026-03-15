import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var callService: CallDirectoryService
    var body: some View {
        NavigationStack {
            List {
                Section("Extension CallKit") {
                    HStack {
                        Label("Statut", systemImage: "shield.fill"); Spacer()
                        Text(callService.extensionStatus).foregroundStyle(callService.isEnabled ? .green : .red).bold()
                    }
                    Button { callService.openSettings() } label: {
                        Label("Ouvrir Réglages iPhone", systemImage: "arrow.up.right.square")
                    }
                }
                Section("Base de données") {
                    Button { callService.loadOfficialFrenchDatabase() } label: {
                        Label("Mettre à jour base FR", systemImage: "arrow.down.circle.fill")
                    }
                    HStack {
                        Label("Source", systemImage: "building.columns"); Spacer()
                        Text("ARCEP / Bloctel 2025").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Section("Extension") {
                    Button { callService.reloadExtension() } label: {
                        Label("Forcer rechargement", systemImage: "arrow.clockwise")
                    }
                }
                Section("À propos") {
                    HStack { Label("Version",    systemImage: "info.circle"); Spacer(); Text("1.0.0").foregroundStyle(.secondary) }
                    HStack { Label("Compatible", systemImage: "iphone");      Spacer(); Text("iOS 18 / iOS 26").foregroundStyle(.secondary) }
                }
            }
            .navigationTitle("Réglages")
        }
    }
}
