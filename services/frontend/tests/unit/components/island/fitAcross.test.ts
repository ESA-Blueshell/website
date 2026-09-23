import {describe, expect, it} from "vitest"
import {fitAcross} from "@/components/island/fitAcross"

describe("fitAcross", () => {
  it("keeps a count until the items would drop below their narrowest", () => {
    expect(fitAcross(1920, 420)).toBe(4)
    expect(fitAcross(1680, 420)).toBe(4)
    expect(fitAcross(1679, 420)).toBe(3)
  })

  it("adds items as the row widens rather than stretching the ones it has", () => {
    expect(fitAcross(3840, 420)).toBe(9)
  })

  it("keeps the fewest asked for, however narrow the row", () => {
    expect(fitAcross(390, 340, 2)).toBe(2)
    expect(fitAcross(1024, 340, 2)).toBe(3)
  })

  it("never fits fewer than one", () => {
    expect(fitAcross(0, 420)).toBe(1)
    expect(fitAcross(300, 420)).toBe(1)
  })
})
