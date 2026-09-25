import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {h, ref} from "vue"
import GameEditor from "@/domains/games/components/GameEditor.vue"
import "@/plugins/validation"

const adapter = vi.hoisted(() => ({addCasualGame: vi.fn(), saveCasualGame: vi.fn(), storeGameBanner: vi.fn(), storeGameIcon: vi.fn()}))
vi.mock("@/domains/games/adapters/games", () => adapter)
const casual = vi.hoisted(() => ({refresh: vi.fn()}))
vi.mock("@/domains/games/useCasualGames", async importOriginal => ({
  ...(await importOriginal<typeof import("@/domains/games/useCasualGames")>()),
  useCasualGames: () => casual,
}))
const committees = vi.hoisted(() => ({saveGameOrganisers: vi.fn(), refresh: vi.fn(), committees: null as never}))
vi.mock("@/domains/committees", () => ({
  saveGameOrganisers: committees.saveGameOrganisers,
  useCommittees: () => ({committees: committees.committees, refresh: committees.refresh}),
}))
const esports = vi.hoisted(() => ({enterGameInSeason: vi.fn(), refresh: vi.fn()}))
vi.mock("@/domains/esports", () => ({enterGameInSeason: esports.enterGameInSeason, forgetCompetitionReads: vi.fn(), useGames: () => ({refresh: esports.refresh})}))

const passThrough = (name: string) => ({name, setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) =>
  () => h("div", [slots["actions"]?.(), slots["default"]?.(), slots["footer"]?.(), slots["preview"]?.()])})
const picker = (name: string) => ({name, props: ["modelValue", "testid"], emits: ["update:modelValue"], template: "<div />"})
const dialog = (name: string) => ({name, props: ["open", "game"], emits: ["update:open", "saved", "removed"], template: "<div />"})
const stubs = {
  EditPage: {...passThrough("EditPage"), props: ["title", "eyebrow", "back", "testid", "accent"]},
  PreviewFrame: passThrough("PreviewFrame"),
  RecordHead: {name: "RecordHead", props: ["title", "accent", "archived"], template: "<div><slot /></div>"},
  EsportsGameHead: {name: "EsportsGameHead", props: ["name", "accent", "intro"], template: "<div />"},
  SliceBand: {name: "SliceBand", props: ["items", "accent"], template: "<div />"},
  ArtCells: {name: "ArtCells", props: ["cells"], template: "<div />"},
  ImagePicker: {name: "ImagePicker", props: ["picture", "store", "testid"], emits: ["update:picture"], template: "<div />"},
  GameChannelPicker: picker("GameChannelPicker"),
  GameOrganisersPicker: picker("GameOrganisersPicker"),
  ArchiveGameDialog: dialog("ArchiveGameDialog"),
  RemoveGameDialog: dialog("RemoveGameDialog"),
  CutButton: {props: ["href", "testid"], template: "<a :href='href' :data-testid='testid'><slot /></a>"},
}

const chess = {
  code: "CHESS", name: "Chess", slug: "chess", accent: "#b58863", intro: "Blitz", sortIndex: 4, archived: false, inCompetition: true,
  banner: {url: "/b.webp", path: "b.webp", renditions: []}, icon: null, channels: [{id: "900", guildId: "324", name: "chess"}],
}

const field = (wrapper: ReturnType<typeof mountEditor>, id: string) => wrapper.get(`[data-testid=game-edit-${id}] input`)
const write = (wrapper: ReturnType<typeof mountEditor>, name: string, value: unknown) =>
  wrapper.findAllComponents({name: "VvField"}).find(one => one.props("name") === name)!.vm.$emit("update:modelValue", value)

const mountEditor = (game: typeof chess | null, area: "casual" | "competition" = "casual", enterIn: number | null = null) =>
  mount(GameEditor, {props: {game, area, enterIn, back: `/${area}`}, global: {stubs}})

beforeEach(() => {
  Object.values(adapter).forEach(one => one.mockReset())
  casual.refresh.mockReset().mockResolvedValue([])
  esports.refresh.mockReset().mockResolvedValue([])
  esports.enterGameInSeason.mockReset()
  committees.saveGameOrganisers.mockReset()
  committees.refresh.mockReset().mockResolvedValue([])
  committees.committees = ref([{id: 1, name: "LegaCie", gameCodes: ["CHESS"]}, {id: 2, name: "LanCie", gameCodes: []}]) as never
})

describe("the game edit page", () => {
  it("adds a game from the casual pages, its address following its name, previewed as the casual head and cell", async () => {
    adapter.addCasualGame.mockResolvedValue({ok: true, game: chess})
    const wrapper = mountEditor(null)

    await field(wrapper, "name").setValue("Rocket League!")
    expect((field(wrapper, "slug").element as HTMLInputElement).value).toBe("rocket-league")
    await field(wrapper, "slug").setValue("rl")
    await field(wrapper, "name").setValue("Rocket League")
    await field(wrapper, "accent").setValue("#1183d6")
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
    })
    expect(committees.saveGameOrganisers).not.toHaveBeenCalled()
    expect(esports.enterGameInSeason).not.toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toEqual([[chess]])
  })

  it("adds a game from the competition pages and enters it in the season it came from, previewed as the competition head and slice", async () => {
    adapter.addCasualGame.mockResolvedValue({ok: true, game: chess})
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
    adapter.saveCasualGame.mockResolvedValueOnce({ok: false, reason: "The address 'chess' is already used by Go."}).mockResolvedValue({ok: true, game: chess})
    committees.saveGameOrganisers.mockResolvedValueOnce({ok: false, reason: "Refused."}).mockResolvedValue({ok: true})
    const wrapper = mountEditor(chess)

    expect(wrapper.get("[data-testid=game-edit-see]").attributes("href")).toBe("/casual/chess")
    expect(wrapper.getComponent(stubs.GameOrganisersPicker).props("modelValue")).toEqual([1])
    expect(wrapper.get("[data-testid=game-edit-see-competition]").attributes("href")).toBe("/competition/chess")
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
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=game-edit-failure]").text()).toBe("Chess is saved, but its committees are not. Refused.")

    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(adapter.saveCasualGame).toHaveBeenLastCalledWith("CHESS", expect.objectContaining({sortIndex: 2, intro: "Rapid on Thursdays", channels: [], banner: "b.webp"}))
    expect(committees.saveGameOrganisers).toHaveBeenLastCalledWith("CHESS", [1, 2])
    expect(casual.refresh).toHaveBeenCalled()
    expect(esports.refresh).toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toEqual([[chess]])
  })

  it("takes an emptied order as last", async () => {
    adapter.saveCasualGame.mockResolvedValue({ok: true, game: chess})
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
