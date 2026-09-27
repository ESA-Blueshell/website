import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import {ref} from "vue"
import LineupBand from "@/domains/esports/island/LineupBand.vue"

const {mockPush} = vi.hoisted(() => ({mockPush: vi.fn()}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  useRouter: () => ({push: mockPush}),
}))

vi.mock("@/domains/esports/island/useGames", () => ({
  useGames: () => ({
    ready: Promise.resolve([]),
    identityOf: (game: string) => ({name: game === "VALORANT" ? "Valorant" : "GeoGuessr", accent: "#fff", icon: null, banner: null}),
    recordOf: (game: string) => (game === "VALORANT" ? {slug: "valorant"} : null),
  }),
}))

const held = {entries: ref<unknown[]>([]), loading: ref(true)}
vi.mock("@/domains/esports/island/useSeasonLineup", () => ({
  // The band reads the newest season, which it asks for by naming none.
  useSeasonLineup: (season: () => number | null) => (season() === null ? held : {entries: ref([]), loading: ref(false)}),
}))

const SliceBand = {
  name: "SliceBand",
  props: {items: Array, accent: String, testidPrefix: String, short: Boolean},
  emits: ["go"],
  template: '<div><div v-for="item in items" :key="item.id"><slot name="details" :item="item" /></div></div>',
}

const mountBand = () => mount(LineupBand, {global: {stubs: {SliceBand, RouterLink: RouterLinkStub}}})

describe("LineupBand", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    held.entries.value = []
    held.loading.value = true
  })

  it("draws nothing while the season is read, or where it fielded nobody", async () => {
    expect(mountBand().find("[data-testid=home-esports]").exists()).toBe(false)

    held.loading.value = false
    const wrapper = mountBand()
    await flushPromises()
    expect(wrapper.find("[data-testid=home-esports]").exists()).toBe(false)
  })

  it("runs each game fielded with its team count, pinned dark, leading to its own page", async () => {
    held.loading.value = false
    held.entries.value = [
      {game: "VALORANT", teams: [{id: 1}], public: true},
      {game: "GEOGUESSR", teams: [{id: 2}, {id: 3}], public: true},
    ]
    const wrapper = mountBand()
    await flushPromises()

    const band = wrapper.findComponent({name: "SliceBand"})
    expect(band.classes()).toContain("island-dark")
    expect(band.props("short")).toBe(true)
    const [valorant, geo] = band.props("items")
    expect(valorant).toMatchObject({title: "Valorant", meta: "1 team this season", href: "/competition/valorant"})
    expect(geo.href).toBe("/competition")
    expect(wrapper.find("[data-testid=home-esports-link-VALORANT]").text()).toBe("Valorant this season →")
    expect(wrapper.findAllComponents(RouterLinkStub).map(one => one.props("to")))
      .toEqual(expect.arrayContaining(["/competition", "/competition/valorant"]))
  })

  it("leads a slice with no address of its own to the index", async () => {
    held.loading.value = false
    held.entries.value = [{game: "VALORANT", teams: [], public: true}]
    const wrapper = mount(LineupBand, {global: {stubs: {
      RouterLink: RouterLinkStub,
      SliceBand: {...SliceBand, template: "<div><slot name='details' :item='{id: \"X\", title: \"X\"}' /></div>"},
    }}})
    await flushPromises()

    expect(wrapper.find("[data-testid=home-esports-link-X]").findComponent(RouterLinkStub).props("to")).toBe("/competition")
  })

  it("follows a slice that was gone to", async () => {
    held.loading.value = false
    held.entries.value = [{game: "VALORANT", teams: [], public: true}]
    const wrapper = mountBand()
    await flushPromises()

    const band = wrapper.findComponent({name: "SliceBand"})
    band.vm.$emit("go", {href: "/competition/valorant"})
    band.vm.$emit("go", {})

    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith("/competition/valorant")
  })
})
