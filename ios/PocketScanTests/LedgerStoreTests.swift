//
//  LedgerStoreTests.swift
//  PocketScanTests (iOS parity target)
//
//  Round-trips the SQLite ledger through a temp-file database.
//

import XCTest
@testable import PocketScan

final class LedgerStoreTests: XCTestCase {

    private func makeStore() -> LedgerStore {
        let path = NSTemporaryDirectory() + "pocketscan-test-\(UUID().uuidString).sqlite"
        return LedgerStore(path: path)
    }

    private func sample(merchant: String, total: Int64, currency: String) -> Receipt {
        Receipt(
            id: 0,
            merchant: merchant,
            purchaseEpochDay: 20000,
            totalMinor: total,
            currency: currency,
            imagePath: nil,
            rawText: "raw",
            createdAt: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }

    func testInsertAndFetch() {
        let store = makeStore()
        let id = store.insert(sample(merchant: "Cafe", total: 1299, currency: "USD"))
        XCTAssertGreaterThan(id, 0)

        let all = store.all()
        XCTAssertEqual(all.count, 1)
        XCTAssertEqual(all.first?.merchant, "Cafe")
        XCTAssertEqual(all.first?.totalMinor, 1299)
    }

    func testTotalMinorSumsByCurrency() {
        let store = makeStore()
        store.insert(sample(merchant: "A", total: 1000, currency: "USD"))
        store.insert(sample(merchant: "B", total: 500, currency: "USD"))
        store.insert(sample(merchant: "C", total: 999, currency: "TRY"))

        XCTAssertEqual(store.totalMinor(currency: "USD"), 1500)
        XCTAssertEqual(store.totalMinor(currency: "TRY"), 999)
    }

    func testDelete() {
        let store = makeStore()
        let id = store.insert(sample(merchant: "Gone", total: 100, currency: "USD"))
        store.delete(id: id)
        XCTAssertTrue(store.all().isEmpty)
    }
}
