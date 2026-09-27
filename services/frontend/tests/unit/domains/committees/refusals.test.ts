import {describe, expect, it} from "vitest"
import {sentenceFor} from "@/domains/committees/refusals"

describe("a refused committee write, in words", () => {
  it("says what was wrong with the address, the committee asked for or the picture", () => {
    expect(sentenceFor({code: "CommitteeAddressBlank"})).toBe("A committee's page needs an address.")
    expect(sentenceFor({code: "CommitteeAddressTaken", address: "board", committeeName: "Board"})).toBe("The address 'board' is already used by Board.")
    expect(sentenceFor({code: "UnknownCommitteeAddress", address: "gone"})).toBe("No committee answers to 'gone'.")
    expect(sentenceFor({code: "PictureNotStored"})).toBe("That picture is not in storage.")
  })
})
