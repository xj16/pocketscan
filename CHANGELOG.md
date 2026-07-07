# Changelog

All notable changes to PocketScan are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims
to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] — 2026-07-07

The "make the pitch true" release: the advertised live edge detection now
actually renders, the ledger is genuinely searchable, mixed-currency totals are
honest, and the smartest part of the code — the receipt parser — is playable in
a browser.

### Added
- **Live edge-detection overlay.** The scanner now runs an `ImageAnalysis`
  pipeline (~6 fps, off the main thread) that feeds frames to the existing
  `DocumentScanner.detectQuad()` and draws the detected document as an animated
  highlighted quadrilateral over the camera preview, with **auto-capture** once
  the document is held steady. Previously `detectQuad()` shipped but was never
  wired to the UI.
- **Searchable, filterable ledger.** A search bar queries both the merchant and
  the stored raw OCR text (Room `LIKE`), plus category filter chips — finally
  making the "searchable local ledger" claim real. Search is debounced.
- **Spending-by-category chart.** A dependency-free Compose `Canvas` donut with
  an animated sweep and an amount/percentage legend, computed from the dominant
  currency's category totals.
- **Receipt detail screen.** Tapping a ledger row (a nav route that previously
  went nowhere) now opens the perspective-corrected scan, the parsed fields, the
  raw OCR text, and **share** + **delete** actions.
- **CSV export.** Export the whole ledger to an RFC-4180-style CSV via the system
  share sheet, backed by a `FileProvider`. Serialization lives in a pure,
  unit-tested `LedgerCsv`.
- **In-browser parser playground** (`demo/`). A static, zero-dependency page that
  runs a faithful JavaScript port of `ReceiptParser` — the same heuristics that
  ship on Android and iOS — so anyone can paste receipt text and watch the fields
  light up live. Deployed to GitHub Pages via CI.
- **Flagship tests.**
  - Robolectric round-trip over the real Room `ReceiptDao` (insert / query /
    search / per-currency + per-category aggregates / delete), mirroring the iOS
    `LedgerStoreTests`.
  - `HomeViewModel` tests over the currency-grouping, search, filter and chart
    pipeline (Turbine + coroutine test dispatcher).
  - A **golden-corpus benchmark** (14 anonymized US/EU/TR fixtures) that scores
    per-field parser accuracy and prints a report in CI.
  - `LedgerCsv` serialization tests, and a Node parity suite that re-runs the
    JVM/XCTest fixtures against the JS port.
- **JaCoCo coverage** report over the JVM unit tests, uploaded as a CI artifact,
  with a coverage/CI badge row in the README.

### Fixed
- **Mixed-currency totals.** The home header summed every receipt as USD
  regardless of its stored currency, producing a meaningless number for a ledger
  mixing USD/TRY/EUR. Totals are now grouped **per currency**
  (e.g. `$142.10 · ₺980.00`), and the category chart is scoped to a single
  (dominant) currency and labeled as such.

### Changed
- The home list is now a single `LazyColumn` hosting the summary card, the
  search/filter controls, and the receipt rows, with an explicit
  "no receipts match your search" state distinct from the first-run empty state.
- CI gained a Node job that enforces JS-vs-Kotlin parser parity, and a Pages
  workflow that publishes the playground.

## [1.0.0] — 2026

Initial public release.

- Offline document scanner: OpenCV edge detection + perspective correction +
  adaptive threshold.
- On-device OCR (ML Kit) and heuristic receipt parsing (merchant / date / total
  / currency) for US & EU/TR money and date conventions.
- On-device spending categorization (TensorFlow Lite with a transparent keyword
  fallback).
- Local Room/SQLite ledger with a live running total; **no `INTERNET`
  permission** — receipts never leave the device.
- iOS parity target (SwiftUI + Vision + `libsqlite3`) with an Objective-C interop
  shim, and Android + iOS CI.

[1.1.0]: https://github.com/xj16/pocketscan/releases/tag/v1.1.0
[1.0.0]: https://github.com/xj16/pocketscan/releases/tag/v1.0.0
