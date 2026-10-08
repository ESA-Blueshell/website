import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {h, type Ref, ref, type VNode} from "vue"
import type {Committee} from "@/domains/committees/adapters/committees"
import type {CasualGame} from "@/domains/games/adapters/games"
import {aCasualGame, aCommittee, anImage} from "../../../helpers/apiFixtures"
import GameEditor from "@/domains/games/components/GameEditor.vue"

const adapter = vi.hoisted(() => ({addCasualGame: vi.fn(), saveCasualGame: vi.fn(), storeGameBanner: vi.fn(), storeGameIcon: vi.fn()}))
vi.mock("@/domains/games/adapters/games", () => adapter)
const lists = vi.hoisted(() => ({refreshSharedLists: vi.fn()}))
vi.mock("@/utils/sharedLists", async importOriginal => ({
  ...(await importOriginal<typeof import("@/utils/sharedLists")>()),
  refreshSharedLists: lists.refreshSharedLists,
}))
// The list is made before each test, since a ref cannot be made inside a hoisted factory.
const committees = vi.hoisted(() => ({saveGameOrganisers: vi.fn(), refresh: vi.fn()} as {
  saveGameOrganisers: ReturnType<typeof vi.fn>, refresh: ReturnType<typeof vi.fn>, committees: Ref<Committee[]>,
}))
vi.mock("@/domains/committees", () => ({
  saveGameOrganisers: committees.saveGameOrganisers,
  useCommittees: () => ({committees: committees.committees, refresh: committees.refresh}),
}))
const esports = vi.hoisted(() => ({enterGameInSeason: vi.fn(), refresh: vi.fn()}))
vi.mock("@/domains/esports", () => ({enterGameInSeason: esports.enterGameInSeason}))

const passThrough = (name: string) => ({name, setup: (_: unknown, {slots}: {slots: Record<string, (() => VNode[]) | undefined>}) =>
  () => h("div", [slots["actions"]?.(), slots["default"]?.(), slots["footer"]?.(), slots["preview"]?.()])})
const picker = (name: string) => ({name, props: ["modelValue", "testid"], emits: ["update:modelValue"], template: "<div />"})
const dialog = (name: string) => ({name, props: ["open", "game"], emits: ["update:open", "saved", "removed"], template: "<div />"})
const stubs = {
  EditPage: {...passThrough("EditPage"), props: ["title", "eyebrow", "back", "testid", "accent"]},
  PreviewFrame: passThrough("PreviewFrame"),
  RecordHead: {name: "RecordHead", props: ["title", "accent", "archived"], template: "<div data-testid=casual-head><slot /><slot name=\"facts\" /></div>"},
  EsportsGameHead: {name: "EsportsGameHead", props: ["name", "accent", "intro", "channels"], template: "<div />"},
  SliceBand: {name: "SliceBand", props: ["items", "accent"], template: "<div />"},
  ArtCells: {name: "ArtCells", props: ["cells"], template: "<div />"},
  ImagePicker: {name: "ImagePicker", props: ["picture", "store", "testid"], emits: ["update:picture"], template: "<div />"},
  GameChannelPicker: picker("GameChannelPicker"),
  GameOrganisersPicker: picker("GameOrganisersPicker"),
  ArchiveGameDialog: dialog("ArchiveGameDialog"),
  RemoveGameDialog: dialog("RemoveGameDialog"),
  CutButton: {props: ["href", "testid"], template: "<a :href='href' :data-testid='testid'><slot /></a>"},
}

const chess = aCasualGame({
  code: "CHESS", name: "Chess", slug: "chess", accent: "#b58863", intro: "Blitz", sortIndex: 4, inCompetition: true,
  banner: anImage({url: "/b.webp", path: "b.webp", width: null, height: null, renditions: []}), icon: null,
  channels: [{id: "900", guildId: "324", name: "chess"}],
})

const field = (wrapper: ReturnType<typeof mountEditor>, id: string) => wrapper.get(`[data-testid=game-edit-${id}] input`)
const write = (wrapper: ReturnType<typeof mountEditor>, id: string, value: unknown) =>
  wrapper.findAllComponents({name: "FormControl"})
    .find(one => one.element.parentElement?.dataset.testid === `game-edit-${id}`)!.vm.$emit("update:modelValue", value)

const mountEditor = (game: CasualGame | null, area: "casual" | "competition" = "casual", enterIn: number | null = null) =>
  mount(GameEditor, {props: {game, area, enterIn, back: `/${area}`}, global: {stubs}})

beforeEach(() => {
  Object.values(adapter).forEach(one => one.mockReset())
  lists.refreshSharedLists.mockReset().mockResolvedValue(undefined)
  esports.enterGameInSeason.mockReset()
  committees.saveGameOrganisers.mockReset()
  committees.refresh.mockReset().mockResolvedValue([])
  committees.committees = ref([aCommittee({id: 1, name: "LegaCie", gameCodes: ["CHESS"]}), aCommittee({id: 2, name: "LanCie", gameCodes: []})])
})

