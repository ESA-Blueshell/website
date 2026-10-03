import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import GameAccessSection from "@/domains/discord/island/GameAccessSection.vue"
import {mountInApp, settle, unmountAll} from "../../../pages/helpers"

const api = vi.hoisted(() => ({findGameAccess: vi.fn(), setGameAccess: vi.fn(), archiveChannel: vi.fn()}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn()}}))
vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const policy = (everyone: string, members: string) => ({everyone, members})
const kept = policy("READ", "WRITE")
const channel = (id: string, name: string, actual = kept) => ({id, name, state: {kept, actual, differs: actual !== kept}})

describe("a game's Discord access", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(GameAccessSection, {props: {code: "VALO", testid: "access"}})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findGameAccess.mockResolvedValue({status: 200, data: {policy: kept, channels: [channel("1", "bs-valo", policy("WRITE", "WRITE")), channel("2", "blueshell-valorant")]}})
    api.setGameAccess.mockResolvedValue({status: 200, data: {policy: policy("HIDDEN", "WRITE"), channels: [channel("2", "blueshell-valorant")]}})
    api.archiveChannel.mockResolvedValue({status: 204, data: undefined})
  })

  afterEach(() => unmountAll(wrappers, "GameAccessSection"))

  it("shows the access every channel shares, says which channel Discord has otherwise, and sets them back only when asked", async () => {
    const wrapper = await mount()

    const choices = wrapper.findAllComponents({name: "SegmentedChoice"})
    expect(choices[0]!.props("modelValue")).toBe("READ")
    expect(choices[1]!.props("modelValue")).toBe("WRITE")
    expect(wrapper.get('[data-testid="access-differs-1"]').text()).toBe("On Discord everybody read and write, members read and write. Discord is left as it is.")
    expect(wrapper.find('[data-testid="access-differs-2"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="access-rewrite"]').text()).toBe("Set it back on Discord")
    expect(api.setGameAccess).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="access-rewrite"]').trigger("click")
    await settle()
    expect(api.setGameAccess).toHaveBeenCalledWith({path: {code: "VALO"}, body: kept})
  })

  it("writes a changed access for every channel, and says why where it could not", async () => {
    const wrapper = await mount()
    wrapper.findAllComponents({name: "SegmentedChoice"})[0]!.vm.$emit("update:modelValue", "HIDDEN")
    await settle()
    expect(api.setGameAccess).toHaveBeenCalledWith({path: {code: "VALO"}, body: policy("HIDDEN", "WRITE")})
    expect(wrapper.find('[data-testid="access-rewrite"]').exists()).toBe(false)

    api.setGameAccess.mockResolvedValueOnce({status: 503, error: {code: "DiscordUnreachable"}})
    wrapper.findAllComponents({name: "SegmentedChoice"})[1]!.vm.$emit("update:modelValue", "READ")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Discord cannot be reached now; try again in a moment.")
  })

  it("archives a channel, and says why where it could not", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="access-archive-2"]').trigger("click")
    await settle()
    expect(api.archiveChannel).toHaveBeenCalledWith({path: {id: "2"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "#blueshell-valorant is in the archive.")
    expect(api.findGameAccess).toHaveBeenCalledTimes(2)

    api.archiveChannel.mockResolvedValueOnce({status: 503, error: {code: "DiscordUnreachable"}})
    await wrapper.get('[data-testid="access-archive-2"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "Discord cannot be reached now; try again in a moment.")
  })

  it("draws nothing where Discord cannot be read, and says a game without channels gets the access on each one it gets", async () => {
    api.findGameAccess.mockResolvedValueOnce({status: 503, error: {}})
    expect((await mount()).find('[data-testid="access"]').exists()).toBe(false)
    api.findGameAccess.mockResolvedValueOnce({status: 200, data: {policy: kept, channels: []}})
    expect((await mount()).text()).toContain("The game has no channel yet")
  })
})
