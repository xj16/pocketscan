/*
 * receipt-parser.js
 * -----------------
 * A dependency-free JavaScript port of PocketScan's pure-Kotlin
 * `dev.xj16.pocketscan.ocr.ReceiptParser`. It mirrors the Android and iOS
 * implementations line-for-line so the in-browser playground behaves exactly
 * like the on-device parser — no network, no ML, just the same robust regexes
 * and line-ranking heuristics.
 *
 * The parity of this file with the Kotlin/Swift sources is locked in by
 * `demo/test/parser.test.mjs`, which re-runs the JVM/XCTest fixtures here.
 *
 * Works in both the browser (attached to `window.PocketScan`) and Node
 * (CommonJS + ESM interop via the footer).
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api; // Node / CommonJS
  }
  if (root) root.PocketScan = api; // Browser / worker global
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  // --- currency ------------------------------------------------------------
  // Symbols/codes we recognize, mapped to ISO-4217. Order matters: TRY before
  // USD so "₺" wins even when a receipt also has stray "$" noise.
  const CURRENCY_TOKENS = [
    { re: /₺|\bTL\b|\bTRY\b/i, code: "TRY" },
    { re: /€|\bEUR\b/i, code: "EUR" },
    { re: /£|\bGBP\b/i, code: "GBP" },
    { re: /\$|\bUSD\b/i, code: "USD" },
  ];

  // Lines containing one of these are strong "grand total" signals.
  const TOTAL_KEYWORDS = [
    "grand total", "total due", "amount due", "balance due",
    "total", "toplam", "genel toplam", "tutar",
  ];

  // Lines we must NOT treat as the grand total.
  const NEGATIVE_KEYWORDS = [
    "subtotal", "sub total", "ara toplam", "tax", "kdv", "vat",
    "change", "cash", "tip", "discount", "indirim",
  ];

  // A monetary amount: optional thousands separators + a 2-digit fraction.
  // (Recreated per call because JS regexes with /g are stateful.)
  function amountRe() {
    return /(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})|\d+[.,]\d{2})/g;
  }

  const ISO_DATE = /\b(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\b/;
  const DMY_OR_MDY = /\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b/;

  function splitLines(raw) {
    return raw
      .split("\n")
      .map((l) => l.trim())
      .filter((l) => l.length > 0);
  }

  function countLetters(s) {
    let n = 0;
    for (const ch of s) if (/\p{L}/u.test(ch)) n++;
    return n;
  }
  function countDigits(s) {
    let n = 0;
    for (const ch of s) if (ch >= "0" && ch <= "9") n++;
    return n;
  }

  // --- merchant ------------------------------------------------------------
  function guessMerchant(lines) {
    for (const line of lines.slice(0, 6)) {
      const letters = countLetters(line);
      const digits = countDigits(line);
      if (letters < 3) continue;
      if (digits > letters) continue; // phone numbers, dates, receipt ids
      if (amountRe().test(line)) continue;
      if (/receipt/i.test(line)) continue;
      return line.trim().replace(/^[*\-= ]+|[*\-= ]+$/g, "");
    }
    return lines.length ? lines[0] : null;
  }

  // --- date ----------------------------------------------------------------
  function normalizeYear(y) {
    return y < 100 ? 2000 + y : y;
  }
  function buildDate(year, month, day) {
    if (month < 1 || month > 12 || day < 1 || day > 31) return null;
    // Reject impossible calendar dates (e.g. Feb 30) the way LocalDate does.
    const d = new Date(Date.UTC(year, month - 1, day));
    if (
      d.getUTCFullYear() !== year ||
      d.getUTCMonth() !== month - 1 ||
      d.getUTCDate() !== day
    ) {
      return null;
    }
    const iso = `${String(year).padStart(4, "0")}-${String(month).padStart(
      2,
      "0"
    )}-${String(day).padStart(2, "0")}`;
    return iso; // ISO yyyy-MM-dd string, matching Kotlin LocalDate.toString()
  }

  function guessDate(lines) {
    const text = lines.join("\n");

    const iso = ISO_DATE.exec(text);
    if (iso) {
      const built = buildDate(+iso[1], +iso[2], +iso[3]);
      if (built) return built;
    }

    const tri = DMY_OR_MDY.exec(text);
    if (tri) {
      const year = normalizeYear(+tri[3]);
      const x = +tri[1];
      const y = +tri[2];
      // Prefer DMY (a=day, b=month); fall back to MDY when the first field
      // can't be a day/month. Mirrors the Kotlin disambiguation exactly.
      if (y <= 12) {
        return buildDate(year, y, x) || buildDate(year, x, y);
      }
      return buildDate(year, x, y) || buildDate(year, y, x);
    }
    return null;
  }

  // --- total ---------------------------------------------------------------
  function allAmountsOnLine(line) {
    const out = [];
    const re = amountRe();
    let m;
    while ((m = re.exec(line)) !== null) {
      const v = toMinor(m[1]);
      if (v !== null) out.push(v);
    }
    return out;
  }

  function guessTotalMinor(lines) {
    const keywordCandidates = [];
    for (const line of lines) {
      const lower = line.toLowerCase();
      if (NEGATIVE_KEYWORDS.some((k) => lower.includes(k))) continue;
      if (!TOTAL_KEYWORDS.some((k) => lower.includes(k))) continue;
      const amts = allAmountsOnLine(line);
      if (amts.length) keywordCandidates.push(amts[amts.length - 1]);
    }
    if (keywordCandidates.length) return Math.max(...keywordCandidates);

    const all = lines.flatMap(allAmountsOnLine);
    return all.length ? Math.max(...all) : null;
  }

  /**
   * Parses a monetary token into integer cents, treating the *last* separator
   * as the decimal point — this is what makes both `1,234.56` (US) and
   * `1.234,56` (EU/TR) parse to 123456.
   */
  function toMinor(token) {
    const t = token.trim();
    let lastSep = -1;
    for (let i = t.length - 1; i >= 0; i--) {
      if (t[i] === "." || t[i] === ",") {
        lastSep = i;
        break;
      }
    }
    if (lastSep === -1) {
      const whole = parseIntStrict(t);
      return whole === null ? null : whole * 100;
    }
    const intPart = t.slice(0, lastSep).replace(/[.,]/g, "");
    const fracPart = t.slice(lastSep + 1);
    if (fracPart.length !== 2) {
      const whole = parseIntStrict(t.replace(/[.,]/g, ""));
      return whole === null ? null : whole * 100;
    }
    const intVal = parseIntStrict(intPart === "" ? "0" : intPart);
    const fracVal = parseIntStrict(fracPart);
    if (intVal === null || fracVal === null) return null;
    return intVal * 100 + fracVal;
  }

  // Strict integer parse: only digits allowed (JS parseInt is too lenient).
  function parseIntStrict(s) {
    if (!/^\d+$/.test(s)) return null;
    return Number(s);
  }

  // --- currency ------------------------------------------------------------
  function guessCurrency(raw) {
    for (const { re, code } of CURRENCY_TOKENS) {
      if (re.test(raw)) return code;
    }
    return null;
  }

  // --- formatting ----------------------------------------------------------
  function formatMoney(minor, currency) {
    const symbols = { USD: "$", EUR: "€", GBP: "£", TRY: "₺" };
    const symbol = symbols[(currency || "").toUpperCase()] || `${currency} `;
    const major = Math.trunc(minor / 100);
    const cents = String(Math.abs(minor % 100)).padStart(2, "0");
    return `${symbol}${major}.${cents}`;
  }

  function majorToMinor(major) {
    return Math.round(major * 100);
  }

  // --- public parse --------------------------------------------------------
  function parse(raw) {
    const lines = splitLines(raw);
    return {
      merchant: guessMerchant(lines),
      date: guessDate(lines),
      totalMinor: guessTotalMinor(lines),
      currency: guessCurrency(raw),
    };
  }

  return {
    parse,
    toMinor,
    guessMerchant,
    guessDate,
    guessTotalMinor,
    guessCurrency,
    formatMoney,
    majorToMinor,
    splitLines,
    // Constants exposed for the playground's live highlighting.
    TOTAL_KEYWORDS,
    NEGATIVE_KEYWORDS,
    CURRENCY_TOKENS,
  };
});
