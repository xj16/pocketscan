//
//  ReviewView.swift
//  PocketScan (iOS parity target)
//
//  Post-scan review sheet mirroring the Android review screen: editable
//  merchant / date / total / currency fields, then save to the SQLite ledger.
//

import SwiftUI

struct ReviewView: View {
    @ObservedObject var viewModel: LedgerViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var merchant: String
    @State private var totalText: String
    @State private var currency: String
    @State private var date: Date

    init(viewModel: LedgerViewModel, parsed: ParsedReceipt) {
        self.viewModel = viewModel
        _merchant = State(initialValue: parsed.merchant ?? "")
        if let minor = parsed.totalMinor {
            _totalText = State(initialValue: String(format: "%.2f", Double(minor) / 100.0))
        } else {
            _totalText = State(initialValue: "")
        }
        _currency = State(initialValue: parsed.currency ?? "USD")
        _date = State(initialValue: parsed.date ?? Date())
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Details") {
                    TextField("Merchant", text: $merchant)
                    DatePicker("Date", selection: $date, displayedComponents: .date)
                    TextField("Total", text: $totalText)
                        .keyboardType(.decimalPad)
                    TextField("Currency", text: $currency)
                        .textInputAutocapitalization(.characters)
                }
            }
            .navigationTitle("Review receipt")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { viewModel.pendingParse = nil; dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let total = Double(totalText.replacingOccurrences(of: ",", with: ".")) ?? 0
                        viewModel.save(
                            merchant: merchant,
                            totalMajor: total,
                            currency: currency,
                            date: date,
                            rawText: ""
                        )
                        dismiss()
                    }
                }
            }
        }
    }
}
