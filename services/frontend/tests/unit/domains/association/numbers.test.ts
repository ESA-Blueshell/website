import {describe, expect, it} from "vitest"
import {
  associationFigures,
  figureText,
  FLOORS,
  MEMBERS_CLAIMED,
} from "@/domains/association/numbers"

const counted = {
  boards: 9,
  committees: 15,
  eventsLastYear: 63,
  gamesPlayed: 5,
  seasonsPlayed: 12,
  teamsThisSeason: 13,
}

const byId = (numbers: Parameters<typeof associationFigures>[0], id: string) =>
  associationFigures(numbers).find(figure => figure.id === id)

describe("associationFigures", () => {
  it("stands on the published floors before the api has counted anything", () => {
    expect(byId(null, "teams")).toMatchObject({value: FLOORS.teamsThisSeason, exact: false})
    expect(byId(null, "committees")).toMatchObject({value: FLOORS.committees, exact: false})
    expect(byId(null, "events")).toMatchObject({value: FLOORS.eventsLastYear, exact: false})
  })

  it("takes the counted numbers where the api has answered", () => {
    expect(byId(counted, "teams")).toMatchObject({value: 13, exact: true})
    expect(byId(counted, "committees")).toMatchObject({value: 15, exact: true})
    expect(byId(counted, "events")).toMatchObject({value: 63, exact: true})
  })

  // The count is permission-gated, so a visitor reading this page cannot be told it: no
  // answer from the statistics endpoint may turn the association's own claim into a count.
  it("keeps the member count a claim whatever the api says", () => {
    expect(byId(null, "members")).toMatchObject({value: MEMBERS_CLAIMED, exact: false})
    expect(byId(counted, "members")).toMatchObject({value: MEMBERS_CLAIMED, exact: false})
  })

  it("draws the same four figures either way, so nothing appears or leaves", () => {
    expect(associationFigures(counted).map(one => one.id))
      .toEqual(associationFigures(null).map(one => one.id))
  })
})

describe("figureText", () => {
  it("marks a floor as at least that many and states a count plainly", () => {
    expect(figureText({id: "members", value: 200, exact: false, label: ""})).toBe("200+")
    expect(figureText({id: "teams", value: 13, exact: true, label: ""})).toBe("13")
  })
})
