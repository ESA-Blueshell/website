import {describe, expect, it} from "vitest"
import {initialThemeName} from "@/plugins/theme"

describe("theme plugin", () => {
  it("honours a stored preference over the operating system", () => {
    expect(initialThemeName("true", false)).toBe("dark")
    expect(initialThemeName("false", true)).toBe("light")
  })

  it("follows the operating system when nothing is stored", () => {
    expect(initialThemeName(null, true)).toBe("dark")
    expect(initialThemeName(null, false)).toBe("light")
  })

  // Only "true" means dark, which is the check this replaced. Nothing else writes the key.
  it("reads anything else stored as light", () => {
    expect(initialThemeName("yes", true)).toBe("light")
  })
})
