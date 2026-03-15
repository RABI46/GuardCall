import SwiftUI

struct BlocklistView: View {
    @EnvironmentObject var callService: CallDirectoryService
    @State private var searchText = ""
    @State private var filterCategory: BlockedNumber.BlockCategory? = nil

    var filteredNumbers: [BlockedNumber] {
        callService.blockedNumbers.filter { n in
            (searchText.isEmpty || "\(n.phoneNumber)".contains(searchText)
                || n.label.localizedCaseInsensitiveContains(searchText))
            && (filterCategory == nil || n.category == filterCategory)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        FilterChip(title: "Tous", isSelected: filterCategory == nil) { filterCategory = nil }
                        ForEach(BlockedNumber.BlockCategory.allCases, id: \.self) { cat in
                            FilterChip(title: cat.rawValue, icon: cat.icon, isSelected: filterCategory == cat) { filterCategory = cat }
                        }
                    }.padding(.horizontal, 4)
                }
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear).padding(.vertical, 8)

                ForEach(filteredNumbers) { number in
                    NumberRow(number: number)
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) { callService.removeNumber(number) } label: { Label("Supprimer", systemImage: "trash") }
                        }
                        .swipeActions(edge: .leading) {
                            Button { callService.toggleNumber(number) } label: {
                                Label(number.isActive ? "Désactiver" : "Activer",
                                      systemImage: number.isActive ? "pause.circle" : "play.circle")
                            }.tint(number.isActive ? .orange : .green)
                        }
                }
            }
            .searchable(text: $searchText, prompt: "Rechercher un numéro...")
            .navigationTitle("Blocklist")
            .overlay {
                if filteredNumbers.isEmpty {
                    ContentUnavailableView("Aucun numéro bloqué", systemImage: "checkmark.shield.fill",
                        description: Text("Chargez la base FR officielle ou ajoutez un numéro"))
                }
            }
        }
    }
}

struct NumberRow: View {
    let number: BlockedNumber
    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill(Color(number.category.color).opacity(0.15)).frame(width: 42, height: 42)
                Image(systemName: number.category.icon).foregroundStyle(Color(number.category.color))
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(formattedNumber).font(.subheadline.bold()).foregroundStyle(number.isActive ? .primary : .secondary)
                Text(number.label).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if !number.isActive {
                Text("Inactif").font(.caption2).padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Color.orange.opacity(0.2)).foregroundStyle(.orange).clipShape(Capsule())
            }
        }
        .opacity(number.isActive ? 1.0 : 0.6)
    }

    var formattedNumber: String {
        let n = "\(number.phoneNumber)"
        if n.hasPrefix("33"), n.count == 11 {
            let local = "0" + n.dropFirst(2)
            var result = "+33"
            var idx = local.index(local.startIndex, offsetBy: 1)
            while idx < local.endIndex {
                result += " "
                let end = local.index(idx, offsetBy: min(2, local.distance(from: idx, to: local.endIndex)))
                result += local[idx..<end]; idx = end
            }
            return result
        }
        return "+" + n
    }
}

struct FilterChip: View {
    let title: String; var icon: String? = nil; let isSelected: Bool; let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon { Image(systemName: icon).font(.caption) }
                Text(title).font(.caption.bold())
            }
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(isSelected ? Color.blue : Color.secondary.opacity(0.15))
            .foregroundStyle(isSelected ? .white : .primary)
            .clipShape(Capsule())
        }
    }
}
