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
