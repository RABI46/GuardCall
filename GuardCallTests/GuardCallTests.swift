import XCTest
@testable import GuardCall

/// Tests unitaires de la logique partagée (`Shared/`).
///
/// Chaque test repart d'un état de stockage connu via `setUp()` : aucun test ne
/// dépend de l'ordre d'exécution.
final class GuardCallTests: XCTestCase {

    private var store: BlocklistStore!

    override func setUp() {
        super.setUp()
        store = BlocklistStore()
        try? store.reset()
    }

    override func tearDown() {
        try? store.reset()
        store = nil
        super.tearDown()
    }

    // MARK: - Constantes

    func testConstantsAreStable() {
        XCTAssertEqual(Constants.appGroupIdentifier, "group.com.guardcall.shared")
        XCTAssertEqual(Constants.extensionIdentifier, "com.guardcall.app.calldirectory")
        // Le bundle ID de l'extension DOIT être préfixé par celui de l'app.
        XCTAssertTrue(
            Constants.extensionIdentifier.hasPrefix("com.guardcall.app."),
            "Une extension doit avoir un bundle ID préfixé par celui de l'app conteneur"
        )
    }

    // MARK: - Invariant CallKit (tri strictement croissant + déduplication)

    func testPayloadIsSortedAscendingAndDeduplicated() {
        let payload = BlocklistPayload(
            blocked: [
                BlockedEntry(phoneNumber: 33_612_345_678),
                BlockedEntry(phoneNumber: 33_140_855_512),
                BlockedEntry(phoneNumber: 33_612_345_678) // doublon
            ],
            identified: [
                IdentificationEntry(phoneNumber: 1_877_555_5555, label: "Spam"),
                IdentificationEntry(phoneNumber: 1_800_555_0199, label: "Test"),
                IdentificationEntry(phoneNumber: 1_877_555_5555, label: "Doublon")
            ]
        ).sanitized

        XCTAssertEqual(payload.blockingNumbersAscending, [33_140_855_512, 33_612_345_678])
        XCTAssertEqual(payload.identificationEntriesAscending.map(\.phoneNumber),
                       [1_800_555_0199, 1_877_555_5555])
    }

    func testSanitizedIsIdempotent() {
        let payload = BlocklistPayload(
            blocked: [BlockedEntry(phoneNumber: 300), BlockedEntry(phoneNumber: 100)],
            identified: [IdentificationEntry(phoneNumber: 200, label: "x")]
        ).sanitized
        XCTAssertEqual(payload.sanitized, payload)
    }

    func testBlockingNumbersAreStrictlyAscending() {
        let payload = BlocklistPayload(
            blocked: (1...50).shuffled().map { BlockedEntry(phoneNumber: Int64($0) * 7) }
        ).sanitized

        let numbers = payload.blockingNumbersAscending
        for index in 1..<numbers.count {
            XCTAssertGreaterThan(numbers[index], numbers[index - 1],
                                 "CallKit rejette un annuaire non strictement croissant")
        }
    }

    // MARK: - Persistance

    func testRoundTrip() throws {
        let original = BlocklistPayload(
            blocked: [BlockedEntry(phoneNumber: 33_111_111_111, label: "A"),
                      BlockedEntry(phoneNumber: 33_222_222_222)],
            identified: [IdentificationEntry(phoneNumber: 33_333_333_333, label: "Spam")]
        )
        try store.save(original)

        let loaded = store.load()
        XCTAssertEqual(loaded.blocked.map(\.phoneNumber), [33_111_111_111, 33_222_222_222])
        XCTAssertEqual(loaded.identified.map(\.phoneNumber), [33_333_333_333])
    }

    func testAddKeepsSortedOrder() throws {
        try store.addBlocked(phoneNumber: 33_999_999_999)
        try store.addBlocked(phoneNumber: 33_111_111_111)
        try store.addBlocked(phoneNumber: 33_555_555_555)

        XCTAssertEqual(store.load().blocked.map(\.phoneNumber),
                       [33_111_111_111, 33_555_555_555, 33_999_999_999])
    }

    func testAddDeduplicatesAndReportsIt() throws {
        XCTAssertTrue(try store.addBlocked(phoneNumber: 33_123_456_789))
        XCTAssertFalse(try store.addBlocked(phoneNumber: 33_123_456_789),
                       "Un doublon doit être signalé et non ré-inséré")
        XCTAssertEqual(store.load().blocked.filter { $0.phoneNumber == 33_123_456_789 }.count, 1)
    }

