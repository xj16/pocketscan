//
//  ReceiptParser.swift
//  PocketScan (iOS parity target)
//
//  A Swift port of the Android `ReceiptParser`. Same heuristics, same money
//  conventions, so both platforms behave identically. Pure Foundation — no
//  network, no ML — runnable offline and unit-testable.
//

import Foundation

enum ReceiptParser {

    private static let currencyTokens: [(pattern: String, code: String)] = [
        ("₺|\\bTL\\b|\\bTRY\\b", "TRY"),
        ("€|\\bEUR\\b", "EUR"),
        ("£|\\bGBP\\b", "GBP"),
        ("\\$|\\bUSD\\b", "USD"),
    ]

    private static let totalKeywords = [
        "grand total", "total due", "amount due", "balance due",
        "total", "toplam", "genel toplam", "tutar",
    ]

    private static let negativeKeywords = [
        "subtotal", "sub total", "ara toplam", "tax", "kdv", "vat",
        "change", "cash", "tip", "discount", "indirim",
    ]

    private static let amountPattern =
        "(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})|\\d+[.,]\\d{2})"

    static func parse(_ raw: String) -> ParsedReceipt {
        let lines = raw
            .split(separator: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        return ParsedReceipt(
            merchant: guessMerchant(lines),
            date: guessDate(lines),
            totalMinor: guessTotalMinor(lines),
            currency: guessCurrency(raw)
        )
    }

    // MARK: - Merchant

    static func guessMerchant(_ lines: [String]) -> String? {
        for line in lines.prefix(6) {
            let letters = line.filter { $0.isLetter }.count
            let digits = line.filter { $0.isNumber }.count
            if letters < 3 { continue }
            if digits > letters { continue }
            if matches(amountPattern, in: line) { continue }
            if line.lowercased().contains("receipt") { continue }
            return line.trimmingCharacters(in: CharacterSet(charactersIn: "*-= "))
        }
        return lines.first
    }

    // MARK: - Date

    static func guessDate(_ lines: [String]) -> Date? {
        let text = lines.joined(separator: "\n")
        let patterns: [(regex: String, format: String)] = [
            ("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b", "yyyy-M-d"),
            ("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})\\b", "d-M-yyyy"),
            ("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2})\\b", "d-M-yy"),
        ]
        for (regex, format) in patterns {
            guard let match = firstMatch(regex, in: text) else { continue }
            let normalized = match.replacingOccurrences(
                of: "[/.]", with: "-", options: .regularExpression)
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = TimeZone(identifier: "UTC")
            formatter.dateFormat = format
            if let date = formatter.date(from: normalized) { return date }
        }
        return nil
    }

    // MARK: - Total

    static func guessTotalMinor(_ lines: [String]) -> Int64? {
        let keywordCandidates: [Int64] = lines.compactMap { line in
            let lower = line.lowercased()
            if negativeKeywords.contains(where: { lower.contains($0) }) { return nil }
            if !totalKeywords.contains(where: { lower.contains($0) }) { return nil }
            return allAmounts(in: line).last
        }
        if let best = keywordCandidates.max() { return best }

        let allAmountsFlat = lines.flatMap { allAmounts(in: $0) }
        return allAmountsFlat.max()
    }

    private static func allAmounts(in line: String) -> [Int64] {
        allMatches(amountPattern, in: line).compactMap { toMinor($0) }
    }

    /// Parses a money token into integer cents, treating the *last* separator
    /// as the decimal point (handles both `1,234.56` and `1.234,56`).
    static func toMinor(_ token: String) -> Int64? {
        let t = token.trimmingCharacters(in: .whitespaces)
        guard let lastSep = t.lastIndex(where: { $0 == "." || $0 == "," }) else {
            return Int64(t).map { $0 * 100 }
        }
        let intPart = String(t[t.startIndex..<lastSep])
            .replacingOccurrences(of: "[.,]", with: "", options: .regularExpression)
        let fracPart = String(t[t.index(after: lastSep)...])
        if fracPart.count != 2 {
            let digits = t.replacingOccurrences(
                of: "[.,]", with: "", options: .regularExpression)
            return Int64(digits).map { $0 * 100 }
        }
        let intVal = Int64(intPart.isEmpty ? "0" : intPart)
        guard let intValue = intVal, let fracValue = Int64(fracPart) else { return nil }
        return intValue * 100 + fracValue
    }

    // MARK: - Currency

    static func guessCurrency(_ raw: String) -> String? {
        for (pattern, code) in currencyTokens where matches(pattern, in: raw) {
            return code
        }
        return nil
    }

    // MARK: - Formatting

    static func formatMoney(_ minor: Int64, _ currency: String) -> String {
        let symbol: String
        switch currency.uppercased() {
        case "USD": symbol = "$"
        case "EUR": symbol = "€"
        case "GBP": symbol = "£"
        case "TRY": symbol = "₺"
        default: symbol = "\(currency) "
        }
        let major = minor / 100
        let cents = String(format: "%02d", abs(minor % 100))
        return "\(symbol)\(major).\(cents)"
    }

    static func majorToMinor(_ major: Double) -> Int64 {
        Int64((major * 100).rounded())
    }

    // MARK: - Regex helpers

    private static func matches(_ pattern: String, in text: String) -> Bool {
        firstMatch(pattern, in: text) != nil
    }

    private static func firstMatch(_ pattern: String, in text: String) -> String? {
        allMatches(pattern, in: text).first
    }

    private static func allMatches(_ pattern: String, in text: String) -> [String] {
        guard let regex = try? NSRegularExpression(
            pattern: pattern, options: [.caseInsensitive]) else { return [] }
        let range = NSRange(text.startIndex..., in: text)
        return regex.matches(in: text, range: range).compactMap { match in
            guard let r = Range(match.range, in: text) else { return nil }
            return String(text[r])
        }
    }
}
