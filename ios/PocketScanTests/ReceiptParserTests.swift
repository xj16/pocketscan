//
//  ReceiptParserTests.swift
//  PocketScanTests (iOS parity target)
//
//  Mirrors the Android JVM parser tests so both platforms are held to the same
//  behavior. Pure Foundation — runs on any iOS simulator in CI.
//

import XCTest
@testable import PocketScan

final class ReceiptParserTests: XCTestCase {

    private let sampleUS = """
    WHOLE FOODS MARKET
    123 Main St, Austin TX
    Tel: (512) 555-0199

    Bananas            1.29
    Almond Milk        3.49
    Sourdough Loaf     4.99

    Subtotal           9.77
    Tax                0.81
    TOTAL             10.58

    VISA ************1234
    Date: 03/14/2026
    """

    func testExtractsMerchant() {
        XCTAssertEqual(ReceiptParser.parse(sampleUS).merchant, "WHOLE FOODS MARKET")
    }

    func testPicksGrandTotal() {
        XCTAssertEqual(ReceiptParser.parse(sampleUS).totalMinor, 1058)
    }

    func testDetectsUSD() {
        XCTAssertEqual(ReceiptParser.parse("Coffee $4.50\nTOTAL $4.50").currency, "USD")
    }

    func testEuropeanCommaAndTRY() {
        let turkish = """
        BIM MARKET
        Ekmek            5,50
        Süt              32,90
        ARA TOPLAM       38,40
        KDV               3,49
        TOPLAM           41,89 TL
        """
        let parsed = ReceiptParser.parse(turkish)
        XCTAssertEqual(parsed.totalMinor, 4189)
        XCTAssertEqual(parsed.currency, "TRY")
    }

    func testToMinorUSThousands() {
        XCTAssertEqual(ReceiptParser.toMinor("1,234.56"), 123456)
    }

    func testToMinorEUThousands() {
        XCTAssertEqual(ReceiptParser.toMinor("1.234,56"), 123456)
    }

    func testToMinorBareInteger() {
        XCTAssertEqual(ReceiptParser.toMinor("42"), 4200)
    }

    func testThousandsOnlyTreatedAsWhole() {
        XCTAssertEqual(ReceiptParser.toMinor("1.234"), 123400)
    }

    func testFallsBackToLargestAmount() {
        let noKeyword = """
        CORNER STORE
        Item A    2.00
        Item B    9.95
        Item C    1.50
        """
        XCTAssertEqual(ReceiptParser.parse(noKeyword).totalMinor, 995)
    }

    func testEmptyInput() {
        let parsed = ReceiptParser.parse("")
        XCTAssertNil(parsed.totalMinor)
        XCTAssertNil(parsed.currency)
    }

    func testFormatMoney() {
        XCTAssertEqual(ReceiptParser.formatMoney(1058, "USD"), "$10.58")
        XCTAssertEqual(ReceiptParser.formatMoney(4189, "TRY"), "₺41.89")
    }

    func testMajorToMinorRounding() {
        XCTAssertEqual(ReceiptParser.majorToMinor(10.58), 1058)
        XCTAssertEqual(ReceiptParser.majorToMinor(10.0), 1000)
    }
}
