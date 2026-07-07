import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const P = require("../js/receipt-parser.js");

const SAMPLES = {
  us: ["WHOLE FOODS MARKET","123 Main St, Austin TX","Tel: (512) 555-0199","","Bananas           $1.29","Almond Milk       $3.49","Sourdough Loaf    $4.99","","Subtotal          $9.77","Tax               $0.81","TOTAL            $10.58","","VISA ************1234","Date: 03/14/2026"].join("\n"),
  tr: ["BIM MARKET","Kadikoy / Istanbul","","Ekmek            5,50","Sut              32,90","Yumurta 10lu     44,75","","ARA TOPLAM       83,15","KDV %8            6,65","TOPLAM           89,80 TL","","Tarih: 14.03.2026"].join("\n"),
  eu: ["CAFÉ DE FLORE","172 Bd Saint-Germain, Paris","","Espresso           2.50","Croissant          1.80","Jus d'orange       4.20","","Sous-total         8.50","TVA 10%            0.85","TOTAL              9.35 €","","Date: 14.03.2026"].join("\n"),
  messy: ["TARGET  T1932","  ***  ST0RE #0042  ***","8OO-591-3869","","Gr0cery      12,00","H0me         1.234,50","","SUBT0TAL    1.246,50","TAX            99,72","BALANCE DUE 1.346,22 USD","","01/31/26"].join("\n"),
};

const expect = {
  us:    { merchant: "WHOLE FOODS MARKET", date: "2026-03-14", currency: "USD", money: "$10.58" },
  tr:    { merchant: "BIM MARKET",         date: "2026-03-14", currency: "TRY", money: "₺89.80" },
  eu:    { merchant: "CAFÉ DE FLORE",      date: "2026-03-14", currency: "EUR", money: "€9.35" },
  messy: { merchant: "TARGET  T1932",      date: "2026-01-31", currency: "USD", money: "$1346.22" },
};

let fail = 0;
for (const [k, v] of Object.entries(SAMPLES)) {
  const r = P.parse(v);
  const money = r.totalMinor != null ? P.formatMoney(r.totalMinor, r.currency || "USD") : "—";
  const e = expect[k];
  const ok = r.merchant === e.merchant && r.date === e.date && r.currency === e.currency && money === e.money;
  console.log(`${ok ? "PASS" : "FAIL"} ${k.padEnd(6)} ${JSON.stringify(r)} -> ${money}`);
  if (!ok) { fail++; console.log(`     expected ${JSON.stringify(e)}`); }
}
process.exit(fail ? 1 : 0);
