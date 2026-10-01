import XCTest
@testable import GuardCall

final class GuardCallTests: XCTestCase {

    func testConstants() {
        XCTAssertEqual(Constants.appGroupIdentifier, "group.com.guardcall.shared")
        XCTAssertEqual(Constants.extensionIdentifier, "com.guardcall.app.calldirectory")
    }

    func testBlocklistStore_roundTrip() throws {
        let store = BlocklistStore()
        let original = BlocklistPayload(
            blocked: [BlockedEntry(phoneNumber: 111, label: "A"), BlockedEntry(phoneNumber: 222, label: nil)],
            identified: [IdentificationEntry(phoneNumber: 333, label: "Spam")]
        )
        try store.save(original)
        let loaded = store.load()
        // Les données chargées doivent contenir au moins les entrées sauvegardées
        XCTAssertTrue(loaded.blocked.contains { $0.phoneNumber == 111 })
        XCTAssertTrue(loaded.blocked.contains { $0.phoneNumber == 222 })
        XCTAssertTrue(loaded.identified.contains { $0.phoneNumber == 333 })
    }

    func testBlocklistStore_addKeepsSortedOrder() throws {
        let store = BlocklistStore()
        // Reset avec payload vide
        try store.save(BlocklistPayload(blocked: [], identified: []))
        try store.addBlocked(phoneNumber: 999)
        try store.addBlocked(phoneNumber: 111)
        try store.addBlocked(phoneNumber: 555)
        let loaded = store.load()
        let numbers = loaded.blocked.map(\.phoneNumber)
        XCTAssertEqual(numbers, numbers.sorted(), "Les numéros doivent rester triés croissants pour CallKit")
        XCTAssertEqual(numbers, [111, 555, 999])
    }

    func testBlocklistStore_addDeduplicates() throws {
        let store = BlocklistStore()
        try store.save(BlocklistPayload(blocked: [], identified: []))
        try store.addBlocked(phoneNumber: 12345)
        try store.addBlocked(phoneNumber: 12345) // doublon
        let loaded = store.load()
        XCTAssertEqual(loaded.blocked.filter { $0.phoneNumber == 12345 }.count, 1)
    }

    func testBlocklistStore_remove() throws {
        let store = BlocklistStore()
        try store.save(BlocklistPayload(blocked: [BlockedEntry(phoneNumber: 100, label: nil)], identified: []))
        try store.removeBlocked(phoneNumber: 100)
        XCTAssertFalse(store.load().blocked.contains { $0.phoneNumber == 100 })
    }

    func testPhoneNumberSortingInvariant() {
        // Invariant CallKit : ordre strictement croissant
        let unsorted: [Int64] = [1_877_555_5555, 1_408_555_5555, 1_800_555_0199]
        let sorted = unsorted.sorted()
        for i in 1..<sorted.count {
            XCTAssertGreaterThan(sorted[i], sorted[i-1])
        }
    }
}
