import {describe, expect, it} from "vitest"
import {maskedIban} from "@/domains/contribution/incasso"

describe("an IBAN shown without a reveal", () => {
  it("keeps the country code and the last two characters only", () => {
    expect(maskedIban({ibanCountry: "NL", ibanLastTwo: "34"})).toBe("NL•• … ••34")
    expect(maskedIban({ibanCountry: "DE", ibanLastTwo: null})).toBe("None recorded")
    expect(maskedIban(null, "none")).toBe("none")
  })
})
