import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {h, ref} from "vue"
import SeasonEditor from "@/domains/esports/components/SeasonEditor.vue"

const adapter = vi.hoisted(() => ({
  dropSeasonOrReason: vi.fn(),
  enterGameInSeason: vi.fn(),
  leaveGameInSeason: vi.fn(),
  loadSeasonContents: vi.fn(),
  loadSeasonGames: vi.fn(),
  saveSeasonOrReason: vi.fn(),
}))
vi.mock("@/domains/esports/adapters/esports", () => adapter)
const seasonsStore = vi.hoisted(() => ({seasons: null as never, refresh: vi.fn()}))
vi.mock("@/domains/esports/island/useSeasons", () => ({useSeasons: () => seasonsStore}))
vi.mock("@/domains/esports/island/useGames", () => ({
  useGames: () => ({
    games: ref([{code: "VAL", name: "Valorant"}, {code: "CS2", name: "Counter-Strike 2"}]),
    identityOf: (code: string) => ({name: code === "VAL" ? "Valorant" : ""}),
  }),
}))

const autumn = {id: 3, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31", played: true}
const spring = {id: 4, name: "Spring 2026", startDate: "2026-02-01", endDate: "2026-06-30", played: false}

const passThrough = (name: string) => ({name, setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) =>
  () => h("div", [slots["actions"]?.(), slots["default"]?.(), slots["footer"]?.(), slots["preview"]?.()])})
const stubs = {
  EditPage: {...passThrough("EditPage"), props: ["title", "eyebrow", "back", "testid"]},
  PreviewFrame: passThrough("PreviewFrame"),
  Timeline: {name: "Timeline", props: ["stops", "selectedId", "accent"], template: "<div />"},
  SearchPicker: {name: "SearchPicker", props: ["options"], emits: ["pick"], template: "<div />"},
  ConfirmDialog: {name: "ConfirmDialog", props: ["open", "question", "failure"], emits: ["confirm", "update:open"], template: "<div />"},
  RouterLink: {props: ["to"], template: "<a :data-to='to'><slot /></a>"},
}

/** Types into the island field under [testid], as its control reports what was typed. */
const write = async (wrapper: Awaited<ReturnType<typeof mountEditor>>, testid: string, value: string) => {
  wrapper.findAllComponents({name: "FormControl"}).find(one => one.attributes("data-testid") === testid)!.vm.$emit("update:modelValue", value)
  await flushPromises()
}

