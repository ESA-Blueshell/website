import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import DiscordRole from "@/pages/management/DiscordRole.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findListedTarget: vi.fn(),
  listRoleOpenings: vi.fn(),
  listCataloguedChannels: vi.fn(),
  setRoleOpening: vi.fn(),
  removeRoleOpening: vi.fn(),
  createRoleChannel: vi.fn(),
  archiveRoleChannel: vi.fn(),
}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn(), getters: {isAdmin: false} as Record<string, unknown>}}))

vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => ({params: {roleId: "500"}}),
}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const games = {channel: {id: "10", name: "Games", kind: "CATEGORY"}, kept: "WRITE", actual: "WRITE", differs: false}
const lounge = {channel: {id: "1", name: "members-lounge", kind: "TEXT", category: "Members"}, kept: "WRITE", actual: "READ", differs: true}
const voice = {channel: {id: "3", name: "Lounge", kind: "VOICE", category: "Voice"}, actual: "SPEAK", differs: true}
const catalogue = [
  {id: "10", name: "Games", kind: "CATEGORY", private: false, roleIds: ["500"]},
  {id: "11", name: "bs-valo", kind: "TEXT", category: "Games", private: false, roleIds: []},
  {id: "20", name: "Members", kind: "CATEGORY", private: true, roleIds: []},
  {id: "1", name: "members-lounge", kind: "TEXT", category: "Members", private: true, roleIds: ["500"]},
  {id: "2", name: "announcements", kind: "TEXT", category: "Members", private: true, roleIds: []},
]
const member = {externalId: "500", label: "Member", targetId: 1, cohortId: 4, cohortLabel: "Members", cohortType: "CURRENT_MEMBERS", enforced: false}

const inDialog = (testid: string) => new DOMWrapper(document.body.querySelector(`[data-testid="${testid}"]`)!)

