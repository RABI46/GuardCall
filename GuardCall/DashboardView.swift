import SwiftUI

struct DashboardView: View {
    @EnvironmentObject var callService: CallDirectoryService
    @State private var showAddSheet = false
    @State private var pulse = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {

                    // Hero Card
                    ZStack {
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .fill(.ultraThinMaterial)
                            .overlay(RoundedRectangle(cornerRadius: 28)
                                .stroke(callService.isEnabled ? Color.green.opacity(0.4) : Color.red.opacity(0.4), lineWidth: 1.5))
                            .shadow(color: callService.isEnabled ? .green.opacity(0.3) : .red.opacity(0.2), radius: 20)

                        VStack(spacing: 16) {
                            ZStack {
                                Circle()
                                    .fill(callService.isEnabled ? Color.green.opacity(0.15) : Color.red.opacity(0.15))
                                    .frame(width: 90, height: 90)
                                    .scaleEffect(pulse ? 1.15 : 1.0)
                                    .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true), value: pulse)
                                Image(systemName: callService.isEnabled ? "shield.checkmark.fill" : "shield.slash.fill")
                                    .font(.system(size: 40))
                                    .foregroundStyle(callService.isEnabled ? .green : .red)
                            }
                            .onAppear { pulse = true }

                            Text(callService.isEnabled ? "Protection Active" : "Protection Inactive")
                                .font(.title2.bold())
                            Text(callService.isEnabled
                                 ? "\(callService.totalBlocked) numéros bloqués automatiquement"
                                 : "Activez le blocage dans les Réglages")
                                .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)

                            Button { callService.openSettings() } label: {
                                Label(callService.isEnabled ? "Gérer dans Réglages" : "Activer maintenant", systemImage: "gear")
                                    .font(.subheadline.bold())
                                    .padding(.horizontal, 20).padding(.vertical, 10)
                                    .background(callService.isEnabled ? Color.green.opacity(0.2) : Color.blue)
                                    .foregroundStyle(callService.isEnabled ? .green : .white)
                                    .clipShape(Capsule())
                            }
                        }
                        .padding(28)
                    }
                    .padding(.horizontal)

                    // Stats Grid
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                        StatCard(icon: "hand.raised.fill",        title: "Bloqués",      value: "\(callService.totalBlocked)",       color: .red)
                        StatCard(icon: "building.columns.fill",   title: "Base FR",      value: "Officielle",                         color: .blue)
                        StatCard(icon: "clock.badge.checkmark",   title: "Mis à jour",   value: callService.lastUpdateString,         color: .green)
                        StatCard(icon: "person.fill.xmark",       title: "Personnalisés",value: "\(callService.customBlockedCount)", color: .orange)
                    }
                    .padding(.horizontal)

                    // Actions rapides
                    VStack(spacing: 12) {
                        ActionRow(icon: "arrow.down.circle.fill",      title: "Charger base FR officielle", subtitle: "Préfixes ARCEP + Bloctel 2025",   color: .blue)   { callService.loadOfficialFrenchDatabase() }
                        ActionRow(icon: "plus.circle.fill",            title: "Ajouter un numéro",          subtitle: "Bloquer ou identifier",           color: .orange) { showAddSheet = true }
                        ActionRow(icon: "arrow.clockwise.circle.fill", title: "Actualiser l'extension",    subtitle: "Synchroniser les changements",    color: .green)  { callService.reloadExtension() }
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical)
            }
            .navigationTitle("GuardCall 🛡️")
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: $showAddSheet) {
                AddNumberSheet().environmentObject(callService)
            }
        }
    }
}

struct StatCard: View {
    let icon: String; let title: String; let value: String; let color: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon).font(.title2).foregroundStyle(color)
            Text(value).font(.title3.bold())
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct ActionRow: View {
    let icon: String; let title: String; let subtitle: String; let color: Color; let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.title2).foregroundStyle(color).frame(width: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline.bold()).foregroundStyle(.primary)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
            }
            .padding(16).background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }
}

struct AddNumberSheet: View {
    @EnvironmentObject var callService: CallDirectoryService
    @Environment(\.dismiss) var dismiss
    @State private var phoneInput = ""
    @State private var labelInput = ""
    @State private var selectedCategory: BlockedNumber.BlockCategory = .custom

    var body: some View {
        NavigationStack {
            Form {
                Section("Numéro")   { TextField("+33 6 12 34 56 78", text: $phoneInput).keyboardType(.phonePad) }
                Section("Étiquette") { TextField("Ex: Démarchage SFR", text: $labelInput) }
                Section("Catégorie") {
                    Picker("Catégorie", selection: $selectedCategory) {
                        ForEach(BlockedNumber.BlockCategory.allCases, id: \.self) { cat in
                            Label(cat.rawValue, systemImage: cat.icon).tag(cat)
                        }
                    }
                }
            }
            .navigationTitle("Ajouter un numéro")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Ajouter") {
                        callService.addNumber(phoneInput,
                            label: labelInput.isEmpty ? selectedCategory.rawValue : labelInput,
                            category: selectedCategory)
                        dismiss()
                    }
                    .disabled(phoneInput.isEmpty)
                }
            }
        }
    }
}
