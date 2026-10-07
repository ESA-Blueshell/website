import {describe, expect, it} from "vitest"
import router from "@/plugins/router"

describe("Esports routes", () => {
  it("serves every game from one address, whatever the game", () => {
    // No route is written per game: a game's page is reached by the address its record names.
    expect(router.resolve("/esports/trackmania").name).toBe("game")
    expect(router.resolve("/esports/valorant").name).toBe("game")
    expect(router.resolve("/esports/a-game-nobody-has-added-yet").name).toBe("game")
  })

  it("keeps the index on its own address rather than reading it as a game", () => {
    expect(router.resolve("/esports").name).toBe("esports")
    expect(router.resolve("/esports").redirectedFrom).toBeUndefined()
  })

  it("sends every old address, in Management too, to the same page under /esports, its query and hash kept", () => {
    // Read off the redirect itself, since following it would meet the sign-in guard of an edit page.
    const target = (path: string) => {
      const from = router.resolve(path)
      const redirect = from.matched.at(-1)?.redirect
      const to = typeof redirect === "function" ? redirect(from, from) : redirect
      return router.resolve(to as Parameters<typeof router.resolve>[0]).fullPath
    }
    for (const [from, to] of [
      ["/competition", "/esports"],
      ["/competition/valorant?season=19#roster", "/esports/valorant?season=19#roster"],
      ["/competition/seasons/3/edit", "/esports/seasons/3/edit"],
      ["/esports/competitive-scene", "/esports"],
    ]) {
      expect(target(from), from).toBe(to)
    }
  })
})

describe("Edit routes", () => {
  it("edits a game from either area, and a season or a team in the competition, on their own pages", () => {
    expect(router.resolve("/casual/new").name).toBe("casualGameNew")
    expect(router.resolve("/casual/chess/edit").meta.area).toBe("casual")
    expect(router.resolve("/esports/new").name).toBe("competitionGameNew")
    expect(router.resolve("/esports/valorant/edit").meta.area).toBe("competition")
    expect(router.resolve("/esports/seasons/new").name).toBe("seasonNew")
    expect(router.resolve("/esports/seasons/3/edit").name).toBe("seasonEdit")
    expect(router.resolve("/esports/valorant/teams/new").name).toBe("teamNew")
    expect(router.resolve("/esports/valorant/teams/9/edit").name).toBe("teamEdit")
    expect(router.resolve("/esports/valorant").name).toBe("game")
  })
})

describe("Committee routes", () => {
  it("serves every committee from its address, and sends the old manager to the committees", () => {
    expect(router.resolve("/committees/lancie").name).toBe("committee")
    expect(router.resolve("/committees/manage").matched[0]?.redirect).toBe("/committees")
    expect(router.resolve("/committees").name).toBe("committees")
    expect(router.resolve("/committees/new").name).toBe("committeeNew")
    expect(router.resolve("/committees/lancie/edit").name).toBe("committeeEdit")
  })

  it("edits a board and its members on their own pages, by number", () => {
    expect(router.resolve("/board/new").name).toBe("boardNew")
    expect(router.resolve("/board/9/edit").name).toBe("boardEdit")
    expect(router.resolve("/board/9/members/new").name).toBe("boardMemberNew")
    expect(router.resolve("/board/9/members/91/edit").name).toBe("boardMemberEdit")
    expect(router.resolve("/board/nine/edit").name).not.toBe("boardEdit")
  })

  it("loads each committee and casual page's own component", async () => {
    const names = [
      "committee", "casual", "casualGame", "casualGameNew", "casualGameEdit", "competitionGameNew", "competitionGameEdit",
      "seasonNew", "seasonEdit", "teamNew", "teamEdit", "committeeNew", "committeeEdit",
      "boardNew", "boardEdit", "boardMemberNew", "boardMemberEdit",
    ]
    const pages = names.map(name => router.getRoutes().find(one => one.name === name)!)
    const loaded = await Promise.all(pages.map(one => (one.components!.default as () => Promise<{default: {name?: string}}>)()))

    expect(loaded.map(one => one.default.name)).toEqual([
      "CommitteeByAddressPage", "CasualPage", "CasualGameBySlugPage", "GameEditPage", "GameEditPage", "GameEditPage", "GameEditPage",
      "SeasonEditPage", "SeasonEditPage", "TeamEditPage", "TeamEditPage", "CommitteeEditPage", "CommitteeEditPage",
      "BoardEditPage", "BoardEditPage", "BoardMemberEditPage", "BoardMemberEditPage",
    ])
  }, 30_000)
})
