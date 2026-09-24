import {describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import GameBySlug from "@/pages/esports/GameBySlug.vue"

const {route} = vi.hoisted(() => ({route: {params: {slug: "valorant"}}}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  useRoute: () => route,
}))

vi.mock("@/domains/esports", () => ({
  useGames: () => ({
    ready: Promise.resolve(),
    bySlug: (slug: string) => slug === "valorant" ? {code: "VALORANT", name: "Valorant"} : undefined,
  }),
}))

describe("GameBySlug", () => {
  it("names the tab after the game it shows", () => {
    document.title = "Esports — Blueshell Esports"
    shallowMount(GameBySlug)
    expect(document.title).toBe("Valorant — Blueshell Esports")
  })
})