const mountEditor = async (season: typeof autumn | null) => {
  const wrapper = mount(SeasonEditor, {props: {season, back: "/competition"}, global: {stubs}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  Object.values(adapter).forEach(one => one.mockReset())
  adapter.loadSeasonGames.mockResolvedValue([{game: "VAL", teams: [{id: 1}], public: true}, {game: "LOL", teams: [], public: false}])
  seasonsStore.seasons = ref([autumn, spring]) as never
  seasonsStore.refresh.mockReset().mockResolvedValue([])
})

describe("the season edit page", () => {
  it("adds a season, previewing it on the strip among the others as it is typed", async () => {
    adapter.saveSeasonOrReason.mockResolvedValue({ok: true, season: {...spring, id: 9}})
    const wrapper = await mountEditor(null)
    const strip = () => wrapper.getComponent(stubs.Timeline)

    expect(strip().props("selectedId")).toBe(-1)
    expect(strip().props("stops").map((one: {name: string}) => one.name)).toContain("New season")
    await write(wrapper, "season-edit-name", "Autumn 2026")
    await write(wrapper, "season-edit-start", "2026-09-01")
    await write(wrapper, "season-edit-end", "2027-01-31")
    expect(strip().props("stops").at(-1).name).toBe("Autumn 2026")
    expect(wrapper.find("[data-testid=season-edit-games]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=season-edit-remove]").exists()).toBe(false)
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveSeasonOrReason).toHaveBeenCalledWith({id: undefined, name: "Autumn 2026", startDate: "2026-09-01", endDate: "2027-01-31"})
    expect(seasonsStore.refresh).toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toEqual([[{...spring, id: 9}]])
  })

  it("keeps what was typed when refused, and never saves without every field", async () => {
    adapter.saveSeasonOrReason.mockResolvedValue({ok: false, reason: "Those dates overlap Spring 2026."})
    const wrapper = await mountEditor(autumn)

    expect(wrapper.getComponent(stubs.Timeline).props("selectedId")).toBe(3)
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=season-edit-failure]").text()).toBe("Those dates overlap Spring 2026.")
    expect(wrapper.emitted("saved")).toBeUndefined()

    await write(wrapper, "season-edit-name", "")
    expect(wrapper.get("[data-testid=season-edit-save]").attributes("disabled")).toBeDefined()
    await wrapper.get("[data-testid=season-edit-cancel]").trigger("click")
    expect(wrapper.emitted("cancel")).toHaveLength(1)
  })

  it("lists the games entered in the season, enters one played before and takes one out", async () => {
    adapter.enterGameInSeason.mockResolvedValueOnce({ok: true}).mockResolvedValueOnce({ok: false, reason: "Already in."})
    adapter.leaveGameInSeason.mockResolvedValueOnce({ok: false, reason: "Valorant still has 1 team in this season."}).mockResolvedValueOnce({ok: true})
    const wrapper = await mountEditor(autumn)

    expect(wrapper.get("[data-testid=season-edit-game-VAL]").text()).toContain("Valorant")
    expect(wrapper.get("[data-testid=season-edit-game-VAL]").text()).toContain("1 team")
    expect(wrapper.get("[data-testid=season-edit-game-LOL]").text()).toContain("LOL")
    expect(wrapper.getComponent(stubs.SearchPicker).props("options")).toEqual([{key: "CS2", label: "Counter-Strike 2"}])
    expect(wrapper.get("[data-testid=season-edit-new-game]").attributes("data-to")).toBe("/competition/new?season=3")

    wrapper.getComponent(stubs.SearchPicker).vm.$emit("pick", "CS2")
    await flushPromises()
    expect(adapter.enterGameInSeason).toHaveBeenCalledWith(3, "CS2")
    expect(adapter.loadSeasonGames).toHaveBeenCalledTimes(2)
    wrapper.getComponent(stubs.SearchPicker).vm.$emit("pick", "CS2")
    await flushPromises()
    expect(wrapper.get("[data-testid=season-edit-games-failure]").text()).toBe("Already in.")

    await wrapper.get("[data-testid=season-edit-take-out-VAL]").trigger("click")
    await flushPromises()
    expect(wrapper.get("[data-testid=season-edit-games-failure]").text()).toContain("still has 1 team")
    await wrapper.get("[data-testid=season-edit-take-out-LOL]").trigger("click")
    await flushPromises()
    expect(adapter.leaveGameInSeason).toHaveBeenLastCalledWith(3, "LOL")
    expect(wrapper.find("[data-testid=season-edit-games-failure]").exists()).toBe(false)
  })

  it("says what removing the season takes with it before it goes", async () => {
    adapter.loadSeasonContents.mockResolvedValueOnce({teams: 2, players: 9}).mockResolvedValueOnce({teams: 0, players: 0})
    adapter.dropSeasonOrReason.mockResolvedValueOnce({ok: false, reason: "Refused."}).mockResolvedValueOnce({ok: true})
    const wrapper = await mountEditor(autumn)
    const confirm = () => wrapper.getComponent(stubs.ConfirmDialog)

    await wrapper.get("[data-testid=season-edit-remove]").trigger("click")
    await flushPromises()
    expect(confirm().props("question")).toBe("Autumn 2025 holds 2 teams and 9 people. Removing the season takes them with it.")
    confirm().vm.$emit("confirm")
    await flushPromises()
    expect(confirm().props("failure")).toBe("Refused.")

    await wrapper.get("[data-testid=season-edit-remove]").trigger("click")
    await flushPromises()
    expect(confirm().props("question")).toBe("Autumn 2025 holds no teams. Removing it takes it off the strip.")
    confirm().vm.$emit("confirm")
    await flushPromises()
    expect(wrapper.emitted("removed")).toEqual([[autumn]])
    confirm().vm.$emit("update:open", false)
    await flushPromises()
    expect(confirm().props("open")).toBe(false)
  })
})
