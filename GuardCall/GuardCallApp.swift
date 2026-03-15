import SwiftUI

@main
struct GuardCallApp: App {
    @StateObject private var callService = CallDirectoryService()
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(callService)
                .onAppear { callService.checkStatus() }
        }
    }
}
