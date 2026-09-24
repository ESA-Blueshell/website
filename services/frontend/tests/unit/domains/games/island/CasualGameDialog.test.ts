import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import CasualGameDialog from "@/domains/games/island/CasualGameDialog.vue"

const adapter = vi.hoisted(() => ({addCasualGame: vi.fn(), saveCasualGame: vi.fn(), storeGamePicture: vi.fn()}))
vi.mock("@/domains/games/adapters/games", () => adapter)

const ModalDialog = {name: "ModalDialog", props: ["open", "title", "testid", "accent"], emits: ["update:open"], template: "<div><slot /><slot name=\"footer\" /></div>"}
const ImagePicker = {name: "ImagePicker", props: ["label", "picture", "store", "testid", "shape", "mayBeVector"], emits: ["update:picture"], template: "<div />"}

const chess = {code: "CHESS", name: "Chess", slug: "chess", accent: "#b58863", intro: "Blitz", sortIndex: 1, archived: false, inCompetition: false,
  banner: {url: "/b.webp", path: "b.webp", renditions: []}, icon: null}

const mountDialog = (game: typeof chess | null) =>
  mount(CasualGameDialog, {props: {open: true, game}, global: {stubs: {ModalDialog, ImagePicker}}})

beforeEach(() => Object.values(adapter).forEach(one => one.mockReset()))

describe("the game dialog", () => {
  it("adds a game, its address following its name until somebody types one", async () => {
    adapter.addCasualGame.mockResolvedValue({ok: true, game: chess})
    const wrapper = mountDialog(null)

    await wrapper.get("[data-testid=casual-game-dialog-name]").setValue("Rocket League!")
    expect((wrapper.get("[data-testid=casual-game-dialog-slug]").element as HTMLInputElement).value).toBe("rocket-league")
    await wrapper.get("[data-testid=casual-game-dialog-slug]").setValue("rl")
    await wrapper.get("[data-testid=casual-game-dialog-name]").setValue("Rocket League")
    await wrapper.get("[data-testid=casual-game-dialog-intro]").setValue("  ")
    await wrapper.get("[data-testid=casual-game-dialog-accent]").setValue("#1183d6")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.addCasualGame).toHaveBeenCalledWith({name: "Rocket League", slug: "rl", intro: null, accent: "#1183d6", banner: null, icon: null})
    expect(wrapper.emitted("saved")).toEqual([[chess]])
    expect(wrapper.emitted("update:open")).toEqual([[false]])
    expect(wrapper.get("[data-testid=casual-game-dialog-save]").text()).toBe("Add the game")
  })

  it("corrects a game from what it holds, keeping what was typed when refused", async () => {
    adapter.saveCasualGame.mockResolvedValue({ok: false, reason: "The address 'chess' is already used by Go."})
    const wrapper = mountDialog(chess)

    expect((wrapper.get("[data-testid=casual-game-dialog-intro]").element as HTMLTextAreaElement).value).toBe("Blitz")
    await wrapper.get("[data-testid=casual-game-dialog-name]").setValue("Chess club")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.saveCasualGame).toHaveBeenCalledWith("CHESS", expect.objectContaining({name: "Chess club", slug: "chess", banner: "b.webp"}))
    expect(wrapper.get("[data-testid=casual-game-dialog-failure]").text()).toBe("The address 'chess' is already used by Go.")
    expect((wrapper.get("[data-testid=casual-game-dialog-name]").element as HTMLInputElement).value).toBe("Chess club")
    expect(wrapper.emitted("saved")).toBeUndefined()
  })

  it("stores each picture as its own kind, holds it until Save, and never saves without a name", async () => {
    const wrapper = mountDialog(null)
    const [banner, icon] = wrapper.findAllComponents(ImagePicker)
    const file = new File(["x"], "a.png")

    await banner.props("store")(file)
    await icon.props("store")(file)
    icon.vm.$emit("update:picture", {path: "i.webp", url: "/i.webp", renditions: []})
    banner.vm.$emit("update:picture", {path: "b.webp", url: "/b.webp", renditions: []})
    await wrapper.get("form").trigger("submit")

    expect(adapter.storeGamePicture.mock.calls.map(call => call[1])).toEqual(["GAME_BANNER", "GAME_ICON"])
    expect(adapter.addCasualGame).not.toHaveBeenCalled()
    expect(wrapper.get("[data-testid=casual-game-dialog-save]").attributes("disabled")).toBeDefined()
  })

  it("closes on Cancel", async () => {
    const wrapper = mountDialog(chess)

    await wrapper.get("[data-testid=casual-game-dialog-cancel]").trigger("click")
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)

    expect(wrapper.emitted("update:open")).toEqual([[false], [false]])
  })
})
