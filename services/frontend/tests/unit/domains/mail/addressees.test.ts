import {describe, expect, it} from "vitest"
import {AddresseeKind, addresseeKey, addresseeOf} from "@/domains/mail"

describe("addressees", () => {
  it("names each addressee by one key, and reads only keys it made", () => {
    expect(addresseeKey({kind: AddresseeKind.ROLE, id: "BOARD"})).toBe("ROLE:BOARD")
    expect(addresseeOf("COHORT:PERIOD_MEMBERS:4")).toEqual({kind: AddresseeKind.COHORT, id: "PERIOD_MEMBERS:4"})
    expect(addresseeOf("PERSON:12")).toEqual({kind: AddresseeKind.PERSON, id: "12"})
    expect(addresseeOf("TEAM:3")).toBeNull()
    expect(addresseeOf("nothing")).toBeNull()
  })
})
