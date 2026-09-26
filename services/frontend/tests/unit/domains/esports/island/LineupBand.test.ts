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
    identityOf: (game: string) => (game === "VALORANT"
      ? {name: "Valorant", accent: "#ff4655", icon: null, banner: "/val.webp", srcset: "/val-640.webp 640w", width: 1280, height: 720}
      : {name: "GeoGuessr", accent: "#6cbf3f", icon: null, banner: null}),
    recordOf: (game: string) => (game === "VALORANT" ? {slug: "valorant"} : null),
  }),
}))

const spring = {id: 4, name: "Spring 2026"}
const held = {entries: ref<unknown[]>([]), loading: ref(true), seasons: ref([spring]), selected: ref<number | null>(4)}
vi.mock("@/domains/esports/island/useSeasonLineup", () => ({
  // The band reads the newest season, which it asks for by naming none.
  useSeasonLineup: (season: () => number | null) => (season() === null ? held : {entries: ref([]), loading: ref(false), seasons: ref([]), selected: ref(null)}),
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
    held.selected.value = 4
  })

  const team = (id: number, name: string, extra: Record<string, unknown> = {}) => ({
    id, name, banner: null, icon: null, members: [{handle: `p${id}`, role: "PLAYER"}, {handle: `c${id}`, role: "COACH"}], ...extra,
  })

  it("draws nothing while the season is read, or where it fielded nobody", async () => {
    expect(mountBand().find("[data-testid=home-esports]").exists()).toBe(false)

    held.loading.value = false
    const wrapper = mountBand()
    await flushPromises()
    expect(wrapper.find("[data-testid=home-esports]").exists()).toBe(false)
  })

  it("runs every team of the newest season, named with its game and the season, opening to its line-up", async () => {
    held.loading.value = false
    held.entries.value = [
      {game: "VALORANT", teams: [team(1, "Blue Shells"), team(2, "Blue Waves", {banner: {url: "/waves.webp", renditions: []}})], public: true},
      {game: "GEOGUESSR", teams: [team(1, "Blue Shells")], public: true},
    ]
    const wrapper = mountBand()
    await flushPromises()

    const band = wrapper.findComponent({name: "SliceBand"})
    expect(band.classes()).toContain("island-dark")
    const [shells, waves, geo] = band.props("items")
    expect(shells).toMatchObject({
      id: "VALORANT-1", title: "Blue Shells", meta: "Valorant · Spring 2026", accent: "#ff4655",
      href: "/competition/valorant?season=4", banner: "/val.webp", srcset: "/val-640.webp 640w",
    })
    expect(waves.banner).toBe("/waves.webp")
    expect(geo).toMatchObject({id: "GEOGUESSR-1", href: "/competition", banner: ""})
    expect(wrapper.find("[data-testid=home-esports-link-VALORANT-1]").text()).toBe("Valorant in Spring 2026 →")
    expect(wrapper.text()).toContain("p1")
    expect(wrapper.text()).toContain("Coach")
  })

  it("names a team by its game alone where the season is not known", async () => {
    held.loading.value = false
    held.selected.value = null
    held.entries.value = [{game: "VALORANT", teams: [team(1, "Blue Shells")], public: true}]
    const wrapper = mountBand()
    await flushPromises()

    const [shells] = wrapper.findComponent({name: "SliceBand"}).props("items")
    expect(shells).toMatchObject({meta: "Valorant", href: "/competition/valorant"})
    expect(wrapper.find("[data-testid=home-esports-link-VALORANT-1]").text()).toBe("Valorant →")
  })

  it("draws no line-up for a slice it does not hold", async () => {
    held.loading.value = false
    held.entries.value = [{game: "VALORANT", teams: [team(1, "Blue Shells")], public: true}]
    const wrapper = mount(LineupBand, {global: {stubs: {
      RouterLink: RouterLinkStub,
      SliceBand: {...SliceBand, template: "<div><slot name='details' :item='{id: \"X\", title: \"X\"}' /></div>"},
    }}})
    await flushPromises()

    expect(wrapper.find("[data-testid=home-esports-link-X]").findComponent(RouterLinkStub).props("to")).toBe("/competition")
    expect(wrapper.text()).not.toContain("Coach")
  })

  it("follows a slice that was gone to", async () => {
    held.loading.value = false
    held.entries.value = [{game: "VALORANT", teams: [team(1, "Blue Shells")], public: true}]
    const wrapper = mountBand()
    await flushPromises()

    const band = wrapper.findComponent({name: "SliceBand"})
    band.vm.$emit("go", {href: "/competition/valorant"})
    band.vm.$emit("go", {})

    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith("/competition/valorant")
  })
})
