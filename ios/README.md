# PocketScan — iOS parity target

This is the **secondary** PocketScan client. The Android app (in the repo root)
is the complete flagship; this iOS target ships **feature parity for the ledger
+ OCR pipeline** so the project is genuinely cross-platform, plus an explicit
Objective-C interop shim.

## What's here

| Layer            | Android (flagship)                    | iOS (this target)                          |
|------------------|---------------------------------------|--------------------------------------------|
| UI               | Jetpack Compose                       | SwiftUI (`LedgerView`, `ReviewView`)       |
| On-device OCR    | ML Kit (bundled model)                | Apple Vision (`VNRecognizeTextRequest`)    |
| Receipt parsing  | `ReceiptParser.kt`                    | `ReceiptParser.swift` (same heuristics)    |
| Local ledger     | Room / SQLite                         | `libsqlite3` via `SQLite3` (`LedgerStore`) |
| Obj-C interop    | —                                     | `PSImageShim` (Core Graphics preprocessing)|

## About the Objective-C shim

`PocketScan/Interop/PSImageShim.{h,m}` is **explicitly a thin shim**. It exists
to demonstrate real Swift ↔ Objective-C interop through a bridging header: a
small Core Graphics grayscale/contrast pass applied to a frame before it is
handed to the Vision OCR request. The heavy computer-vision work (OpenCV edge
detection + perspective correction) lives in the Android flagship — the iOS
target intentionally keeps preprocessing minimal.

## Scope note

The iOS edge-detection/perspective step is not reimplemented here; iOS imports a
photo, runs the Obj-C preprocessing shim + Vision OCR, parses fields with the
shared Swift parser, and writes to the SQLite ledger. Camera capture uses the
system `PhotosPicker`.

## Building

The Xcode project is generated from `ios/project.yml` with
[XcodeGen](https://github.com/yonaskolb/XcodeGen) so we don't commit a
merge-hostile `.xcodeproj`:

```bash
cd ios
brew install xcodegen        # once
xcodegen generate            # produces PocketScan.xcodeproj
open PocketScan.xcodeproj     # build & run in Xcode, or:
xcodebuild -scheme PocketScan \
  -destination 'platform=iOS Simulator,name=iPhone 15' build
```

CI (`.github/workflows/ci.yml`) does exactly this on a macOS runner and also
runs the `PocketScanTests` XCTest bundle.
