import {describe, expect, it} from "vitest"
import type {RouteLocationNormalizedLoaded} from "vue-router"
import {wholeNumberInQuery} from "@/utils/wholeNumberInQuery"

const route = (query: Record<string, string | string[]>) =>
  ({query}) as RouteLocationNormalizedLoaded

describe("wholeNumberInQuery", () => {
  it("reads the number the named key carries", () => {
    expect(wholeNumberInQuery(route({season: "19", board: "9"}), "season")).toBe(19)
    expect(wholeNumberInQuery(route({season: "19", board: "9"}), "board")).toBe(9)
  })

  it("reads nothing where the key is absent or empty", () => {
    expect(wholeNumberInQuery(route({}), "season")).toBeNull()
    expect(wholeNumberInQuery(route({season: ""}), "season")).toBeNull()
  })

  it("reads nothing out of a value no row could be numbered", () => {
    expect(wholeNumberInQuery(route({season: "nineteen"}), "season")).toBeNull()
    expect(wholeNumberInQuery(route({season: "19.5"}), "season")).toBeNull()
    expect(wholeNumberInQuery(route({season: "0"}), "season")).toBeNull()
    expect(wholeNumberInQuery(route({season: "-2"}), "season")).toBeNull()
    expect(wholeNumberInQuery(route({season: "Infinity"}), "season")).toBeNull()
  })

  it("takes the first of a query repeated, which is what a hand-written url does", () => {
    expect(wholeNumberInQuery(route({season: ["19", "18"]}), "season")).toBe(19)
  })
})
