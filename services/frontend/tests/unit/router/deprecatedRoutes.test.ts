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

describe("Committee routes", () => {
  it("serves every committee from its address, and keeps the manager on its own", () => {
    expect(router.resolve("/committees/lancie").name).toBe("committee")
    expect(router.resolve("/committees/manage").name).toBe("committeeManager")
    expect(router.resolve("/committees").name).toBe("committees")
  })

  it("loads each committee and casual page's own component", async () => {
    const pages = ["committee", "casual", "casualGame"].map(name => router.getRoutes().find(one => one.name === name)!)
    const loaded = await Promise.all(pages.map(one => (one.components!.default as () => Promise<{default: {name?: string}}>)()))

    expect(loaded.map(one => one.default.name)).toEqual(["CommitteeByAddressPage", "CasualPage", "CasualGameBySlugPage"])
  }, 30_000)
})
