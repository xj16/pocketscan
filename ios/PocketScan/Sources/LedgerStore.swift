//
//  LedgerStore.swift
//  PocketScan (iOS parity target)
//
//  Local SQLite ledger built directly on the system `libsqlite3` (imported via
//  `SQLite3`), so there are zero third-party dependencies. Mirrors the Android
//  Room schema: one `receipts` table keyed by an autoincrement id.
//

import Foundation
import SQLite3

/// Thread-safe-ish minimal SQLite wrapper. For an app of this size a single
/// serial queue guarding one connection is plenty.
final class LedgerStore {

    private var db: OpaquePointer?
    private let queue = DispatchQueue(label: "dev.xj16.pocketscan.ledger")

    /// SQLITE_TRANSIENT tells SQLite to copy bound strings (they may be freed
    /// before the statement runs). The constant isn't imported into Swift.
    private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    init(path: String) {
        queue.sync {
            if sqlite3_open(path, &db) == SQLITE_OK {
                createSchema()
            } else {
                assertionFailure("Unable to open SQLite ledger at \(path)")
            }
        }
    }

    deinit {
        sqlite3_close(db)
    }

    private func createSchema() {
        let sql = """
        CREATE TABLE IF NOT EXISTS receipts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            merchant TEXT NOT NULL,
            purchaseEpochDay INTEGER,
            totalMinor INTEGER NOT NULL,
            currency TEXT NOT NULL,
            imagePath TEXT,
            rawText TEXT NOT NULL,
            createdAt INTEGER NOT NULL
        );
        """
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    /// Inserts a receipt and returns its new row id.
    @discardableResult
    func insert(_ receipt: Receipt) -> Int64 {
        queue.sync {
            let sql = """
            INSERT INTO receipts
                (merchant, purchaseEpochDay, totalMinor, currency, imagePath, rawText, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?);
            """
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return -1 }
            defer { sqlite3_finalize(stmt) }

            sqlite3_bind_text(stmt, 1, receipt.merchant, -1, SQLITE_TRANSIENT)
            bindOptionalInt(stmt, 2, receipt.purchaseEpochDay)
            sqlite3_bind_int64(stmt, 3, receipt.totalMinor)
            sqlite3_bind_text(stmt, 4, receipt.currency, -1, SQLITE_TRANSIENT)
            bindOptionalText(stmt, 5, receipt.imagePath)
            sqlite3_bind_text(stmt, 6, receipt.rawText, -1, SQLITE_TRANSIENT)
            sqlite3_bind_int64(stmt, 7, receipt.createdAt)

            guard sqlite3_step(stmt) == SQLITE_DONE else { return -1 }
            return sqlite3_last_insert_rowid(db)
        }
    }

    /// Returns all receipts, newest first.
    func all() -> [Receipt] {
        queue.sync {
            let sql = "SELECT id, merchant, purchaseEpochDay, totalMinor, currency, " +
                      "imagePath, rawText, createdAt FROM receipts ORDER BY createdAt DESC;"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }
            defer { sqlite3_finalize(stmt) }

            var results: [Receipt] = []
            while sqlite3_step(stmt) == SQLITE_ROW {
                results.append(readRow(stmt))
            }
            return results
        }
    }

    /// Sum of all totals in a given currency, in minor units.
    func totalMinor(currency: String) -> Int64 {
        queue.sync {
            let sql = "SELECT COALESCE(SUM(totalMinor), 0) FROM receipts WHERE currency = ?;"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return 0 }
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, currency, -1, SQLITE_TRANSIENT)
            guard sqlite3_step(stmt) == SQLITE_ROW else { return 0 }
            return sqlite3_column_int64(stmt, 0)
        }
    }

    func delete(id: Int64) {
        queue.sync {
            let sql = "DELETE FROM receipts WHERE id = ?;"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_int64(stmt, 1, id)
            sqlite3_step(stmt)
        }
    }

    // MARK: - Row / binding helpers

    private func readRow(_ stmt: OpaquePointer?) -> Receipt {
        Receipt(
            id: sqlite3_column_int64(stmt, 0),
            merchant: columnText(stmt, 1) ?? "",
            purchaseEpochDay: columnOptionalInt(stmt, 2),
            totalMinor: sqlite3_column_int64(stmt, 3),
            currency: columnText(stmt, 4) ?? "USD",
            imagePath: columnText(stmt, 5),
            rawText: columnText(stmt, 6) ?? "",
            createdAt: sqlite3_column_int64(stmt, 7)
        )
    }

    private func columnText(_ stmt: OpaquePointer?, _ index: Int32) -> String? {
        guard let cString = sqlite3_column_text(stmt, index) else { return nil }
        return String(cString: cString)
    }

    private func columnOptionalInt(_ stmt: OpaquePointer?, _ index: Int32) -> Int64? {
        if sqlite3_column_type(stmt, index) == SQLITE_NULL { return nil }
        return sqlite3_column_int64(stmt, index)
    }

    private func bindOptionalInt(_ stmt: OpaquePointer?, _ index: Int32, _ value: Int64?) {
        if let value = value {
            sqlite3_bind_int64(stmt, index, value)
        } else {
            sqlite3_bind_null(stmt, index)
        }
    }

    private func bindOptionalText(_ stmt: OpaquePointer?, _ index: Int32, _ value: String?) {
        if let value = value {
            sqlite3_bind_text(stmt, index, value, -1, SQLITE_TRANSIENT)
        } else {
            sqlite3_bind_null(stmt, index)
        }
    }
}
