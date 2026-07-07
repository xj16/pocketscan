// Validates the Kotlin golden corpus (app/src/test/resources/golden/corpus.tsv)
// against the JS parser (which is parity-tested with the Kotlin one). Run
// locally so the committed expectations are known-correct before CI compiles
// the Kotlin test.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const P = require("../js/receipt-parser.js");

const here = dirname(fileURLToPath(import.meta.url));
const path = join(here, "..", "..", "app", "src", "test", "resources", "golden", "corpus.tsv");
const lines = readFileSync(path, "utf8").split("\n").filter((l) => l && !l.startsWith("#"));

let total = 0, merchantOk = 0, dateOk = 0, totalOk = 0, currencyOk = 0, fail = 0;
for (const line of lines) {
  const [id, eMerchant, eDate, eTotal, eCurrency, raw] = line.split("\t");
  const text = raw.replace(/\\n/g, "\n");
  const r = P.parse(text);
  total++;

  const mOk = r.merchant === eMerchant;
  const dOk = (eDate === "-" ? r.date == null : r.date === eDate);
  const tOk = (eTotal === "-" ? r.totalMinor == null : String(r.totalMinor) === eTotal);
  const cOk = (eCurrency === "-" ? r.currency == null : r.currency === eCurrency);
  if (mOk) merchantOk++;
  if (dOk) dateOk++;
  if (tOk) totalOk++;
  if (cOk) currencyOk++;

  if (!(mOk && dOk && tOk && cOk)) {
    fail++;
    console.log(`MISMATCH ${id}`);
    if (!mOk) console.log(`  merchant: got ${JSON.stringify(r.merchant)} want ${JSON.stringify(eMerchant)}`);
    if (!dOk) console.log(`  date:     got ${JSON.stringify(r.date)} want ${JSON.stringify(eDate)}`);
    if (!tOk) console.log(`  total:    got ${JSON.stringify(r.totalMinor)} want ${JSON.stringify(eTotal)}`);
    if (!cOk) console.log(`  currency: got ${JSON.stringify(r.currency)} want ${JSON.stringify(eCurrency)}`);
  }
}

console.log(`\nGolden corpus: ${total} cases`);
console.log(`  merchant ${merchantOk}/${total}  date ${dateOk}/${total}  total ${totalOk}/${total}  currency ${currencyOk}/${total}`);
process.exit(fail ? 1 : 0);
