/*
 * Whether a font file actually carries a given letter, read from the file rather than assumed.
 *
 * No font library: the format is a table directory of 16-byte records, and a cmap subtable in
 * format 4 (BMP) or 12 (full range) is enough to answer "is this codepoint in this file".
 * Shared by the tests that hold a face to the letters the site sets in it.
 */
import {readFileSync} from "node:fs"
import {fileURLToPath} from "node:url"

// Held in a variable, not written inline: Vite rewrites a literal `new URL("…", import.meta.url)`
// into an asset reference, and the font then resolves against the served root instead of the disk.
const FONT_DIR = "../../../src/assets/fonts/"

export function read(file: string): DataView {
  const bytes = readFileSync(fileURLToPath(new URL(FONT_DIR + file, import.meta.url)))
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
}

function tableOffset(font: DataView, tag: string): number {
  for (let i = 0; i < font.getUint16(4); i++) {
    const record = 12 + i * 16
    const name = Array.from({length: 4}, (_, byte) => String.fromCharCode(font.getUint8(record + byte))).join("")
    if (name === tag) return font.getUint32(record + 8)
  }
  throw new Error(`no ${tag} table`)
}

/** Segmented mapping, 16-bit. Codepoints above the BMP are out of its reach by definition. */
function glyphFromFormat4(font: DataView, sub: number, codepoint: number): number {
  if (codepoint > 0xffff) return 0
  const segments = font.getUint16(sub + 6) / 2
  const ends = sub + 14
  const starts = ends + segments * 2 + 2
  const deltas = starts + segments * 2
  const rangeOffsets = deltas + segments * 2

  for (let i = 0; i < segments; i++) {
    if (font.getUint16(ends + i * 2) < codepoint) continue
    const start = font.getUint16(starts + i * 2)
    if (start > codepoint) return 0

    const delta = font.getInt16(deltas + i * 2)
    const rangeOffset = font.getUint16(rangeOffsets + i * 2)
    if (rangeOffset === 0) return (codepoint + delta) & 0xffff

    // The offset is measured from its own slot in the array, not from the subtable.
    const glyph = font.getUint16(rangeOffsets + i * 2 + rangeOffset + (codepoint - start) * 2)
    return glyph === 0 ? 0 : (glyph + delta) & 0xffff
  }
  return 0
}

/** Segmented coverage, 32-bit, groups ascending. */
function glyphFromFormat12(font: DataView, sub: number, codepoint: number): number {
  const groups = font.getUint32(sub + 12)
  for (let i = 0; i < groups; i++) {
    const group = sub + 16 + i * 12
    const start = font.getUint32(group)
    if (codepoint < start) return 0
    if (codepoint > font.getUint32(group + 4)) continue
    return font.getUint32(group + 8) + (codepoint - start)
  }
  return 0
}

/** Every format 4 and 12 subtable in the file, so a missing letter is missing from all of them. */
function subtables(font: DataView, cmap: number): number[] {
  const found: number[] = []
  for (let i = 0; i < font.getUint16(cmap + 2); i++) {
    const sub = cmap + font.getUint32(cmap + 4 + i * 8 + 4)
    const format = font.getUint16(sub)
    if (format === 4 || format === 12) found.push(sub)
  }
  return found
}

export function coverageOf(file: string): (codepoint: number) => boolean {
  const font = read(file)
  const cmap = tableOffset(font, "cmap")
  const found = subtables(font, cmap)
  // Otherwise "no glyphs" would read as a missing letter rather than as a parser that
  // understood none of the file, and the whole test would pass by saying nothing.
  if (found.length === 0) throw new Error(`${file} has no format 4 or 12 cmap subtable`)

  return (codepoint) => found.some((sub) => {
    const glyph = font.getUint16(sub) === 4
      ? glyphFromFormat4(font, sub, codepoint)
      : glyphFromFormat12(font, sub, codepoint)
    return glyph !== 0
  })
}

export function missingFrom(file: string, letters: Record<string, number>): string[] {
  const covered = coverageOf(file)
  return Object.entries(letters).filter(([, codepoint]) => !covered(codepoint)).map(([letter]) => letter)
}
