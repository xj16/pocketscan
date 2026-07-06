//
//  PocketScanApp.swift
//  PocketScan (iOS parity target)
//

import SwiftUI

@main
struct PocketScanApp: App {

    @StateObject private var viewModel: LedgerViewModel

    init() {
        // Local SQLite ledger in the app's Documents directory.
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let path = dir.appendingPathComponent("pocketscan.sqlite").path
        _viewModel = StateObject(wrappedValue: LedgerViewModel(store: LedgerStore(path: path)))
    }

    var body: some Scene {
        WindowGroup {
            LedgerView(viewModel: viewModel)
        }
    }
}