describe("the game edit page", () => {
  it("adds a game from the casual pages, its address following its name, previewed as the casual head and cell", async () => {
    adapter.addCasualGame.mockResolvedValue({ok: true, saved: chess})
    const wrapper = mountEditor(null)
    expect(wrapper.get("[data-testid=casual-head]").text()).toContain("None yet")

    await field(wrapper, "name").setValue("Rocket League!")
    expect((field(wrapper, "slug").element as HTMLInputElement).value).toBe("rocket-league")
    await field(wrapper, "slug").setValue("rl")
    await field(wrapper, "name").setValue("Rocket League")
    await wrapper.get("[data-testid=game-edit-accent] input:not([type=color])").setValue("#1183d6")
    expect(wrapper.getComponent(stubs.RecordHead).props("title")).toBe("Rocket League")
    expect(wrapper.getComponent(stubs.ArtCells).props("cells")[0]).toMatchObject({title: "Rocket League", accent: "#1183d6"})
    expect(wrapper.getComponent(stubs.EsportsGameHead).props("name")).toBe("Rocket League")
    const areas = wrapper.findAll("[data-testid^=game-edit-preview-]").map(one => one.attributes("data-testid"))
    expect(areas).toEqual(["game-edit-preview-casual", "game-edit-preview-competition"])
    expect(wrapper.get("[data-testid=game-edit-save]").text()).toBe("Add the game")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.addCasualGame).toHaveBeenCalledWith({
      name: "Rocket League", slug: "rl", intro: null, accent: "#1183d6", banner: null, icon: null, channels: [], sortIndex: null,
      competitionIntro: null, esportsChannels: [],
    })
    expect(committees.saveGameOrganisers).not.toHaveBeenCalled()
    expect(esports.enterGameInSeason).not.toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toEqual([[chess]])
  })

  it("adds a game from the competition pages and enters it in the season it came from, previewed as the competition head and slice", async () => {
    adapter.addCasualGame.mockResolvedValue({ok: true, saved: chess})
    esports.enterGameInSeason.mockResolvedValueOnce({ok: false, reason: "Refused."}).mockResolvedValueOnce({ok: true})
    const wrapper = mountEditor(null, "competition", 4)

    await field(wrapper, "name").setValue("Chess")
    expect(wrapper.getComponent(stubs.EsportsGameHead).props("name")).toBe("Chess")
    expect(wrapper.getComponent(stubs.SliceBand).props("items")[0]).toMatchObject({title: "Chess", accent: "var(--color-brand)"})
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=game-edit-failure]").text()).toBe("Chess is recorded, but it could not be entered in the season. Refused. Enter it from the season itself.")

    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(esports.enterGameInSeason).toHaveBeenLastCalledWith(4, "CHESS")
    expect(wrapper.emitted("saved")).toHaveLength(1)
  })

  it("corrects a game, its channels, order and committees, keeping what was typed when refused", async () => {
    adapter.saveCasualGame.mockResolvedValueOnce({ok: false, reason: "The address 'chess' is already used by Go."}).mockResolvedValue({ok: true, saved: chess})
    committees.saveGameOrganisers.mockResolvedValueOnce({ok: false, reason: "Refused."}).mockResolvedValue({ok: true})
    const wrapper = mountEditor(chess)

    expect(wrapper.get("[data-testid=game-edit-see]").attributes("href")).toBe("/casual/chess")
    expect(wrapper.getComponent(stubs.GameOrganisersPicker).props("modelValue")).toEqual([1])
    expect(wrapper.get("[data-testid=game-edit-see-competition]").attributes("href")).toBe("/competition/chess")
    expect(wrapper.get("[data-testid=casual-head]").text()).toContain("#chess")
    expect(wrapper.get("[data-testid=casual-head]").text()).toContain("LegaCie")
    await field(wrapper, "order").setValue("2")
    write(wrapper, "intro", "Rapid on Thursdays")
    await flushPromises()
    expect(wrapper.getComponent(stubs.EsportsGameHead).props("intro")).toBe("Rapid on Thursdays")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=game-edit-failure]").text()).toBe("The address 'chess' is already used by Go.")
    expect(committees.saveGameOrganisers).not.toHaveBeenCalled()

    wrapper.getComponent(stubs.GameOrganisersPicker).vm.$emit("update:modelValue", [1, 2])
    wrapper.getComponent(stubs.GameChannelPicker).vm.$emit("update:modelValue", [])
    await flushPromises()
    expect(wrapper.get("[data-testid=casual-head]").text()).toContain("LegaCie · LanCie")
    expect(wrapper.get("[data-testid=casual-head]").text()).not.toContain("#chess")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=game-edit-failure]").text()).toBe("Chess is saved, but its committees are not. Refused.")

    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(adapter.saveCasualGame).toHaveBeenLastCalledWith("CHESS", expect.objectContaining({sortIndex: 2, intro: "Rapid on Thursdays", channels: [], banner: "b.webp"}))
    expect(committees.saveGameOrganisers).toHaveBeenLastCalledWith("CHESS", [1, 2])
    expect(lists.refreshSharedLists).toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toEqual([[chess]])
  })

  it("refuses to save a highlight colour that is not one, and previews the island's own meanwhile", async () => {
    const wrapper = mountEditor(chess)

    await wrapper.get("[data-testid=game-edit-accent] input:not([type=color])").setValue("blue")

    expect(wrapper.text()).toContain("Write a colour as # and six hex digits.")
    expect(wrapper.getComponent(stubs.EsportsGameHead).props("accent")).toBe("var(--color-brand)")
    expect(wrapper.get("[data-testid=game-edit-save]").attributes("disabled")).toBe("true")
    await wrapper.get("form").trigger("submit")
    expect(adapter.saveCasualGame).not.toHaveBeenCalled()

    await wrapper.get("[data-testid=game-edit-accent-field-swatch]").setValue("#1183d6")
    expect(wrapper.getComponent(stubs.EsportsGameHead).props("accent")).toBe("#1183d6")
    expect(wrapper.get("[data-testid=game-edit-save]").attributes("disabled")).toBe("false")
  })

  it("writes the competition pages' own intro and esports channels, previewed with the casual intro as fallback", async () => {
    adapter.saveCasualGame.mockResolvedValue({ok: true, saved: chess})
    const wrapper = mountEditor({...chess, competitionIntro: null, esportsChannels: []})
    const head = () => wrapper.getComponent(stubs.EsportsGameHead)
    const esports = wrapper.findAllComponents(stubs.GameChannelPicker).find(one => one.props("testid") === "game-edit-esports-channels")!

    expect(head().props("intro")).toBe("Blitz")
    write(wrapper, "competition-intro", "Rated only")
    esports.vm.$emit("update:modelValue", [{id: "7", guildId: "324", name: "chess-esports"}])
    await flushPromises()

    expect(head().props("intro")).toBe("Rated only")
    expect(head().props("channels")).toEqual([{id: "7", guildId: "324", name: "chess-esports"}])
    expect(wrapper.getComponent(stubs.SliceBand).props("items")[0].meta).toBe("#chess-esports")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(adapter.saveCasualGame).toHaveBeenCalledWith("CHESS", expect.objectContaining({
      competitionIntro: "Rated only", esportsChannels: [{id: "7", guildId: "324", name: "chess-esports"}],
    }))
  })

  it("takes an emptied order as last", async () => {
    adapter.saveCasualGame.mockResolvedValue({ok: true, saved: chess})
    const wrapper = mountEditor(chess, "competition")

    expect(wrapper.getComponent(stubs.EditPage).props("back")).toEqual({to: "/competition", label: "Competition"})
    await field(wrapper, "order").setValue("")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveCasualGame).toHaveBeenCalledWith("CHESS", expect.objectContaining({sortIndex: null}))
  })

  it("stores each picture as its kind, and never saves without a name", async () => {
    const wrapper = mountEditor(null)
    const [banner, icon] = wrapper.findAllComponents(stubs.ImagePicker)
    if (!banner || !icon) throw new Error("the editor draws a banner picker and an icon picker")
    const file = new File(["x"], "a.png")

    await banner.props("store")(file)
    await icon.props("store")(file)
    banner.vm.$emit("update:picture", {path: "b.webp", url: "/b.webp", renditions: []})
    icon.vm.$emit("update:picture", {path: "i.webp", url: "/i.webp", renditions: []})
    await wrapper.get("form").trigger("submit")

    expect(adapter.storeGameBanner).toHaveBeenCalledWith(file)
    expect(adapter.storeGameIcon).toHaveBeenCalledWith(file)
    expect(adapter.addCasualGame).not.toHaveBeenCalled()
    expect(wrapper.get("[data-testid=game-edit-save]").attributes("disabled")).toBeDefined()
  })

  it("archives, brings back and removes a game through their confirmations, and leaves on Cancel", async () => {
    const wrapper = mountEditor({...chess, archived: true})

    expect(wrapper.get("[data-testid=game-edit-archive]").text()).toBe("Bring back")
    await wrapper.get("[data-testid=game-edit-archive]").trigger("click")
    expect(wrapper.getComponent(stubs.ArchiveGameDialog).props("open")).toBe(true)
    wrapper.getComponent(stubs.ArchiveGameDialog).vm.$emit("saved")
    await flushPromises()
    expect(wrapper.emitted("saved")).toHaveLength(1)
    wrapper.getComponent(stubs.ArchiveGameDialog).vm.$emit("update:open", false)
    await flushPromises()
    expect(wrapper.getComponent(stubs.ArchiveGameDialog).props("open")).toBe(false)

    await wrapper.get("[data-testid=game-edit-remove]").trigger("click")
    wrapper.getComponent(stubs.RemoveGameDialog).vm.$emit("removed")
    await flushPromises()
    expect(wrapper.emitted("removed")).toHaveLength(1)
    wrapper.getComponent(stubs.RemoveGameDialog).vm.$emit("update:open", false)
    await flushPromises()
    expect(wrapper.findComponent(stubs.RemoveGameDialog).exists()).toBe(false)

    expect(mountEditor(chess).find("[data-testid=game-edit-remove]").exists()).toBe(false)
    await wrapper.get("[data-testid=game-edit-cancel]").trigger("click")
    expect(wrapper.emitted("cancel")).toHaveLength(1)
  })
})
