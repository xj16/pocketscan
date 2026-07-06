//
//  TextRecognizer.swift
//  PocketScan (iOS parity target)
//
//  On-device OCR via Apple's Vision framework — the iOS analogue of the
//  Android ML Kit path. Runs entirely offline. Before recognition it calls the
//  Objective-C `PSImageShim` to grayscale/contrast-boost the frame, exercising
//  the Swift <-> Obj-C interop bridge.
//

import Foundation
import Vision

#if canImport(UIKit)
import UIKit
#endif

enum OCRError: Error {
    case noImage
    case recognitionFailed(Error)
}

struct TextRecognizer {

    /// Recognizes text in a CGImage, returning the joined recognized lines.
    func recognize(cgImage: CGImage) async throws -> String {
        // Preprocess through the Objective-C shim (interop demonstration).
        // The shim is CF_RETURNS_RETAINED + audited, so Swift imports the
        // result as a managed `CGImage?` — ARC owns it, no Unmanaged dance.
        let prepared = PSImageShim.preprocessedImage(fromCGImage: cgImage) ?? cgImage

        return try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error = error {
                    continuation.resume(throwing: OCRError.recognitionFailed(error))
                    return
                }
                let observations = request.results as? [VNRecognizedTextObservation] ?? []
                let lines = observations.compactMap {
                    $0.topCandidates(1).first?.string
                }
                continuation.resume(returning: lines.joined(separator: "\n"))
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true

            let handler = VNImageRequestHandler(cgImage: prepared, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(throwing: OCRError.recognitionFailed(error))
            }
        }
    }

    #if canImport(UIKit)
    func recognize(image: UIImage) async throws -> String {
        guard let cg = image.cgImage else { throw OCRError.noImage }
        return try await recognize(cgImage: cg)
    }
    #endif
}
