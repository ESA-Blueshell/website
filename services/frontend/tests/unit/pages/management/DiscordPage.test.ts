import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import DiscordPage from "@/pages/management/DiscordPage.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  reconcileTarget: vi.fn(),
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
  {id: "3", name: "old-lan", kind: "VOICE", category: "Archive", private: true, roleIds: ["960"]},
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
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 1, refused: []}})
  })

  afterEach(() => unmountAll(wrappers, "DiscordPage"))

  it("lists the roles in one table: the missing first, the managed with their drift and access, then the ones made by hand", async () => {
    const wrapper = await mount()

    expect(api.findTargetOverview).toHaveBeenCalledWith({path: {system: "DISCORD"}})
    expect(wrapper.get('[data-testid="discord-role-900"]').text()).toContain("@Sitecie")
    expect(wrapper.get('[data-testid="discord-role-900"]').text()).toContain("Committee members · Sitecie")
    expect(wrapper.get('[data-testid="discord-role-state-900"]').text()).toBe("2 extra")
    expect(wrapper.get('[data-testid="discord-role-900"]').text()).toContain("1 category, 1 channel")
    expect(wrapper.get('[data-testid="discord-role-missing-3"]').text()).toContain("No role yet")
    expect(wrapper.get('[data-testid="discord-fact-drift"]').text()).toContain("2 people with no Discord linked")
    expect(wrapper.get('[data-testid="discord-role-900"]').text()).toContain("Managed by the site")
    expect(wrapper.get('[data-testid="discord-role-950"]').text()).toContain("@Gamers")
    expect(wrapper.get('[data-testid="discord-role-950"]').text()).toContain("not managed by the site")
    expect(wrapper.get('[data-testid="discord-role-900"] a').attributes("to")).toBe("/management/platforms/discord/roles/900")
    expect(wrapper.findAll('[data-testid^="discord-role-"]')[0]!.attributes("data-testid")).toBe("discord-role-missing-3")
    expect(await sortByEveryHead(wrapper)).toBe(4)
  })

  it("compares the ticked roles with Discord together, and adds the role to who is missing it", async () => {
    api.reconcileTarget.mockResolvedValue({status: 202, data: undefined})
    const wrapper = await mount()
    const bulk = () => wrapper.findComponent({name: "BulkAdd"})

    expect(wrapper.find('[data-testid="discord-role-check-missing-3"]').exists()).toBe(false)
    wrapper.findAllComponents({name: "ManagementTable"})[0]!.vm.$emit("toggleShown")
    await settle()
    expect(wrapper.get('[data-testid="discord-role-selection"]').text()).toContain("2 selected")
    wrapper.findAllComponents({name: "ManagementTable"})[0]!.vm.$emit("clearSelection")
    await settle()
    await wrapper.get('[data-testid="discord-role-check-900"]').setValue(true)
    await wrapper.get('[data-testid="discord-role-check-950"]').setValue(true)

    await wrapper.get('[data-testid="discord-bulk-push"]').trigger("click")
    await settle()
    expect(bulk().props("title")).toBe("Add missing people")
    expect(bulk().props("skipped")).toEqual([{name: "Sitecie", why: "Nobody is missing"}, {name: "Gamers", why: "Follows nothing on the site"}])
    bulk().vm.$emit("update:open", false)
    await settle()

    await wrapper.get('[data-testid="discord-bulk-compare"]').trigger("click")
    await settle()
    expect(bulk().props("title")).toBe("Compare with Discord")
    expect(bulk().props("items").map((one: {name: string}) => one.name)).toEqual(["Sitecie"])
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: true})
    expect(api.reconcileTarget).toHaveBeenCalledWith({path: {id: 101, targetId: 1}})

    api.findTargetOverview.mockClear()
    bulk().vm.$emit("done")
    await settle()
    expect(api.findTargetOverview).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="discord-role-selection"]').text()).toContain("0 selected")
  })

  it("lists the channels by category with what they belong to, who gets in and where Discord differs", async () => {
    const roles = await mount()
    expect(roles.find('[data-testid="discord-channel-1"]').exists()).toBe(false)
    here.path = "/management/platforms/discord/channels"
    const wrapper = await mount()
    expect(wrapper.find('[data-testid="discord-roles"]').exists()).toBe(false)

    const glyph = (id: string) => wrapper.get(`[data-testid="discord-channel-${id}"] [role="img"]`).attributes("aria-label")
    const sitecie = wrapper.get('[data-testid="discord-channel-1"]').text()
    expect(glyph("1")).toBe("Private text channel")
    expect(glyph("2")).toBe("Public text channel")
    expect(sitecie).toContain("Only @Sitecie")
    const valo = wrapper.get('[data-testid="discord-channel-2"]').text()
    expect(valo).toContain("Game Valorant")
    expect(valo).toContain("Everyone reads, @Member writes")
    expect(wrapper.get('[data-testid="discord-channel-differs-2"]').text()).toBe("Everyone writes")
    expect(wrapper.get('[data-testid="discord-channel-differs-1"]').text()).toBe("No")
    expect(await sortByEveryHead(wrapper)).toBe(4)

    // An archived channel is left out until the filter asks for it.
    expect(wrapper.find('[data-testid="discord-channel-3"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="discord-channels"]').text()).toContain("2 channels, 1 archived hidden")
    const archived = () => wrapper.findAllComponents({name: "FilterPicker"}).find((one) => one.props("testid") === "discord-channel-archived")!
    archived().vm.$emit("update:modelValue", "shown")
    await settle()
    expect(glyph("3")).toBe("Private voice channel")
    expect(wrapper.get('[data-testid="discord-channel-3"]').text()).toContain("read only, kept for history")
    expect(wrapper.get('[data-testid="discord-channels"]').text()).toContain("3 channels")
    // Narrowed by kind and by who it is open to, and a row opens the channel's own page.
    const picker = (testid: string) => wrapper.findAllComponents({name: "FilterPicker"}).find((one) => one.props("testid") === testid)!
    const shown = () => wrapper.findAll('[data-testid^="discord-channel-"][data-row]').map((one) => one.attributes("data-testid"))
    picker("discord-channel-kind").vm.$emit("update:modelValue", "VOICE")
    await settle()
    expect(shown()).toEqual(["discord-channel-3"])
    picker("discord-channel-kind").vm.$emit("update:modelValue", null)
    picker("discord-channel-visibility").vm.$emit("update:modelValue", "public")
    await settle()
    expect(shown()).toEqual(["discord-channel-2"])
    picker("discord-channel-visibility").vm.$emit("update:modelValue", "private")
    await settle()
    expect(shown()).toEqual(["discord-channel-1", "discord-channel-3"])
    expect(wrapper.findAllComponents({name: "ManagementTable"}).at(-1)!.props("to")({id: "1"})).toBe("/management/platforms/discord/channels/1")

    wrapper.findComponent({name: "FilterBar"}).vm.$emit("clear")
    await settle()
    expect(wrapper.find('[data-testid="discord-channel-3"]').exists()).toBe(false)
    expect(shown()).toEqual(["discord-channel-1", "discord-channel-2"])
  })

  it("creates a missing role only once that is confirmed, and says why when Discord refuses", async () => {
    const wrapper = await mount()

    const dialog = () => wrapper.findComponent({name: "ConfirmDialog"})
    const confirm = async () => {
      await wrapper.get('[data-testid="discord-create-3"]').trigger("click")
      await settle()
      expect(dialog().props("open")).toBe(true)
      dialog().vm.$emit("confirm")
      await settle()
    }

    // Nothing is created by the first press: the role is named once more and confirmed.
    await wrapper.get('[data-testid="discord-create-3"]').trigger("click")
    await settle()
    expect(dialog().props("title")).toContain("on Discord?")
    expect(api.createMissingTargets).not.toHaveBeenCalled()
    dialog().vm.$emit("update:open", false)
    await settle()
    expect(dialog().props("open")).toBe(false)

    await confirm()
    expect(api.createMissingTargets).toHaveBeenCalledWith({path: {system: "DISCORD"}, body: {targetIds: [3]}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The role is being created.")

    api.createMissingTargets.mockResolvedValueOnce({status: 200, data: {queued: 2}})
    await confirm()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "2 roles are being created.")

    api.createMissingTargets.mockResolvedValueOnce({status: 503, error: {code: "TargetSystemUnavailable", message: "Discord cannot be reached now."}, response: {status: 503}})
    await confirm()
    expect(dialog().props("failure")).toContain("cannot be reached now")
    expect(api.findTargetOverview).toHaveBeenCalledTimes(3)
  })

  it("offers the matches by name, and links the ones left ticked", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-matches"]').text()).toContain("2 existing roles match by name")
    await wrapper.get('[data-testid="discord-review-matches"]').trigger("click")
    await settle()
    // One entry a match: the role, and under it every channel it has access to once linked.
    expect(document.body.textContent).toContain("Lancie: @Lancie")
    expect(document.body.textContent).toContain("Has access to #old-lan, #lancie.")
    expect(document.body.textContent).toContain("Blueshell CS2: @Blueshell CS2")
    expect(document.body.textContent).toContain("Has access to no channels yet.")
    expect(document.body.textContent).not.toContain("and no channel")
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
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 3, refused: []}})
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="discord-matches"]').text()).toContain("1 existing role matches by name")
    await wrapper.get('[data-testid="discord-review-matches"]').trigger("click")
    await settle()
    expect(inDialog("discord-matches-link").text()).toBe("Link 1 match")
    await inDialog("discord-matches-link").trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "3 matches are linked.")

    // A match Discord refused stays on the dialog with why, while the others are linked.
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 0, refused: [{label: "Lancie", reason: "The bot may not change lancie."}]}})
    await wrapper.get('[data-testid="discord-review-matches"]').trigger("click")
    await settle()
    await inDialog("discord-matches-link").trigger("click")
    await settle()
    expect(inDialog("discord-matches-refusal").text()).toContain("Lancie: The bot may not change lancie.")
    const matchesDialog = wrapper.findAllComponents({name: "ModalDialog"}).find((one) => one.props("testid") === "discord-matches-dialog")!
    expect(matchesDialog.props("open")).toBe(true)

    api.findTargetOverview.mockResolvedValue({status: 503, error: null, response: {status: 503}})
    api.listDiscordMatches.mockResolvedValue({status: 200, data: []})
    const unread = await mount()
    expect(unread.get('[data-testid="discord-unreadable"]').text()).toContain("Discord could not be read")
    expect(unread.find('[data-testid="discord-matches"]').exists()).toBe(false)
  })

  it("says so where Discord has no roles or no channels yet", async () => {
    api.findTargetOverview.mockResolvedValue({status: 200, data: {lists: [], missing: []}})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: []})
    expect((await mount()).get('[data-testid="discord-roles"]').text()).toContain("No roles yet.")

    here.path = "/management/platforms/discord/channels"
    expect((await mount()).get('[data-testid="discord-channels"]').text()).toContain("No channels yet.")
  })

  it("draws roles and channels as rows on a phone", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const roles = await mount()
    expect(roles.get('[data-testid="discord-role-900-open"]').attributes("to")).toBe("/management/platforms/discord/roles/900")
    expect(roles.get('[data-testid="discord-role-missing-3"]').text()).toContain("No role yet")
    expect(roles.get('[data-testid="discord-role-950"]').text()).toContain("Not compared")

    here.path = "/management/platforms/discord/channels"
    const channels = await mount()
    vi.unstubAllGlobals()
    expect(channels.get('[data-testid="discord-channel-1"]').text()).toContain("#")
  })
})
