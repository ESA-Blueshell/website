import {describe, expect, it} from "vitest"
import {sentenceFor} from "@/domains/committees/refusals"

describe("a refused committee write, in words", () => {
  it("says what was wrong with the address, the committee asked for or the picture", () => {
    expect(sentenceFor({code: "CommitteeAddressBlank"})).toBe("A committee's page needs an address.")
    expect(sentenceFor({code: "CommitteeAddressTaken", address: "board", committeeName: "Board"})).toBe("The address 'board' is already used by Board.")
    expect(sentenceFor({code: "CommitteeEventsNeedTaker", events: 1})).toBe("It still organises 1 event. Pick the committee that takes them over.")
    expect(sentenceFor({code: "CommitteeEventsNeedTaker", events: 4})).toBe("It still organises 4 events. Pick the committee that takes them over.")
    expect(sentenceFor({code: "CommitteeCannotTakeOwnEvents"})).toBe("Pick another committee to take over its events.")
    expect(sentenceFor({code: "ArchivedCommitteeCannotTakeEvents", committeeName: "Oldcie"})).toBe("Oldcie is archived, so it cannot take over events.")
    expect(sentenceFor({code: "UnknownCommitteeAddress", address: "gone"})).toBe("No committee answers to 'gone'.")
    expect(sentenceFor({code: "PictureNotStored"})).toBe("That picture is not in storage.")
  })
})
