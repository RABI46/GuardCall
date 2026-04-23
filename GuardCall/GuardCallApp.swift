import SwiftUI
import CallKit

@main
struct GuardCallApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @State private var statusMessage = "Checking status..."

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "shield.fill")
                .imageScale(.large)
                .foregroundStyle(.tint)
                .font(.system(size: 60))

            Text("GuardCall")
                .font(.title)

            Text(statusMessage)
                .font(.subheadline)

            Button("Reload Extension") {
                reloadExtension()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .onAppear {
            checkExtensionStatus()
        }
    }

    func checkExtensionStatus() {
        CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(withIdentifier: "com.guardcall.GuardCallDirectory") { status, error in
            DispatchQueue.main.async {
                if let error = error {
                    statusMessage = "Error: \(error.localizedDescription)"
                } else {
                    switch status {
                    case .enabled:
                        statusMessage = "Extension is Enabled"
                    case .disabled:
                        statusMessage = "Extension is Disabled"
                    case .unknown:
                        statusMessage = "Status Unknown"
                    @unknown default:
                        statusMessage = "Unknown Status"
                    }
                }
            }
        }
    }

    func reloadExtension() {
        CXCallDirectoryManager.sharedInstance.reloadExtension(withIdentifier: "com.guardcall.GuardCallDirectory") { error in
            DispatchQueue.main.async {
                if let error = error {
                    statusMessage = "Reload Failed: \(error.localizedDescription)"
                } else {
                    statusMessage = "Extension Reloaded"
                }
            }
        }
    }
}
