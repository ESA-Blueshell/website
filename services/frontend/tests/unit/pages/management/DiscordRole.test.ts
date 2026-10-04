import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import DiscordRole from "@/pages/management/DiscordRole.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findListedTarget: vi.fn(),
  findCohortById: vi.fn(),
  reconcileTarget: vi.fn(),
  enforceTarget: vi.fn(),
  pushDrift: vi.fn(),
  removeDrift: vi.fn(),
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
const ledger = (targetMemberId: number, state: string, fields: Record<string, unknown> = {}) => ({
  targetMemberId, state, isUserDeleted: false, joinedAt: "2026-09-22T10:00:00Z", system: "DISCORD", ...fields,
})
const members = (fields: Record<string, unknown> = {}) => ({
  id: 4, label: "Members", category: "MEMBERS", type: "CURRENT_MEMBERS", orphaned: false,
  mappings: [{targetId: 40, system: "DISCORD", kind: "ROLE", externalId: "500", label: "Member", path: [], folderKnown: true, enforced: false, runs: []}],
  members: [
    ledger(1, "DESIRED", {userId: 11, userFullName: "Lars Mulder"}),
    ledger(2, "DESIRED", {userId: 12, userFullName: "Noor Hendriks", unreachable: true}),
    ledger(3, "STRANGER", {userId: 13, userFullName: "Jesse Bakker", externalUserId: "d3"}),
    ledger(4, "STRANGER", {externalUserId: "d4", externalLabel: "pixelsam"}),
    ledger(5, "VERIFIED", {userId: 15, userFullName: "In Step", syncedAt: "x"}),
  ],
  resolutions: [{system: "DISCORD", action: "PUSH", personName: "Kim Vos", resolvedByName: "Alice", resolvedAt: "2026-09-22T14:02:00Z"}],
  ...fields,
})
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
    api.findCohortById.mockResolvedValue({status: 200, data: members()})
    api.reconcileTarget.mockResolvedValue({status: 202, data: undefined})
    api.enforceTarget.mockResolvedValue({status: 200, data: {}})
    api.pushDrift.mockResolvedValue({status: 200, data: {resolved: 1}})
    api.removeDrift.mockResolvedValue({status: 200, data: {resolved: 1}})
    mockStore.getters.isAdmin = false
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
    expect(wrapper.get('[data-testid="discord-opening-differs-1"]').text()).toContain("On Discord read only, on the site read and write.")
    expect(wrapper.get('[data-testid="discord-opening-set-1"]').text()).toBe("Set it on Discord")
    expect(wrapper.get('[data-testid="discord-opening-set-3"]').text()).toBe("Set it on the site")
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

  // The page holds more than one dialog, so the create dialog is found by its own test id.
  const createDialog = (wrapper: VueWrapper) =>
    wrapper.findAllComponents({name: "ModalDialog"}).find((one) => one.props("testid") === "discord-create-dialog")!

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
    expect(createDialog(wrapper).props("open")).toBe(false)
    expect(api.listCataloguedChannels).toHaveBeenCalledTimes(2)

    await wrapper.get('[data-testid="discord-create-channel"]').trigger("click")
    await settle()
    expect(createDialog(wrapper).props("open")).toBe(true)
    createDialog(wrapper).vm.$emit("update:open", false)
    await settle()
    expect(createDialog(wrapper).props("open")).toBe(false)
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
    expect(empty.get('[data-testid="discord-role-opens-nothing"]').text()).toBe("The role has access to no channel yet.")
    expect(empty.text()).toContain("Made by hand on Discord")
    await empty.get('[data-testid="discord-create-channel"]').trigger("click")
    await settle()
    await inDialog("discord-create-confirm").trigger("click")
    expect(api.createRoleChannel).not.toHaveBeenCalled()
  })

  it("lists the role's drift in its own words: add and remove the role, and link an account where no Discord is linked", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-role-holders"]').text()).toContain("1 in step")
    expect(wrapper.get('[data-testid="discord-role-holders"]').text()).toContain("1 missing · 2 extra · 1 with no Discord linked")
    expect(wrapper.get('[data-testid="discord-role-drift-row-1"]').text()).toContain("In Members since")
    expect(wrapper.get('[data-testid="discord-role-drift-row-1"]').text()).toContain("and without the role")
    expect(wrapper.get('[data-testid="discord-role-drift-push-1"]').text()).toBe("Add the role")
    const unlinked = wrapper.get('[data-testid="discord-role-drift-row-2"]')
    expect(unlinked.text()).toContain("No Discord linked")
    expect(unlinked.find('[data-testid="discord-role-drift-push-2"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="discord-role-drift-link-account-2"]').attributes("to")).toBe("/management/users/12")
    expect(wrapper.get('[data-testid="discord-role-drift-remove-3"]').text()).toBe("Remove the role")
    expect(wrapper.get('[data-testid="discord-role-drift-row-3"]').text()).toContain("Holds the role, and not in Members")
    expect(wrapper.get('[data-testid="discord-role-drift-row-4"]').text()).toContain("Unknown member")
    expect(wrapper.get('[data-testid="discord-role-drift-row-4"]').text()).toContain("No account here has this Discord linked")
    expect(wrapper.get('[data-testid="discord-role-drift-resolved"]').text()).toContain("Added the role")
    expect(wrapper.find('[data-testid="discord-role-enforce"]').exists()).toBe(false)

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "noor")
    await settle()
    expect(wrapper.find('[data-testid="discord-role-drift-row-1"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="discord-role-drift-row-2"]').text()).toContain("Noor Hendriks")
  })

  it("adds the role to the ticked people, reconciles and lets an admin enforce", async () => {
    mockStore.getters.isAdmin = true
    const wrapper = await mount()

    await wrapper.get('[data-testid="discord-role-drift-select-1"]').trigger("change")
    await settle()
    expect(wrapper.get('[data-testid="discord-role-drift-bulk-push"]').text()).toBe("Add the role: 1")
    await wrapper.get('[data-testid="discord-role-drift-bulk-push"]').trigger("click")
    await settle()
    await new DOMWrapper(document.body.querySelector('[data-testid="discord-role-drift-plan-confirm"]')!).trigger("click")
    await settle()
    expect(api.pushDrift).toHaveBeenCalledWith({path: {id: 4, targetId: 40}, body: {userIds: [11]}})

    await wrapper.get('[data-testid="discord-role-reconcile"]').trigger("click")
    await settle()
    expect(api.reconcileTarget).toHaveBeenCalled()

    expect(wrapper.get('[data-testid="discord-role-enforce"]').text()).toContain("Off.")
    await wrapper.get('[data-testid="discord-role-enforce"]').trigger("click")
    await settle()
    expect(api.enforceTarget).toHaveBeenCalledWith(expect.objectContaining({path: {id: 4, targetId: 40}}))

    api.enforceTarget.mockResolvedValueOnce({status: 409, error: {code: "Nope", message: "Enforcing is off for now."}, response: {status: 409}})
    api.findCohortById.mockResolvedValue({status: 200, data: members({mappings: [{...members().mappings[0], enforced: true}]})})
    const enforced = await mount()
    expect(enforced.get('[data-testid="discord-role-enforce"]').text()).toContain("Turn off")
    await enforced.get('[data-testid="discord-role-enforce"]').trigger("click")
    await settle()
    expect(api.enforceTarget).toHaveBeenLastCalledWith(expect.objectContaining({body: {enforced: false}}))
  })
})