    func testRemove() throws {
        try store.addBlocked(phoneNumber: 33_100_000_000)
        try store.removeBlocked(phoneNumber: 33_100_000_000)
        XCTAssertFalse(store.load().blocked.contains { $0.phoneNumber == 33_100_000_000 })
    }

    func testDefaultPayloadBlocksNothing() {
        XCTAssertEqual(BlocklistPayload.empty.blocked.count, 0,
                       "L'app ne doit bloquer aucun numéro par défaut")
        XCTAssertEqual(store.load().blocked.count, 0)
    }

    // MARK: - Normalisation E.164

    func testNormalizesFrenchNationalNumber() {
        XCTAssertEqual(PhoneNumber.normalizeE164("01 40 85 55 12"), 33_140_855_512)
        XCTAssertEqual(PhoneNumber.normalizeE164("0612345678"), 33_612_345_678)
    }

    func testNormalizesInternationalFormats() {
        XCTAssertEqual(PhoneNumber.normalizeE164("+33140855512"), 33_140_855_512)
        XCTAssertEqual(PhoneNumber.normalizeE164("0033140855512"), 33_140_855_512)
        XCTAssertEqual(PhoneNumber.normalizeE164("33140855512"), 33_140_855_512)
        XCTAssertEqual(PhoneNumber.normalizeE164("+1 (408) 555-5555"), 1_408_555_5555)
    }

    func testRejectsInvalidNumbers() {
        XCTAssertNil(PhoneNumber.normalizeE164(""))
        XCTAssertNil(PhoneNumber.normalizeE164("abc"))
        XCTAssertNil(PhoneNumber.normalizeE164("123"), "Trop court pour être un E.164")
        XCTAssertNil(PhoneNumber.normalizeE164("1234567890123456"), "Plus de 15 chiffres")
    }

    func testValidationReportsAnActionableMessage() {
        if case .failure(let failure) = PhoneNumber.validate("123") {
            XCTAssertTrue(failure.message.contains("trop court"))
        } else {
            XCTFail("« 123 » doit être rejeté")
        }
    }

    func testNormalizeE164IsIdempotent() {
        let once = PhoneNumber.normalizeE164("0140855512")
        XCTAssertNotNil(once)
        if let once {
            XCTAssertEqual(PhoneNumber.normalizeE164(String(once)), once)
        }
    }

    func testDisplayFormatting() {
        XCTAssertEqual(PhoneNumber.display(33_140_855_512), "+33 14 08 55 51 2")
    }

    // MARK: - Rechargement incrémental

    func testDeltaComputesAdditionsAndRemovals() {
        let previous = DirectorySnapshot(blocked: [10, 20, 30], identified: [100])
        let current = DirectorySnapshot(blocked: [20, 30, 40], identified: [100, 200])

        let delta = DirectoryDelta.between(previous: previous, current: current)

        XCTAssertEqual(delta.blockedToAdd, [40])
        XCTAssertEqual(delta.blockedToRemove, [10])
        XCTAssertEqual(delta.identifiedToAdd, [200])
        XCTAssertEqual(delta.identifiedToRemove, [])
        XCTAssertFalse(delta.isEmpty)
    }

    func testDeltaIsEmptyWhenNothingChanged() {
        let snapshot = DirectorySnapshot(blocked: [1, 2, 3], identified: [9])
        XCTAssertTrue(DirectoryDelta.between(previous: snapshot, current: snapshot).isEmpty)
    }

    func testDeltaAdditionsAreAscending() {
        let previous = DirectorySnapshot.empty
        let current = DirectorySnapshot(blocked: [300, 100, 200], identified: [])
        XCTAssertEqual(DirectoryDelta.between(previous: previous, current: current).blockedToAdd,
                       [100, 200, 300])
    }

    func testSnapshotRoundTrip() throws {
        try store.saveSnapshot(DirectorySnapshot(blocked: [1, 2], identified: [3]))
        XCTAssertEqual(store.loadSnapshot().blocked, [1, 2])
        XCTAssertEqual(store.loadSnapshot().identified, [3])
    }
}
