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
    expect(router.resolve("/esports/competitive-scene").name).toBe("esports")
    expect(router.resolve("/esports").redirectedFrom).toBeUndefined()
  })
})

describe("Event routes", () => {
  it("gives every event a page of its own, and keeps the words the other pages go by", () => {
    expect(router.resolve("/events/12").name).toBe("event")
    expect(router.resolve("/events/create").name).not.toBe("event")
    expect(router.resolve("/events/circuitShowdown").name).not.toBe("event")
  })

  it("keeps the archive of past events on its own address, and loads its page", async () => {
    expect(router.resolve("/events/past").name).toBe("events/past")

    const load = router.getRoutes().find(one => one.name === "events/past")?.components?.default as () => Promise<unknown>
    await expect(load()).resolves.toBeDefined()
  }, 20_000)

  it("sends the links that named an event by hash or by query to its page", async () => {
    await router.push("/events#12")
    expect(router.currentRoute.value.path).toBe("/events/12")

    await router.push("/events?event=7")
    expect(router.currentRoute.value.path).toBe("/events/7")

    // A guest's sign-up link carries a token in the hash, which is not an event.
    await router.push("/events#accessToken=abc")
    expect(router.currentRoute.value.path).toBe("/events")
    await router.push("/")
  }, 20_000)
})
