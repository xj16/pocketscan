/*
 * Parity + correctness tests for the JavaScript receipt parser.
 *
 * These re-run the exact fixtures from the Android `ReceiptParserTest` (JUnit)
 * and the iOS `ReceiptParserTests` (XCTest) against the browser/JS port, so the
 * playground provably behaves like the on-device parser. Pure Node, zero deps.
 *
 * Run with:  node --test demo/test/parser.test.mjs
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const P = require("../js/receipt-parser.js");

const sampleUs = [
  "WHOLE FOODS MARKET",
  "123 Main St, Austin TX",
  "Tel: (512) 555-0199",
  "",
  "Bananas            1.29",
  "Almond Milk        3.49",
  "Sourdough Loaf     4.99",
  "",
  "Subtotal           9.77",
  "Tax                0.81",
  "TOTAL             10.58",
  "",
  "VISA ************1234",
  "Date: 03/14/2026",
].join("\n");

test("extracts merchant from first meaningful line", () => {
  assert.equal(P.parse(sampleUs).merchant, "WHOLE FOODS MARKET");
});

test("picks the grand total over subtotal and tax", () => {
  assert.equal(P.parse(sampleUs).totalMinor, 1058);
});

test("parses US date format", () => {
  assert.equal(P.parse(sampleUs).date, "2026-03-14");
});

test("detects USD currency from dollar amounts", () => {
  assert.equal(P.parse("Coffee $4.50\nTOTAL $4.50").currency, "USD");
});

test("handles European decimal comma and TRY currency", () => {
  const turkish = [
    "BIM MARKET",
    "Ekmek            5,50",
    "Süt              32,90",
    "ARA TOPLAM       38,40",
    "KDV               3,49",
    "TOPLAM           41,89 TL",
    "Tarih: 14.03.2026",
  ].join("\n");
  const parsed = P.parse(turkish);
  assert.equal(parsed.merchant, "BIM MARKET");
  assert.equal(parsed.totalMinor, 4189);
  assert.equal(parsed.currency, "TRY");
  assert.equal(parsed.date, "2026-03-14");
});

test("thousands separator with US convention parses correctly", () => {
  assert.equal(P.toMinor("1,234.56"), 123456);
});

test("thousands separator with EU convention parses correctly", () => {
  assert.equal(P.toMinor("1.234,56"), 123456);
});

test("bare integer amount becomes minor units", () => {
  assert.equal(P.toMinor("42"), 4200);
});

test("thousands-only value without decimals is treated as whole", () => {
  assert.equal(P.toMinor("1.234"), 123400);
});

test("falls back to largest amount when no total keyword", () => {
  const noKeyword = [
    "CORNER STORE",
    "Item A    2.00",
    "Item B    9.95",
    "Item C    1.50",
  ].join("\n");
  assert.equal(P.parse(noKeyword).totalMinor, 995);
});

test("empty input yields nulls without throwing", () => {
  const parsed = P.parse("");
  assert.equal(parsed.totalMinor, null);
  assert.equal(parsed.date, null);
  assert.equal(parsed.currency, null);
});

test("formatMoney renders symbol and two decimals", () => {
  assert.equal(P.formatMoney(1058, "USD"), "$10.58");
  assert.equal(P.formatMoney(4189, "TRY"), "₺41.89");
});

test("majorToMinor rounds correctly", () => {
  assert.equal(P.majorToMinor(10.58), 1058);
  assert.equal(P.majorToMinor(10.0), 1000);
  assert.equal(P.majorToMinor(0.005), 1);
});

test("negative keywords never win as total", () => {
  const total = P.parse(sampleUs).totalMinor;
  assert.notEqual(total, 81);
  assert.notEqual(total, 977);
});

// --- extra JS-side coverage beyond the mirrored JVM suite -------------------

test("EU date with dotted DMY disambiguates day > 12", () => {
  // 25.12.2025 can only be DMY (25 is not a month).
  assert.equal(P.parse("Tarih: 25.12.2025").date, "2025-12-25");
});

test("ISO date wins over ambiguous slash date", () => {
  assert.equal(P.parse("2026-01-02\n03/04/2026").date, "2026-01-02");
});

test("rejects impossible calendar date (Feb 30) and returns null", () => {
  assert.equal(P.parse("Date: 30/02/2026").date, null);
});

test("grand total keyword outranks a larger non-total amount", () => {
  const r = [
    "STORE",
    "Big Item 999.99",
    "TOTAL 12.00",
  ].join("\n");
  assert.equal(P.parse(r).totalMinor, 1200);
});
