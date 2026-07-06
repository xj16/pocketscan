//
//  Receipt.swift
//  PocketScan (iOS parity target)
//

import Foundation

/// A scanned receipt in the local ledger. Mirrors the Android `ReceiptEntity`.
///
/// Money is stored as integer minor units (cents) to avoid floating-point
/// rounding, exactly as on Android.
struct Receipt: Identifiable, Equatable {
    var id: Int64
    var merchant: String
    /// Purchase date as days since 1970-01-01, or nil if unknown.
    var purchaseEpochDay: Int64?
    /// Total in minor currency units (cents).
    var totalMinor: Int64
    var currency: String
    var imagePath: String?
    var rawText: String
    var createdAt: Int64

    var purchaseDate: Date? {
        guard let day = purchaseEpochDay else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(day) * 86_400)
    }
}

/// Structured fields extracted from raw OCR text; mirrors Android `ParsedReceipt`.
struct ParsedReceipt: Equatable {
    var merchant: String?
    var date: Date?
    var totalMinor: Int64?
    var currency: String?
}
