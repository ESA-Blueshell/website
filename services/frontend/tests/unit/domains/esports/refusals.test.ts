import {describe, expect, it} from "vitest"
import {countOf} from "@/domains/esports/copy"
import {sentenceFor} from "@/domains/esports/refusals"

describe("countOf", () => {
  it("names one thing singly", () => {
    expect(countOf(1, "team", "teams")).toBe("1 team")
  })

  it("names several in the plural", () => {
    expect(countOf(3, "team", "teams")).toBe("3 teams")
  })

  it("names none in the plural", () => {
    expect(countOf(0, "person", "people")).toBe("0 people")
  })
})

describe("a game that holds history", () => {
  it("says what the game holds, and that its history stays", () => {
    const said = sentenceFor({code: "GameHoldsHistory", gameName: "Valorant", teams: 3, players: 14})

    expect(said).toContain("Valorant holds 3 teams and 14 people")
    expect(said).toContain("so it cannot be removed")
    expect(said).toContain("Everything it played stays readable")
  })

  it("says one team and one person singly", () => {
    expect(sentenceFor({code: "GameHoldsHistory", gameName: "Trackmania", teams: 1, players: 1})).toContain("holds 1 team and 1 person")
    expect(sentenceFor({code: "GameHoldsHistory"})).toContain("That game holds 0 teams")
  })
})

describe("sentenceFor", () => {
  it("names the code a game was asked for by", () => {
    expect(sentenceFor({code: "UnknownGameCode", gameCode: "PONG"}))
      .toBe("There is no game with the code 'PONG'.")
  })

  it("keeps the competition index's own address", () => {
    expect(sentenceFor({code: "AddressReserved", address: "competitive-scene"}))
      .toBe("The address 'competitive-scene' is kept for the site's own pages.")
  })

  it("names the game already using an address", () => {
    expect(sentenceFor({code: "AddressTaken", gameName: "Blueshell", address: "blueshell"}))
      .toBe("The address 'blueshell' is already used by Blueshell.")
  })

  it("composes a removal refusal from the counts, not from a sentence the api sent", () => {
    expect(sentenceFor({code: "GameHoldsHistory", gameName: "Valorant", teams: 2, players: 6, detail: "Refused."}))
      .toContain("Valorant holds 2 teams and 6 people")
  })

  it("says how many teams still play a game in a season", () => {
    expect(sentenceFor({code: "GameFieldedInSeason", gameName: "Valorant", teams: 1}))
      .toContain("Valorant still has 1 team in this season.")
  })

  it("says which season some dates overlap", () => {
    expect(sentenceFor({code: "SeasonDatesOverlap", seasonName: "Spring 2026"}))
      .toBe("Those dates overlap Spring 2026.")
  })

  it("says the fixed refusals with no facts to name", () => {
    expect(sentenceFor({code: "SeasonEndsBeforeStart"})).toBe("A season cannot end before it starts.")
    expect(sentenceFor({code: "PictureNotStored"})).toBe("That picture is not in storage.")
    expect(sentenceFor({code: "GameNameBlank"})).toBe("A game needs a name.")
  })

  it("answers nothing for a code it has not been taught, so the caller falls back", () => {
    expect(sentenceFor({code: "SomethingAddedLater", detail: "Refused."})).toBeNull()
  })

  it("answers nothing for a body carrying no code at all", () => {
    expect(sentenceFor({detail: "Validation failed"})).toBeNull()
    expect(sentenceFor(null)).toBeNull()
    expect(sentenceFor(undefined)).toBeNull()
  })
})
