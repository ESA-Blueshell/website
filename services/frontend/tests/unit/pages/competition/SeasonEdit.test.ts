import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {ref} from "vue"
import SeasonEdit from "@/pages/competition/SeasonEdit.vue"

const route = vi.hoisted(() => ({params: {} as Record<string, string>}))
const router = vi.hoisted(() => ({
  replace: vi.fn(),
  resolve: (to: string) => {
    const url = new URL(to, "http://x")
    return {path: url.pathname, query: Object.fromEntries(url.searchParams)}
  },
  options: {history: {state: {back: "/competition/valorant?season=3&tab=x"}}},
}))
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRoute: () => route, useRouter: () => router}))
const seasons = [{id: 3, name: "Autumn 2025"}]
const listed = vi.hoisted(() => ({value: null as unknown}))
vi.mock("@/domains/esports", () => ({useSeasons: () => {
  listed.value = ref(seasons)
  return {seasons: listed.value, ready: Promise.resolve(seasons)}
}}))

const SeasonEditor = {name: "SeasonEditor", props: ["season", "back"], emits: ["saved", "removed", "cancel"], template: "<div />"}
const stubs = {SeasonEditor, NotFound: {template: "<div data-testid=missing />"}}

beforeEach(() => router.replace.mockReset())

describe("the season edit page", () => {
  it("corrects the season its address names, and goes back on the season saved, or on none once removed", async () => {
    route.params = {id: "3"}
    const wrapper = mount(SeasonEdit, {global: {stubs}})
    await flushPromises()
    const editor = wrapper.getComponent(SeasonEditor)

    expect(editor.props()).toMatchObject({season: seasons[0], back: "/competition/valorant?season=3&tab=x"})
    editor.vm.$emit("saved", {id: 7})
    editor.vm.$emit("removed")
    editor.vm.$emit("cancel")

    expect(router.replace.mock.calls).toEqual([
      [{path: "/competition/valorant", query: {tab: "x", season: "7"}}],
      [{path: "/competition/valorant", query: {tab: "x"}}],
      ["/competition/valorant?season=3&tab=x"],
    ])
  })

  it("adds a season, and reads an address no season answers to as not found", async () => {
    route.params = {}
    const adding = mount(SeasonEdit, {global: {stubs}})
    expect(adding.getComponent(SeasonEditor).props("season")).toBeNull()

    route.params = {id: "99"}
    const missing = mount(SeasonEdit, {global: {stubs}})
    expect(missing.find("[data-testid=missing]").exists()).toBe(false)
    await flushPromises()
    expect(missing.find("[data-testid=missing]").exists()).toBe(true)
  })

  it("keeps the editor while the strip is read again without the season it is removing", async () => {
    route.params = {id: "3"}
    const wrapper = mount(SeasonEdit, {global: {stubs}})
    await flushPromises()

    ;(listed.value as {value: unknown[]}).value = []
    await flushPromises()

    expect(wrapper.getComponent(SeasonEditor).props("season")).toEqual(seasons[0])
    expect(wrapper.find("[data-testid=missing]").exists()).toBe(false)
  })
})
