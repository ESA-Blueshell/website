/*
 * That the faces a name can be set in carry the letters names are spelled with.
 *
 * A person's name is set in Barlow Semi Condensed, and the display face is Shellhouse One,
 * which was cut for exactly this: Fugaz One, which it is a modified version of, has no İ, ı,
 * ş, Ş, ğ or Ğ, so a name written in any of them lost letters to a fallback mid-word or to
 * tofu. That is a
 * claim about files in src/assets/fonts, so it is checked against those files rather than
 * trusted: this reads their `cmap` tables and asks each one for the letters.
 *
 * Both halves matter. If either face is ever replaced by something narrower, this fails and
 * says which file and which letters, rather than a name breaking on the page.
 */
import {describe, expect, it} from "vitest"
import {missingFrom} from "./glyphCoverage"

const DISPLAY_FACE = "ShellhouseOne-Regular.ttf"
const BARLOW_FACES = [
  "BarlowSemiCondensed-Light.ttf",
  "BarlowSemiCondensed-Regular.ttf",
  "BarlowSemiCondensed-SemiBold.ttf",
]

/** The six a Turkish name is spelled with, and the two every Latin face has anyway. */
const TURKISH = {
  "İ": 0x0130,
  "ı": 0x0131,
  "ş": 0x015f,
  "Ş": 0x015e,
  "ğ": 0x011f,
  "Ğ": 0x011e,
}
const EUROPEAN = {"ë": 0x00eb, "é": 0x00e9}

describe("the fonts a name can be set in", () => {
  it("has every Turkish letter in the display face, which is why it is the Turkish cut", () => {
    expect(
      missingFrom(DISPLAY_FACE, TURKISH),
      `${DISPLAY_FACE} is the plain cut again, and names in it are about to lose letters`,
    ).toEqual([])
  })

  it("has every one of them in every weight of Barlow Semi Condensed, which is what --font-name names", () => {
    for (const face of BARLOW_FACES) {
      expect(missingFrom(face, TURKISH), `${face} is about to break every name on the site`).toEqual([])
    }
  })

  it("has ë and é in the display face too, so nothing was traded for the Turkish letters", () => {
    expect(missingFrom(DISPLAY_FACE, EUROPEAN)).toEqual([])
  })
})
