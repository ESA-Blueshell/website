import {describe, expect, it, vi} from "vitest"
import {useReturnTo} from "@/composables/useReturnTo"

const state = vi.hoisted(() => ({back: null as unknown}))
vi.mock("vue-router", () => ({useRouter: () => ({options: {history: {state}}})}))

describe("where an edit page goes back to", () => {
  it("goes back to the page it came from, with its query", () => {
    state.back = "/competition?season=3"
    expect(useReturnTo("/competition")).toBe("/competition?season=3")
  })

  it("falls back where it came from nowhere, from outside, from the login page or from another edit page", () => {
    for (const back of [null, "https://elsewhere.nl/x", "//elsewhere.nl", "/login?next=/x", "/competition/new?season=3", "/casual/chess/edit"]) {
      state.back = back
      expect(useReturnTo("/competition")).toBe("/competition")
    }
  })
})