describe("a Discord role's page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordRole)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }
  const picker = (wrapper: VueWrapper, prefix: string) =>
    wrapper.findAllComponents({name: "SearchPicker"}).find((one) => one.props("testidPrefix") === prefix)!

  beforeEach(() => {
    vi.clearAllMocks()
    api.findListedTarget.mockResolvedValue({status: 200, data: member})
    api.listRoleOpenings.mockResolvedValue({status: 200, data: [games, lounge, voice]})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: catalogue})
    api.setRoleOpening.mockResolvedValue({status: 200, data: [games, {...lounge, kept: "READ", differs: false}, voice]})
    api.removeRoleOpening.mockResolvedValue({status: 200, data: [games, voice]})
    api.createRoleChannel.mockResolvedValue({status: 200, data: [games, lounge, voice]})
    api.archiveRoleChannel.mockResolvedValue({status: 200, data: [games, voice]})
  })

  afterEach(() => unmountAll(wrappers, "DiscordRolePage"))

  it("says what fills the role and lists what it opens, each with its access and where Discord differs", async () => {
    const wrapper = await mount()

    expect(api.findListedTarget).toHaveBeenCalledWith({path: {system: "DISCORD", externalId: "500"}})
    expect(wrapper.text()).toContain("@Member")
    expect(wrapper.text()).toContain("Everyone in Members holds this role")
    expect(wrapper.get('[data-testid="discord-role-opens"]').text()).toContain("1 category, 2 channels")
    expect(wrapper.get('[data-testid="discord-opening-10"]').text()).toContain("Category · 1 channel")
    expect(picker(wrapper, "discord-opening-access-10").props("selectedKey")).toBe("WRITE")
    expect(wrapper.find('[data-testid="discord-opening-archive-10"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="discord-opening-differs-1"]').text()).toContain("On Discord read only, where the site keeps read and write.")
    expect(wrapper.get('[data-testid="discord-opening-set-1"]').text()).toBe("Set it on Discord")
    expect(wrapper.get('[data-testid="discord-opening-set-3"]').text()).toBe("Keep it on the site")
    expect(picker(wrapper, "discord-open-another").props("options").map((one: {key: string}) => one.key)).toEqual(["11", "20", "2"])
  })

  it("sets an access, sets Discord back to what the site keeps, removes and archives", async () => {
    const wrapper = await mount()

    picker(wrapper, "discord-opening-access-1").vm.$emit("pick", "READ")
    await settle()
    expect(api.setRoleOpening).toHaveBeenCalledWith({path: {roleId: "500", channelId: "1"}, body: {access: "READ"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "#members-lounge is set to read only.")
    expect(wrapper.find('[data-testid="discord-opening-differs-1"]').exists()).toBe(false)

    await wrapper.get('[data-testid="discord-opening-set-3"]').trigger("click")
    await settle()
    expect(api.setRoleOpening).toHaveBeenLastCalledWith({path: {roleId: "500", channelId: "3"}, body: {access: "SPEAK"}})

    await wrapper.get('[data-testid="discord-opening-remove-10"]').trigger("click")
    await settle()
    expect(api.removeRoleOpening).toHaveBeenCalledWith({path: {roleId: "500", channelId: "10"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Games is taken off the role.")

    api.listRoleOpenings.mockResolvedValue({status: 200, data: [lounge]})
    const again = await mount()
    await again.get('[data-testid="discord-opening-archive-1"]').trigger("click")
    await settle()
    expect(api.archiveRoleChannel).toHaveBeenCalledWith({path: {roleId: "500", channelId: "1"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "#members-lounge is in the archive.")
  })

  it("opens another channel or category at the access picked, and says why not when Discord refuses", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="discord-open-add"]').trigger("click")
    expect(api.setRoleOpening).not.toHaveBeenCalled()
    picker(wrapper, "discord-open-another").vm.$emit("pick", "20")
    picker(wrapper, "discord-open-access").vm.$emit("pick", "READ")
    await settle()
    await wrapper.get('[data-testid="discord-open-add"]').trigger("click")
    await settle()
    expect(api.setRoleOpening).toHaveBeenCalledWith({path: {roleId: "500", channelId: "20"}, body: {access: "READ"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Members is open to the role.")

    picker(wrapper, "discord-open-another").vm.$emit("pick", "2")
    api.setRoleOpening.mockResolvedValueOnce({status: 503, error: {code: "DiscordUnreachable"}, response: {status: 503}})
    await settle()
    await wrapper.get('[data-testid="discord-open-add"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Discord cannot be reached now; try again in a moment.")
    expect(picker(wrapper, "discord-open-another").props("selectedKey")).toBe("2")
  })

  it("makes a channel for the role under the category picked", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="discord-create-channel"]').trigger("click")
    await settle()
    expect(inDialog("discord-create-confirm").attributes("disabled")).toBeDefined()
    await new DOMWrapper(document.body.querySelector('[data-testid="discord-create-name"] input')!).setValue(" lounge-2 ")
    picker(wrapper, "discord-create-category-picker").vm.$emit("pick", "Members")
    await settle()
    await inDialog("discord-create-confirm").trigger("click")
    await settle()

    expect(api.createRoleChannel).toHaveBeenCalledWith({path: {roleId: "500"}, body: {name: "lounge-2", category: "Members", access: "WRITE"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "#lounge-2 is made under Members.")
    expect(wrapper.findAllComponents({name: "ModalDialog"})[0]!.props("open")).toBe(false)
    expect(api.listCataloguedChannels).toHaveBeenCalledTimes(2)

    await wrapper.get('[data-testid="discord-create-channel"]').trigger("click")
    wrapper.findAllComponents({name: "ModalDialog"})[0]!.vm.$emit("update:open", false)
    await settle()
    expect(wrapper.findAllComponents({name: "ModalDialog"})[0]!.props("open")).toBe(false)
  })

  it("says when Discord cannot be read, and words a role nothing fills", async () => {
    api.listRoleOpenings.mockResolvedValue({status: 503, error: {code: "DiscordUnreachable"}, response: {status: 503}})
    api.findListedTarget.mockResolvedValue({status: 200, data: {externalId: "500", label: "Gamers", enforced: false}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-role-unreadable"]').text()).toContain("Discord could not be read")
    expect(wrapper.text()).toContain("Nothing on the site fills this role")

    api.listRoleOpenings.mockResolvedValue({status: 200, data: []})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: []})
    const empty = await mount()
    expect(empty.get('[data-testid="discord-role-opens-nothing"]').text()).toBe("The role opens nothing yet.")
    expect(empty.text()).toContain("Made by hand on Discord")
    await empty.get('[data-testid="discord-create-channel"]').trigger("click")
    await settle()
    await inDialog("discord-create-confirm").trigger("click")
    expect(api.createRoleChannel).not.toHaveBeenCalled()
  })
})
