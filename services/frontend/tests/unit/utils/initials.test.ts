import {describe, expect, it} from "vitest"
import {initialsOf} from "@/utils/initials"

describe("initialsOf", () => {
  it("takes a plate's letters from the first two words, whatever the punctuation", () => {
    expect(initialsOf("Super Smash Bros.")).toBe("SS")
    expect(initialsOf("CS:GO")).toBe("C")
    expect(initialsOf("pokémon")).toBe("P")
    expect(initialsOf("One-Of-Committee (Member's Iniative)")).toBe("OM")
  })
})
