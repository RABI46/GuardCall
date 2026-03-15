import SwiftUI

struct ContentView: View {
    @EnvironmentObject var callService: CallDirectoryService
    @State private var selectedTab: Tab = .dashboard

    enum Tab: String, CaseIterable {
        case dashboard = "Accueil"
        case blocklist = "Blocklist"
        case settings  = "Réglages"
        var icon: String {
            switch self {
            case .dashboard: return "shield.fill"
            case .blocklist: return "list.bullet.rectangle.fill"
            case .settings:  return "gearshape.fill"
            }
        }
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(Tab.allCases, id: \.self) { tab in
                Group {
                    switch tab {
                    case .dashboard: DashboardView()
                    case .blocklist: BlocklistView()
                    case .settings:  SettingsView()
                    }
                }
                .tabItem { Label(tab.rawValue, systemImage: tab.icon) }
                .tag(tab)
            }
        }
        .tint(.blue)
    }
}
