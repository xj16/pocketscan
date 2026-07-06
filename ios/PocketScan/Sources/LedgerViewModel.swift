//
//  LedgerViewModel.swift
//  PocketScan (iOS parity target)
//

import Foundation
import Combine

#if canImport(UIKit)
import UIKit
#endif

/// Drives the SwiftUI ledger UI: holds the receipt list + running total and
/// runs the offline scan pipeline (Vision OCR + parser) on an imported image.
@MainActor
final class LedgerViewModel: ObservableObject {

    @Published private(set) var receipts: [Receipt] = []
    @Published private(set) var totalMinorUSD: Int64 = 0
    @Published var isProcessing = false
    @Published var pendingParse: ParsedReceipt?

    private let store: LedgerStore
    private let recognizer = TextRecognizer()

    init(store: LedgerStore) {
        self.store = store
        refresh()
    }

    func refresh() {
        receipts = store.all()
        totalMinorUSD = store.totalMinor(currency: "USD")
    }

    #if canImport(UIKit)
    /// Runs OCR + parsing on an imported photo and stages the parsed fields.
    func scan(image: UIImage) async {
        isProcessing = true
        defer { isProcessing = false }
        do {
            let text = try await recognizer.recognize(image: image)
            pendingParse = ReceiptParser.parse(text)
        } catch {
            pendingParse = ParsedReceipt(
                merchant: nil, date: nil, totalMinor: nil, currency: nil)
        }
    }
    #endif

    /// Commits an edited receipt to the ledger.
    func save(merchant: String, totalMajor: Double, currency: String,
              date: Date?, rawText: String) {
        let epochDay = date.map { Int64($0.timeIntervalSince1970 / 86_400) }
        let receipt = Receipt(
            id: 0,
            merchant: merchant.isEmpty ? "Unknown merchant" : merchant,
            purchaseEpochDay: epochDay,
            totalMinor: ReceiptParser.majorToMinor(totalMajor),
            currency: currency.isEmpty ? "USD" : currency.uppercased(),
            imagePath: nil,
            rawText: rawText,
            createdAt: Int64(Date().timeIntervalSince1970 * 1000)
        )
        store.insert(receipt)
        pendingParse = nil
        refresh()
    }

    func delete(_ receipt: Receipt) {
        store.delete(id: receipt.id)
        refresh()
    }
}
