import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordChannel from "@/pages/management/DiscordChannel.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  listCataloguedChannels: vi.fn(),
  findTargetOverview: vi.fn(),
  listRoleOpenings: vi.fn(),
  setRoleOpening: vi.fn(),
  removeRoleOpening: vi.fn(),
}))
const {mockStore, here} = vi.hoisted(() => ({mockStore: {commit: vi.fn(), getters: {}}, here: {params: {channelId: "1"} as Record<string, string>}}))

vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => here,
  useRouter: () => ({push: vi.fn()}),
}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const text = {id: "1", name: "sitecie", kind: "TEXT", category: "Committees", private: true, roleIds: ["900"]}
const voice = {id: "2", name: "Lounge", kind: "VOICE", category: "Archive", private: false, roleIds: []}
const lists = [
  {externalId: "900", label: "Sitecie", cohortLabel: "Sitecie", enforced: false},
  {externalId: "901", label: "Board", enforced: false},
]

describe("one Discord channel", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordChannel)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    here.params = {channelId: "1"}
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: [text, voice]})
    api.findTargetOverview.mockResolvedValue({status: 200, data: {lists, missing: []}})
    api.listRoleOpenings.mockResolvedValue({status: 200, data: [{channel: text, kept: "READ", actual: "READ", differs: false}]})
    api.setRoleOpening.mockResolvedValue({status: 200, data: []})
    api.removeRoleOpening.mockResolvedValue({status: 200, data: []})
  })

  afterEach(() => unmountAll(wrappers, "DiscordChannelPage"))

  it("lists the roles with access and what each may do, and changes or removes one from the channel's side", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-channel-page-head"]').text()).toContain("#sitecie")
    expect(wrapper.findComponent({name: "FactList"}).text()).toContain("Private text")
    const row = wrapper.get('[data-testid="discord-channel-role-900"]')
    expect(row.text()).toContain("@Sitecie")
    expect(row.text()).toContain("Follows Sitecie")
    expect(row.get("a").attributes("to")).toBe("/management/platforms/discord/roles/900")
    const pickers = () => wrapper.findAllComponents({name: "SearchPicker"})
    expect(pickers()[0]!.props("selectedKey")).toBe("READ")
    expect(pickers()[0]!.props("options").map((one: {key: string}) => one.key)).toEqual(["WRITE", "READ"])

    pickers()[0]!.vm.$emit("pick", "WRITE")
    await settle()
    expect(api.setRoleOpening).toHaveBeenCalledWith({path: {roleId: "900", channelId: "1"}, body: {access: "WRITE"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "@Sitecie is set to read and write.")

    await wrapper.get('[data-testid="discord-channel-remove-900"]').trigger("click")
    await settle()
    expect(api.removeRoleOpening).toHaveBeenCalledWith({path: {roleId: "900", channelId: "1"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "@Sitecie no longer has access to #sitecie.")
  })

  it("gives another role access at the access picked, and says why when Discord refuses", async () => {
    const wrapper = await mount()
    const pickers = () => wrapper.findAllComponents({name: "SearchPicker"})
    const add = () => pickers().find((one) => one.props("testidPrefix") === "discord-channel-add")!

    expect(add().props("options")).toEqual([{key: "901", label: "@Board", note: undefined}])
    await wrapper.get('[data-testid="discord-channel-add-go"]').trigger("click")
    expect(api.setRoleOpening).not.toHaveBeenCalled()

    add().vm.$emit("pick", "901")
    pickers().find((one) => one.props("testidPrefix") === "discord-channel-add-access")!.vm.$emit("pick", "READ")
    await settle()
    await wrapper.get('[data-testid="discord-channel-add-go"]').trigger("click")
    await settle()
    expect(api.setRoleOpening).toHaveBeenCalledWith({path: {roleId: "901", channelId: "1"}, body: {access: "READ"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "@Board has access to #sitecie.")

    api.setRoleOpening.mockResolvedValue({status: 502, error: {code: "TargetSystemRefused", reason: "The bot may not change sitecie."}})
    add().vm.$emit("pick", "901")
    await settle()
    await wrapper.get('[data-testid="discord-channel-add-go"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "Discord refused it. The bot may not change sitecie.")
  })

  it("offers a voice channel only joining and speaking, says when no role has access, and when the channel is not there", async () => {
    here.params = {channelId: "2"}
    const voiced = await mount()

    expect(voiced.get('[data-testid="discord-channel-page-head-eyebrow"]').text()).toContain("archived")
    expect(voiced.get('[data-testid="discord-channel-no-roles"]').text()).toContain("No role has access")
    expect(voiced.findAllComponents({name: "SearchPicker"}).at(-1)!.props("options")).toEqual([{key: "SPEAK", label: "Join and speak"}])
    voiced.findAllComponents({name: "SearchPicker"})[0]!.vm.$emit("pick", "900")
    await settle()
    await voiced.get('[data-testid="discord-channel-add-go"]').trigger("click")
    await settle()
    expect(api.setRoleOpening).toHaveBeenCalledWith({path: {roleId: "900", channelId: "2"}, body: {access: "SPEAK"}})

    here.params = {channelId: "404"}
    api.findTargetOverview.mockResolvedValue({status: 503, error: null})
    expect((await mount()).get('[data-testid="discord-channel-missing"]').text()).toContain("no such channel")
  })
})
