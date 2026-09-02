import {describe, expect, it} from "vitest"
import {monogramOf} from "@/components/island/monogram"

describe("monogramOf", () => {
  it("takes the first and the last of the names somebody is written under", () => {
    expect(monogramOf("Thijs Lieverse")).toBe("TL")
  })

  it("skips the nickname the name is published with", () => {
    // The history writes a nickname inside the name, and the letters on the plate are the
    // person's rather than the nickname's.
    expect(monogramOf('Roos "SkyeWolf" Kruk')).toBe("RK")
  })

  it("reads the first and last of a name written with more than two parts", () => {
    expect(monogramOf("Anne van der Schrader")).toBe("AS")
  })

  it("gives one letter for a name written as one word", () => {
    expect(monogramOf("Shelly")).toBe("S")
  })

  it("keeps an accented letter rather than dropping it", () => {
    expect(monogramOf("Émile Škoda")).toBe("ÉŠ")
  })

  it("reads past a mark to the letter behind it", () => {
    expect(monogramOf("'t Hooft Jansen-Kruk")).toBe("TJ")
  })

  it("gives nothing at all where a name yields no letter", () => {
    // An empty plate is honest; a plate carrying a punctuation mark set large is not.
    expect(monogramOf("")).toBe("")
    expect(monogramOf("  ")).toBe("")
    expect(monogramOf("--")).toBe("")
  })
})
