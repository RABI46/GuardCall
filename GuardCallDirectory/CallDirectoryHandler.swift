import Foundation
import CallKit

class CallDirectoryHandler: CXCallDirectoryProvider {

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        addAllBlockingPhoneNumbers(to: context)
        addAllIdentificationPhoneNumbers(to: context)

        context.completeRequest()
    }

    private func addAllBlockingPhoneNumbers(to context: CXCallDirectoryExtensionContext) {
        // Numbers must be in strictly ascending order
        let phoneNumbers: [CXCallDirectoryPhoneNumber] = [ 1_408_555_5555 ]
        for phoneNumber in phoneNumbers.sorted() {
            context.addBlockingEntry(withNextSequentialPhoneNumber: phoneNumber)
        }
    }

    private func addAllIdentificationPhoneNumbers(to context: CXCallDirectoryExtensionContext) {
        // Numbers must be in strictly ascending order
        let entries: [(CXCallDirectoryPhoneNumber, String)] = [
            (1_877_555_5555, "Spam")
        ]

        let sortedEntries = entries.sorted { $0.0 < $1.0 }

        for (phoneNumber, label) in sortedEntries {
            context.addIdentificationEntry(withNextSequentialPhoneNumber: phoneNumber, label: label)
        }
    }

}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {

    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        // Log error
    }

}
