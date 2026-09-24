import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ArchiveGameDialog from "@/domains/games/island/ArchiveGameDialog.vue"
import RemoveGameDialog from "@/domains/games/island/RemoveGameDialog.vue"

const adapter = vi.hoisted(() => ({setGameArchived: vi.fn(), loadGameHoldings: vi.fn(), removeCasualGame: vi.fn()}))
vi.mock("@/domains/games/adapters/games", () => adapter)

const ModalDialog = {name: "ModalDialog", props: ["open", "title", "testid"], emits: ["update:open"], template: "<div><slot /><slot name=\"footer\" /></div>"}
const ConfirmDialog = {name: "ConfirmDialog", props: ["open", "title", "question", "confirmLabel", "workingLabel", "failure", "working", "testid"], emits: ["confirm", "update:open"], template: "<div />"}

const chess = {code: "CHESS", name: "Chess", slug: "chess", accent: null, intro: null, sortIndex: 1, archived: false, inCompetition: false, banner: null, icon: null}
const archived = {...chess, archived: true}

beforeEach(() => Object.values(adapter).forEach(one => one.mockReset()))

describe("archiving a game", () => {
  it("says what archiving does, and does it once confirmed", async () => {
    adapter.setGameArchived.mockResolvedValue({ok: true, game: archived})
    const wrapper = mount(ArchiveGameDialog, {props: {open: true, game: chess}, global: {stubs: {ConfirmDialog}}})
    const confirm = wrapper.getComponent(ConfirmDialog)

    expect(confirm.props("title")).toBe("Archive Chess?")
    expect(confirm.props("question")).toContain("joins the games we used to play")
    confirm.vm.$emit("confirm")
    confirm.vm.$emit("confirm")
    await flushPromises()

    expect(adapter.setGameArchived).toHaveBeenCalledTimes(1)
    expect(adapter.setGameArchived).toHaveBeenCalledWith("CHESS", true)
    expect(wrapper.emitted("saved")).toEqual([[archived]])
  })

  it("brings an archived game back, and says why when it could not", async () => {
    adapter.setGameArchived.mockResolvedValue({ok: false, reason: "The game could not be brought back."})
    const wrapper = mount(ArchiveGameDialog, {props: {open: false, game: archived}, global: {stubs: {ConfirmDialog}}})
    await wrapper.setProps({open: true})
    const confirm = wrapper.getComponent(ConfirmDialog)

    expect(confirm.props("confirmLabel")).toBe("Bring it back")
    expect(confirm.props("question")).toContain("goes back on the reel")
    confirm.vm.$emit("confirm")
    await flushPromises()

    expect(adapter.setGameArchived).toHaveBeenCalledWith("CHESS", false)
    expect(confirm.props("failure")).toBe("The game could not be brought back.")
    confirm.vm.$emit("update:open", false)
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })
})

describe("removing a game", () => {
  const mountRemove = () => mount(RemoveGameDialog, {props: {open: true, game: archived}, global: {stubs: {ModalDialog}}})

  it("says what the removal touches, then asks for the name typed out before it removes", async () => {
    adapter.loadGameHoldings.mockResolvedValue({channels: 2, committees: 1, events: 3, teams: 0, people: 0})
    adapter.removeCasualGame.mockResolvedValue({ok: true})
    const wrapper = mountRemove()
    expect(wrapper.find("[data-testid=remove-game-reading]").exists()).toBe(true)
    await flushPromises()

    expect(wrapper.get("[data-testid=remove-game-touches]").text())
      .toBe("Removing takes Chess off every page, list and picker. It is linked to 2 channels, 1 committee and 3 events, which stop naming it.")
    await wrapper.get("[data-testid=remove-game-next]").trigger("click")
    await wrapper.get("[data-testid=remove-game-name]").setValue("Ches")
    expect(wrapper.get("[data-testid=remove-game-confirm]").attributes("disabled")).toBeDefined()
    await wrapper.get("form").trigger("submit")
    expect(adapter.removeCasualGame).not.toHaveBeenCalled()

    await wrapper.get("[data-testid=remove-game-name]").setValue("Chess")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(adapter.removeCasualGame).toHaveBeenCalledWith("CHESS")
    expect(wrapper.emitted("removed")).toEqual([[archived]])
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })

  it("refuses to offer removal for a game with teams, and says so", async () => {
    adapter.loadGameHoldings.mockResolvedValue({channels: 0, committees: 0, events: 0, teams: 1, people: 1})
    const wrapper = mountRemove()
    await flushPromises()

    expect(wrapper.get("[data-testid=remove-game-touches]").text()).toContain("holds 1 team and 1 person in competition, so it cannot be removed")
    expect(wrapper.find("[data-testid=remove-game-next]").exists()).toBe(false)
  })

  it("puts no question on a guess, and says why a removal was refused", async () => {
    adapter.loadGameHoldings.mockResolvedValueOnce(null).mockResolvedValueOnce({channels: 1, committees: 0, events: 0, teams: 0, people: 0})
    adapter.removeCasualGame.mockResolvedValue({ok: false, reason: "The game could not be removed."})
    const wrapper = mountRemove()
    await flushPromises()
    expect(wrapper.get("[data-testid=remove-game-failure]").text()).toContain("could not be read")
    expect(wrapper.find("[data-testid=remove-game-next]").exists()).toBe(false)

    await wrapper.setProps({open: false})
    await wrapper.setProps({open: true})
    await flushPromises()
    await wrapper.get("[data-testid=remove-game-next]").trigger("click")
    await wrapper.get("[data-testid=remove-game-name]").setValue("Chess")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(wrapper.get("[data-testid=remove-game-failure]").text()).toBe("The game could not be removed.")
    await wrapper.get("[data-testid=remove-game-cancel]").trigger("click")
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)
    expect(wrapper.emitted("update:open")).toEqual([[false], [false]])
  })
})
