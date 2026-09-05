import {describe, expect, it} from "vitest"
import type {RouteLocationNormalizedLoaded} from "vue-router"
import {seasonInRoute} from "@/domains/esports/island/seasonInRoute"

const route = (query: Record<string, string | string[]>) =>
  ({query}) as RouteLocationNormalizedLoaded

describe("seasonInRoute", () => {
  it("reads the season a url names", () => {
    expect(seasonInRoute(route({season: "19"}))).toBe(19)
  })

  it("reads nothing where the url names no season, which is where the page opens on the running one", () => {
    expect(seasonInRoute(route({}))).toBeNull()
    expect(seasonInRoute(route({season: ""}))).toBeNull()
    expect(seasonInRoute(route({board: "9"}))).toBeNull()
  })

  it("reads nothing out of a value that is not a season's number", () => {
    expect(seasonInRoute(route({season: "nineteen"}))).toBeNull()
    expect(seasonInRoute(route({season: "0"}))).toBeNull()
    expect(seasonInRoute(route({season: "-2"}))).toBeNull()
    expect(seasonInRoute(route({season: "Infinity"}))).toBeNull()
  })

  it("takes the first of a query repeated, which is what a hand-written url does", () => {
    expect(seasonInRoute(route({season: ["19", "18"]}))).toBe(19)
  })

  it("lets a fraction through, where the same reader for boards would not", () => {
    // `Number.isFinite` here against `Number.isInteger` in boardInRoute: the two readers are
    // otherwise the same function and disagree only on this. No season is numbered 19.5, so the
    // page asks for one that cannot exist rather than opening on the running season.
    expect(seasonInRoute(route({season: "19.5"}))).toBe(19.5)
  })
})
