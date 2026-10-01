import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import DiscordPage from "@/pages/management/DiscordPage.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findTargetOverview: vi.fn(),
  createMissingTargets: vi.fn(),
  listCataloguedChannels: vi.fn(),
  listDiscordMatches: vi.fn(),
  adoptDiscordMatches: vi.fn(),
}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn(), getters: {isAdmin: false} as Record<string, unknown>}}))
const here = vi.hoisted(() => ({path: "/management/platforms/discord"}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => here,
}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const overview = {
  lists: [
    {externalId: "900", label: "Sitecie", targetId: 1, cohortId: 101, cohortLabel: "Sitecie", cohortType: "COMMITTEE_MEMBERS",
      missing: 0, extra: 2, unreachable: 2, enforced: false},
    {externalId: "950", label: "Gamers", enforced: false},
  ],
  missing: [{targetId: 3, cohortId: 103, cohortLabel: "Nintenco", cohortType: "COMMITTEE_MEMBERS", memberCount: 6, creating: false}],
}
const channels = [
  {id: "1", name: "sitecie", kind: "TEXT", category: "Committees", private: true, roleIds: ["900"]},
  {id: "2", name: "bs-valo", kind: "TEXT", category: "Games", private: false, roleIds: [], game: {code: "VALO", name: "Valorant", kind: "CASUAL"},
    access: {kept: {everyone: "READ", members: "WRITE"}, actual: {everyone: "WRITE", members: "WRITE"}, differs: true}},
  {id: "3", name: "old-lan", kind: "VOICE", category: "Archive", private: true, roleIds: []},
  {id: "4", name: "Committees", kind: "CATEGORY", private: true, roleIds: ["900"]},
]
const matches = [
  {key: "COMMITTEE_MEMBERS:5", label: "Lancie", type: "COMMITTEE_MEMBERS", roleId: "960", roleName: "Lancie",
    channels: [{id: "6", name: "lancie", kind: "TEXT", category: "Committees"}]},
  {key: "TEAM_PLAYERS:2", label: "Blueshell CS2", type: "TEAM_PLAYERS", roleId: "961", roleName: "Blueshell CS2", channels: []},
]

const inDialog = (testid: string) => new DOMWrapper(document.body.querySelector(`[data-testid="${testid}"]`)!)

describe("the Discord page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordPage)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    here.path = "/management/platforms/discord"
    api.findTargetOverview.mockResolvedValue({status: 200, data: overview})
    api.createMissingTargets.mockResolvedValue({status: 200, data: {queued: 1}})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: channels})
    api.listDiscordMatches.mockResolvedValue({status: 200, data: matches})
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 1}})
  })

  afterEach(() => unmountAll(wrappers, "DiscordPage"))

  it("lists the kept roles with their drift and what they open, and the roles kept by nobody folded", async () => {
    const wrapper = await mount()

    expect(api.findTargetOverview).toHaveBeenCalledWith({path: {system: "DISCORD"}})
    expect(wrapper.get('[data-testid="discord-role-900"]').text()).toContain("@Sitecie")
    expect(wrapper.get('[data-testid="discord-role-900"]').text()).toContain("Committee members · Sitecie")
    expect(wrapper.get('[data-testid="discord-role-state-900"]').text()).toBe("2 extra")
    expect(wrapper.get('[data-testid="discord-role-900"]').text()).toContain("1 category, 1 channel")
    expect(wrapper.get('[data-testid="discord-role-missing-3"]').text()).toContain("No role yet")
    expect(wrapper.get('[data-testid="discord-fact-drift"]').text()).toContain("2 people with no Discord linked")
    expect(wrapper.find('[data-testid="discord-role-950"]').exists()).toBe(false)

    await wrapper.get('[data-testid="discord-other-roles-toggle"]').trigger("click")
    expect(wrapper.get('[data-testid="discord-role-950"]').text()).toContain("@Gamers")
  })

  it("lists the channels by category with what they belong to, who gets in and where Discord differs", async () => {
    const roles = await mount()
    expect(roles.find('[data-testid="discord-channel-1"]').exists()).toBe(false)
    here.path = "/management/platforms/discord/channels"
    const wrapper = await mount()
    expect(wrapper.find('[data-testid="discord-roles"]').exists()).toBe(false)

    const sitecie = wrapper.get('[data-testid="discord-channel-1"]').text()
    expect(sitecie).toContain("Private")
    expect(sitecie).toContain("Only @Sitecie")
    const valo = wrapper.get('[data-testid="discord-channel-2"]').text()
    expect(valo).toContain("Game Valorant")
    expect(valo).toContain("Everyone reads, @Member writes")
    expect(wrapper.get('[data-testid="discord-channel-differs-2"]').text()).toBe("Everyone writes")
    expect(wrapper.get('[data-testid="discord-channel-differs-1"]').text()).toBe("No")
    expect(wrapper.find('[data-testid="discord-channel-3"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="discord-channel-4"]').exists()).toBe(false)

    await wrapper.get('[data-testid="discord-channels-Archive-toggle"]').trigger("click")
    expect(wrapper.get('[data-testid="discord-channel-3"]').text()).toContain("Voice")
  })

  it("creates a missing role", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="discord-create-3"]').trigger("click")
    await settle()

    expect(api.createMissingTargets).toHaveBeenCalledWith({path: {system: "DISCORD"}, body: {targetIds: [3]}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The role is being created.")

    api.createMissingTargets.mockResolvedValueOnce({status: 200, data: {queued: 2}})
    await wrapper.get('[data-testid="discord-create-3"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "2 roles are being created.")

    api.createMissingTargets.mockResolvedValueOnce({status: 503, error: {code: "TargetSystemUnavailable", message: "Discord cannot be reached now."}, response: {status: 503}})
    await wrapper.get('[data-testid="discord-create-3"]').trigger("click")
    await settle()
    expect(api.findTargetOverview).toHaveBeenCalledTimes(3)
  })

  it("offers the matches by name, and links the ones left ticked", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-matches"]').text()).toContain("2 existing roles match by name")
    await wrapper.get('[data-testid="discord-review-matches"]').trigger("click")
    await settle()
    expect(document.body.textContent).toContain("Lancie: @Lancie and #lancie")
    expect(document.body.textContent).toContain("Blueshell CS2: @Blueshell CS2 and no channel")
    const boxes = () => wrapper.findAllComponents({name: "CheckBox"})
    boxes()[1]!.vm.$emit("update:modelValue", false)
    boxes()[1]!.vm.$emit("update:modelValue", true)
    boxes()[1]!.vm.$emit("update:modelValue", false)
    await settle()
    expect(inDialog("discord-matches-link").text()).toBe("Link 1 match")
    wrapper.findAllComponents({name: "ModalDialog"})[0]!.vm.$emit("update:open", false)
    await settle()
    expect(wrapper.findAllComponents({name: "ModalDialog"})[0]!.props("open")).toBe(false)
    await wrapper.get('[data-testid="discord-review-matches"]').trigger("click")
    await settle()
    boxes()[1]!.vm.$emit("update:modelValue", false)
    await settle()

    api.adoptDiscordMatches.mockResolvedValueOnce({status: 503, error: {code: "DiscordUnreachable"}, response: {status: 503}})
    await inDialog("discord-matches-link").trigger("click")
    await settle()
    expect(inDialog("discord-matches-refusal").text()).toContain("Discord cannot be reached now")

    await inDialog("discord-matches-link").trigger("click")
    await settle()
    expect(api.adoptDiscordMatches).toHaveBeenLastCalledWith({body: {keys: ["COMMITTEE_MEMBERS:5"]}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "1 match is linked.")
    expect(wrapper.findAllComponents({name: "ModalDialog"})[0]!.props("open")).toBe(false)
  })

  it("says how many matches were linked, and says when Discord cannot be read", async () => {
    api.listDiscordMatches.mockResolvedValue({status: 200, data: [matches[0]]})
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 3}})
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="discord-matches"]').text()).toContain("1 existing role matches by name")
    await wrapper.get('[data-testid="discord-review-matches"]').trigger("click")
    await settle()
    expect(inDialog("discord-matches-link").text()).toBe("Link 1 match")
    await inDialog("discord-matches-link").trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "3 matches are linked.")

    api.findTargetOverview.mockResolvedValue({status: 503, error: null, response: {status: 503}})
    api.listDiscordMatches.mockResolvedValue({status: 200, data: []})
    const unread = await mount()
    expect(unread.get('[data-testid="discord-unreadable"]').text()).toContain("Discord could not be read")
    expect(unread.find('[data-testid="discord-matches"]').exists()).toBe(false)
  })
})
