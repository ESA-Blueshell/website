/*
 * That the bitmap face carries every letter the history is written in.
 *
 * Silkscreen is a small face: 226 codepoints, where Barlow has thousands. The history's copy is
 * fixed text in the repository, so what it needs can be checked exactly rather than guessed at
 * — and a milestone written with an em dash or an accent the face lacks would go to the page as
 * tofu, mid-sentence, while it is being written out a character at a time.
 */
import {describe, expect, it} from "vitest"
import {coverageOf} from "./glyphCoverage"
import {MILESTONES} from "@/domains/association/historyAxis"

const BITMAP_FACES = ["Silkscreen-Regular.ttf", "Silkscreen-Bold.ttf"]

/** Every character the band actually sets in the bitmap face: the summaries and the tellings. */
const written = (): string[] => {
  const copy = MILESTONES.map(milestone => `${milestone.summary} ${milestone.telling}`).join("")
  return [...new Set(copy)].filter(character => character !== " ")
}

describe("the face the history is written in", () => {
  it("has every letter the milestones are written with", () => {
    for (const face of BITMAP_FACES) {
      const covered = coverageOf(face)
      const missing = written().filter(character => !covered(character.codePointAt(0) ?? 0))
      expect(missing, `${face} would draw tofu in the history`).toEqual([])
    }
  })
})
