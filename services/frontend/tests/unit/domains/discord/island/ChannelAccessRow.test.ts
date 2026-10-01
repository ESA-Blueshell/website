import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import ChannelAccessRow from "@/domains/discord/island/ChannelAccessRow.vue"
import {mountInApp, settle, unmountAll} from "../../../pages/helpers"

const api = vi.hoisted(() => ({findChannelAccess: vi.fn(), setChannelAccess: vi.fn()}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn()}}))
vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const policy = (everyone: string, members: string) => ({everyone, members})

describe("a game channel's access", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(ChannelAccessRow, {props: {channelId: "1", name: "bs-valo", testid: "access"}})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findChannelAccess.mockResolvedValue({status: 200, data: {kept: policy("READ", "WRITE"), actual: policy("WRITE", "WRITE"), differs: true}})
    api.setChannelAccess.mockResolvedValue({status: 200, data: {kept: policy("HIDDEN", "WRITE"), actual: policy("HIDDEN", "WRITE"), differs: false}})
  })

  afterEach(() => unmountAll(wrappers, "ChannelAccessRow"))

  it("shows what the site keeps, says where Discord differs, and sets it back only when asked", async () => {
    const wrapper = await mount()

    const choices = wrapper.findAllComponents({name: "SegmentedChoice"})
    expect(choices[0].props("modelValue")).toBe("READ")
    expect(wrapper.get('[data-testid="access-differs"]').text()).toContain("On Discord everybody read and write, members read and write, where the site keeps everybody read, members read and write")
    expect(api.setChannelAccess).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="access-rewrite"]').trigger("click")
    await settle()
    expect(api.setChannelAccess).toHaveBeenCalledWith({path: {id: "1"}, body: policy("READ", "WRITE")})
  })

  it("writes a changed access to Discord, and says why where it could not", async () => {
    const wrapper = await mount()
    wrapper.findAllComponents({name: "SegmentedChoice"})[0].vm.$emit("update:modelValue", "HIDDEN")
    await settle()
    expect(api.setChannelAccess).toHaveBeenCalledWith({path: {id: "1"}, body: policy("HIDDEN", "WRITE")})
    expect(wrapper.find('[data-testid="access-differs"]').exists()).toBe(false)

    api.setChannelAccess.mockResolvedValueOnce({status: 503, error: {code: "DiscordUnreachable"}})
    wrapper.findAllComponents({name: "SegmentedChoice"})[1].vm.$emit("update:modelValue", "READ")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Discord cannot be reached now; try again in a moment.")
  })

  it("draws nothing where Discord cannot be read, and shows Discord's own access where the site keeps none", async () => {
    api.findChannelAccess.mockResolvedValueOnce({status: 503, error: {}})
    expect((await mount()).find('[data-testid="access"]').exists()).toBe(false)
    api.findChannelAccess.mockResolvedValueOnce({status: 200, data: {actual: policy("HIDDEN", "READ"), differs: false}})
    const wrapper = await mount()
    expect(wrapper.findAllComponents({name: "SegmentedChoice"})[1].props("modelValue")).toBe("READ")
  })
})
