// Generates legacy launcher PNGs (for API 24–25, which ignore adaptive icons)
// with zero third-party deps: a hand-rolled PNG encoder writing a solid brand
// tile with a white receipt glyph. Run: `node scripts/gen_icons.mjs`.
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";

const BRAND = [0x2e, 0x6c, 0x5b]; // #2E6C5B
const WHITE = [0xff, 0xff, 0xff];

function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const typeBuf = Buffer.from(type, "ascii");
  const body = Buffer.concat([typeBuf, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

// Draw a receipt glyph: returns [r,g,b] for a pixel in a size×size icon.
function pixel(x, y, size) {
  const s = size / 108; // scale from design grid
  const gx = x / s;
  const gy = y / s;
  // Receipt body 38..70 wide, 30..75 tall
  if (gx >= 38 && gx <= 70 && gy >= 30 && gy <= 72) {
    // text bands
    const bands = [
      [40, 43],
      [48, 50],
      [55, 57],
      [63, 66],
    ];
    for (const [a, b] of bands) {
      if (gy >= a && gy <= b && gx >= 43 && gx <= 65) return BRAND;
    }
    return WHITE;
  }
  return BRAND;
}

function makePng(size) {
  const bpp = 3;
  const stride = size * bpp;
  const raw = Buffer.alloc((stride + 1) * size);
  for (let y = 0; y < size; y++) {
    raw[y * (stride + 1)] = 0; // filter: none
    for (let x = 0; x < size; x++) {
      const [r, g, b] = pixel(x, y, size);
      const o = y * (stride + 1) + 1 + x * bpp;
      raw[o] = r;
      raw[o + 1] = g;
      raw[o + 2] = b;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // color type: truecolor
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([
    sig,
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const densities = [
  ["mdpi", 48],
  ["hdpi", 72],
  ["xhdpi", 96],
  ["xxhdpi", 144],
  ["xxxhdpi", 192],
];
const base = new URL("../app/src/main/res/", import.meta.url).pathname.replace(
  /^\/([A-Za-z]:)/,
  "$1",
);

for (const [d, size] of densities) {
  const png = makePng(size);
  for (const name of ["ic_launcher.png", "ic_launcher_round.png"]) {
    const path = `${base}mipmap-${d}/${name}`;
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, png);
    console.log(`wrote ${path} (${png.length} bytes)`);
  }
}
