import {describe, expect, it} from "vitest"
import {lineupSliceOf} from "@/domains/esports/island/lineupSlice"

const identity = {name: "Valorant", accent: "#ff4655", icon: "/icon.webp", banner: "/art.webp", srcset: "/art.webp 1280w", width: 1280, height: 720}
const team = (id: number) => ({id, name: `Team ${id}`, members: []}) as never

describe("a season's game as a slice", () => {
  it("names the game from its record and counts the teams it fielded", () => {
    const slice = lineupSliceOf({game: "VALORANT", teams: [team(1), team(2)], public: true}, identity, "/competition/valorant")

    expect(slice).toMatchObject({id: "VALORANT", title: "Valorant", meta: "2 teams this season", href: "/competition/valorant", banner: "/art.webp", accent: "#ff4655"})
  })

  it("says one team in the singular", () => {
    expect(lineupSliceOf({game: "VALORANT", teams: [team(1)], public: true}, identity, "/").meta).toBe("1 team this season")
  })

  it("says a game nobody is fielded in is not public yet, and draws no art it has none of", () => {
    const slice = lineupSliceOf({game: "VALORANT", teams: [], public: false}, {...identity, banner: null}, "/")

    expect(slice.meta).toBe("no teams yet · not public")
    expect(slice.banner).toBe("")
  })

  it("names the channels its esports players meet in after the teams", () => {
    const slice = lineupSliceOf({game: "VALORANT", teams: [team(1)], public: true}, {...identity, channels: ["valorant-esports", "scrims"]}, "/")

    expect(slice.meta).toBe("1 team this season · #valorant-esports · #scrims")
  })
})
