package dev.xj16.pocketscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single scanned receipt as persisted in the local SQLite ledger.
 *
 * Money is stored as an integer number of minor units (cents) to avoid binary
 * floating-point rounding errors — the cardinal rule of storing currency.
 * [currency] is an ISO-4217 code (e.g. "USD", "TRY", "EUR").
 *
 * [imagePath] points at the perspective-corrected scan saved in app-internal
 * storage; nothing is ever uploaded anywhere.
 */
@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val merchant: String,

    /** Purchase date as epoch-day (days since 1970-01-01), or null if unknown. */
    val purchaseEpochDay: Long?,

    /** Total in minor currency units (cents). */
    val totalMinor: Long,

    val currency: String,

    /** On-device predicted spending category label (e.g. "Groceries"). */
    val category: String = "Other",

    /** Absolute path to the cropped scan image in internal storage. */
    val imagePath: String?,

    /** Full OCR output, kept so the user can re-parse or copy raw text. */
    val rawText: String,

    /** When this row was created (epoch millis). */
    val createdAt: Long = System.currentTimeMillis(),
)
