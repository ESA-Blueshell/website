import {describe, expect, it} from "vitest"
import {ADDRESS_LENGTH, addressOf} from "@/utils/address"

// Pinned to the same values as PageAddressTest, the api twin's test.
describe("addressOf", () => {
  it("keeps letters and digits of any alphabet, and makes everything else one hyphen", () => {
    expect(addressOf("  Pokémon Fan Club!  ")).toBe("pokémon-fan-club")
    expect(addressOf("Counter-Strike 2")).toBe("counter-strike-2")
    expect(addressOf("--LAN / Cie--")).toBe("lan-cie")
  })

  it("is never longer than an address column", () => {
    expect(addressOf("a".repeat(100))).toHaveLength(ADDRESS_LENGTH)
  })
})
