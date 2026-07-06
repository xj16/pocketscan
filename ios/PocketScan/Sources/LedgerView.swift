//
//  LedgerView.swift
//  PocketScan (iOS parity target)
//
//  SwiftUI parity UI: a receipt list with a running total, a PhotosPicker
//  import that runs the offline Vision-OCR pipeline, and a review sheet.
//

import SwiftUI
import PhotosUI

struct LedgerView: View {
    @ObservedObject var viewModel: LedgerViewModel
    @State private var pickerItem: PhotosPickerItem?
    @State private var showingReview = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("Tracked total (USD)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(ReceiptParser.formatMoney(viewModel.totalMinorUSD, "USD"))
                                .font(.title.bold())
                        }
                        Spacer()
                    }
                }
                Section("Receipts") {
                    if viewModel.receipts.isEmpty {
                        Text("No receipts yet. Import one to get started.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(viewModel.receipts) { receipt in
                        ReceiptRow(receipt: receipt)
                    }
                    .onDelete { indexSet in
                        indexSet.map { viewModel.receipts[$0] }.forEach(viewModel.delete)
                    }
                }
            }
            .navigationTitle("PocketScan")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Label("Scan", systemImage: "camera.viewfinder")
                    }
                }
            }
            .overlay {
                if viewModel.isProcessing {
                    ProgressView("Reading text…")
                        .padding()
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                }
            }
            // Single-parameter onChange keeps us compatible with iOS 16.
            .onChange(of: pickerItem) { newItem in
                guard let newItem else { return }
                Task {
                    if let data = try? await newItem.loadTransferable(type: Data.self),
                       let image = UIImage(data: data) {
                        await viewModel.scan(image: image)
                        showingReview = viewModel.pendingParse != nil
                    }
                }
            }
            .sheet(isPresented: $showingReview) {
                if let parsed = viewModel.pendingParse {
                    ReviewView(viewModel: viewModel, parsed: parsed)
                }
            }
        }
    }
}

private struct ReceiptRow: View {
    let receipt: Receipt

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(receipt.merchant).font(.headline)
                if let date = receipt.purchaseDate {
                    Text(date, style: .date)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text(ReceiptParser.formatMoney(receipt.totalMinor, receipt.currency))
                .font(.headline)
        }
    }
}
