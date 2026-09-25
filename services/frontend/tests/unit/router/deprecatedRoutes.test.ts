import {describe, expect, it} from "vitest"
import router from "@/plugins/router"

describe("Esports routes", () => {
  it("serves every game from one address, whatever the game", () => {
    // No route is written per game: a game's page is reached by the address its record names.
    expect(router.resolve("/competition/trackmania").name).toBe("game")
    expect(router.resolve("/competition/valorant").name).toBe("game")
    expect(router.resolve("/competition/a-game-nobody-has-added-yet").name).toBe("game")
  })

  it("keeps the index on its own address rather than reading it as a game", () => {
    expect(router.resolve("/competition").name).toBe("esports")
    expect(router.resolve("/esports").redirectedFrom).toBeUndefined()
  })

  it("sends every old esports address to its competition counterpart", () => {
    const target = (path: string) => {
      const record = router.getRoutes().find(one => one.path === path)
      const redirect = record?.redirect
      return typeof redirect === "function" ? redirect(router.resolve(path.replace(":slug", "trackmania"))) : redirect
    }

    expect(target("/esports")).toBe("/competition")
    expect(target("/esports/competitive-scene")).toBe("/competition")
    expect(target("/esports/:slug")).toBe("/competition/trackmania")
    expect(target("/management/esports")).toBe("/competition")
  })
})

describe("Edit routes", () => {
  it("edits a game from either area, and a season or a team in the competition, on their own pages", () => {
    expect(router.resolve("/casual/new").name).toBe("casualGameNew")
    expect(router.resolve("/casual/chess/edit").meta.area).toBe("casual")
    expect(router.resolve("/competition/new").name).toBe("competitionGameNew")
    expect(router.resolve("/competition/valorant/edit").meta.area).toBe("competition")
    expect(router.resolve("/competition/seasons/new").name).toBe("seasonNew")
    expect(router.resolve("/competition/seasons/3/edit").name).toBe("seasonEdit")
    expect(router.resolve("/competition/valorant/teams/new").name).toBe("teamNew")
    expect(router.resolve("/competition/valorant/teams/9/edit").name).toBe("teamEdit")
    expect(router.resolve("/competition/valorant").name).toBe("game")
  })
})

describe("Committee routes", () => {
  it("serves every committee from its address, and keeps the manager on its own", () => {
    expect(router.resolve("/committees/lancie").name).toBe("committee")
    expect(router.resolve("/committees/manage").name).toBe("committeeManager")
    expect(router.resolve("/committees").name).toBe("committees")
  })

  it("loads each committee and casual page's own component", async () => {
    const names = [
      "committee", "casual", "casualGame", "casualGameNew", "casualGameEdit", "competitionGameNew", "competitionGameEdit",
      "seasonNew", "seasonEdit", "teamNew", "teamEdit",
    ]
    const pages = names.map(name => router.getRoutes().find(one => one.name === name)!)
    const loaded = await Promise.all(pages.map(one => (one.components!.default as () => Promise<{default: {name?: string}}>)()))

    expect(loaded.map(one => one.default.name)).toEqual([
      "CommitteeByAddressPage", "CasualPage", "CasualGameBySlugPage", "GameEditPage", "GameEditPage", "GameEditPage", "GameEditPage",
      "SeasonEditPage", "SeasonEditPage", "TeamEditPage", "TeamEditPage",
    ])
  }, 30_000)
})
