import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordSettings from "@/pages/management/DiscordSettings.vue"
import store from "@/plugins/store"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  listServerCohortRoles: vi.fn(),
  findServerCohortDiscord: vi.fn(),
  setServerCohortDiscord: vi.fn(),
  findDiscordBotSettings: vi.fn(),
  setDiscordBotSettings: vi.fn(),
  listKeptRoles: vi.fn(),
  listKeptChannels: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => ({path: "/management/platforms/discord/settings"}),
}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const cohorts = (memberRole: string | null = "111") => [
  {key: "CURRENT_MEMBERS", type: "CURRENT_MEMBERS", label: "Members", roleId: memberRole, roleName: memberRole ? "Blueshell's Finest" : null, defaultChannels: [], channels: [
    {id: "901", name: "events-info", voice: false, private: false},
    {id: "902", name: "Lounge", voice: true, private: true},
  ]},
  {key: "CURRENT_COMMITTEE_MEMBERS", type: "CURRENT_COMMITTEE_MEMBERS", label: "Committee members", roleId: null, roleName: null, defaultChannels: ["activists", "gone"], channels: []},
  {key: "CURRENT_TEAM_PLAYERS", type: "CURRENT_TEAM_PLAYERS", label: "Esports team members", roleId: null, roleName: null, defaultChannels: [], channels: []},
  {key: "KANDI", type: "KANDI", label: "Kandi", roleId: "333", roleName: "Kandi", defaultChannels: [], channels: Array.from({length: 14}, (_, i) => ({id: `7${i}`, name: `room-${i}`, voice: false, private: false}))},
]
const ModalDialog = {
  name: "ModalDialog",
  props: ["open", "title", "testid", "cancelTestid", "wide"],
  emits: ["update:open"],
  template: "<div v-if='open' :data-testid='testid'><slot /><slot name='footer' /></div>",
}
const DiscordPlaceFields = {name: "DiscordPlaceFields", props: ["read", "name", "slug", "category", "holders", "testid", "modelValue"], emits: ["update:modelValue"], template: "<div />"}
const bot = {infoChannel: "events-info", calendarChannel: "events-calendar", starboardChannel: "starboard", claimRoleIds: ["777"]}

describe("the Discord settings page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordSettings, {global: {stubs: {ModalDialog, DiscordPlaceFields}}})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.listServerCohortRoles.mockResolvedValue({status: 200, data: cohorts()})
    api.findDiscordBotSettings.mockResolvedValue({status: 200, data: bot})
    api.listKeptRoles.mockResolvedValue({status: 200, data: [{id: "111", name: "Blueshell's Finest", assignable: true}, {id: "222", name: "Activist", assignable: true}, {id: "777", name: "Valorant", assignable: true}]})
    api.listKeptChannels.mockResolvedValue({status: 200, data: [
      {id: "900", name: "activists", kind: "TEXT", category: "Activists"},
      {id: "901", name: "events-info", kind: "TEXT", category: null},
      {id: "902", name: "Lounge", kind: "VOICE", category: null},
    ]})
  })

  afterEach(() => unmountAll(wrappers, "DiscordSettingsPage"))

  it("lists each server-wide cohort with its role and the channels it gets, linking a set role to its page", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-settings-cohort-CURRENT_MEMBERS"]').text()).toContain("@Blueshell's Finest")
    expect(wrapper.get('[data-testid="discord-settings-cohort-CURRENT_COMMITTEE_MEMBERS"]').text()).toContain("No role")
    expect(wrapper.get('[data-testid="discord-settings-channel-CURRENT_MEMBERS-902"]').attributes("href")).toContain("/902")
    expect(wrapper.get('[data-testid="discord-settings-channels-more-KANDI"]').text()).toBe("and 2 more")
    expect(wrapper.get('[data-testid="discord-settings-cohort-CURRENT_COMMITTEE_MEMBERS"]').text()).toContain("None")
    expect(wrapper.get('[data-testid="discord-settings-link-CURRENT_MEMBERS"]').text()).toBe("Edit")
    expect(wrapper.get('[data-testid="discord-settings-link-CURRENT_COMMITTEE_MEMBERS"]').text()).toBe("Link")
    expect(wrapper.get('[data-testid="discord-settings-cohort-CURRENT_MEMBERS"]').classes()).toContain("mg-table__row--opens")
    expect(await sortByEveryHead(wrapper)).toBe(2)
  })

  it("links a role and its channels through the shared Discord form, each cohort under its own category", async () => {
    api.setServerCohortDiscord.mockResolvedValue({status: 200, data: {available: true, roleId: "222", roleName: "Committee", channels: []}})
    api.findServerCohortDiscord.mockResolvedValue({status: 200, data: {available: true, roleId: "111", roleName: "Blueshell's Finest", channels: []}})
    const commit = vi.spyOn(store, "commit")
    const wrapper = await mount()

    await wrapper.get('[data-testid="discord-settings-link-CURRENT_COMMITTEE_MEMBERS"]').trigger("click")
    const form = wrapper.getComponent(DiscordPlaceFields)
    expect(wrapper.getComponent(ModalDialog).props("title")).toBe("Committee members")
    // The channels a new role gets as well, a known one linked and an unknown one by its name.
    expect(wrapper.get('[data-testid="discord-settings-default-CURRENT_COMMITTEE_MEMBERS-activists"]').attributes("href")).toContain("/900")
    expect(wrapper.get('[data-testid="discord-settings-default-CURRENT_COMMITTEE_MEMBERS-gone"]').text()).toContain("gone")
    expect([form.props("read"), form.props("slug"), form.props("category")]).toEqual([null, "committee-members", "Committees"])
    form.vm.$emit("update:modelValue", {createRole: true, channelIds: [], createChannel: "committee-members"})
    await settle()
    await wrapper.get('[data-testid="discord-settings-link-save"]').trigger("click")
    await settle()

    expect(api.setServerCohortDiscord).toHaveBeenCalledWith(expect.objectContaining({
      path: {key: "CURRENT_COMMITTEE_MEMBERS"}, body: {createRole: true, channelIds: [], createChannel: "committee-members", move: false},
    }))
    expect(commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Committee members follows @Committee now.")
    expect(wrapper.find('[data-testid="discord-settings-link-dialog"]').exists()).toBe(false)
    expect(api.listServerCohortRoles).toHaveBeenCalledTimes(2)

    // A cohort with a role reads what it has; teams make channels under Esports, the board under Board.
    await wrapper.get('[data-testid="discord-settings-link-CURRENT_MEMBERS"]').trigger("click")
    await (wrapper.getComponent(DiscordPlaceFields).props("read") as () => Promise<unknown>)()
    expect(api.findServerCohortDiscord).toHaveBeenCalledWith(expect.objectContaining({path: {key: "CURRENT_MEMBERS"}}))
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)
    await settle()
    await wrapper.get('[data-testid="discord-settings-link-CURRENT_TEAM_PLAYERS"]').trigger("click")
    expect(wrapper.getComponent(DiscordPlaceFields).props("category")).toBe("Esports")
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", true)
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)
    await settle()
    await wrapper.get('[data-testid="discord-settings-link-KANDI"]').trigger("click")
    expect(wrapper.getComponent(DiscordPlaceFields).props("category")).toBe("Board")
    commit.mockRestore()
  })

  it("offers to move a role another cohort follows, and says why else Discord refused", async () => {
    api.setServerCohortDiscord.mockResolvedValue({status: 409, error: {code: "TargetLinkedElsewhere", cohort: "Board"}, response: {status: 409}})
    const wrapper = await mount()
    await wrapper.get('[data-testid="discord-settings-link-KANDI"]').trigger("click")
    wrapper.getComponent(DiscordPlaceFields).vm.$emit("update:modelValue", {roleId: "333", createRole: false, channelIds: []})
    await settle()
    await wrapper.get('[data-testid="discord-settings-link-save"]').trigger("click")
    await settle()

    const said = wrapper.get('[data-testid="discord-settings-link-failure"]').text()
    expect(said).toContain("That role belongs to Board now.")
    expect(said).toContain("Moving it here takes it from Board. Nothing changes on Discord.")
    api.setServerCohortDiscord.mockResolvedValue({status: 503, error: {code: "TargetSystemUnavailable"}, response: {status: 503}})
    await wrapper.get('[data-testid="discord-settings-link-move"]').trigger("click")
    await settle()
    expect(api.setServerCohortDiscord).toHaveBeenLastCalledWith(expect.objectContaining({body: {roleId: "333", createRole: false, channelIds: [], move: true}}))
    expect(wrapper.get('[data-testid="discord-settings-link-failure"]').text()).toBe("Discord cannot be reached now; try again in a moment.")
    expect(wrapper.find('[data-testid="discord-settings-link-move"]').exists()).toBe(false)
  })

  it("saves where the bots post and the role-claim bot's roles", async () => {
    api.setDiscordBotSettings.mockImplementation(async ({body}) => ({status: 200, data: body}))
    const commit = vi.spyOn(store, "commit")
    const wrapper = await mount()
    const pickers = wrapper.findAllComponents({name: "SearchPicker"})
    const named = (prefix: string) => pickers.find((one) => one.props("testidPrefix") === prefix)!

    expect(named("discord-settings-info-picker").props("options")).toEqual([
      {key: "activists", label: "#activists", note: "Activists"},
      {key: "events-info", label: "#events-info", note: undefined},
    ])
    named("discord-settings-info-picker").vm.$emit("pick", "activists")
    named("discord-settings-calendar-picker").vm.$emit("pick", "events-info")
    named("discord-settings-starboard-picker").vm.$emit("pick", "activists")
    const claim = wrapper.getComponent({name: "ChipPicker"})
    expect(claim.props("chosen")).toEqual([{key: "777", label: "Valorant"}])
    claim.vm.$emit("add", ["222"])
    claim.vm.$emit("remove", "777")
    await wrapper.get('[data-testid="discord-settings-bot-form"]').trigger("submit")
    await settle()

    expect(api.setDiscordBotSettings).toHaveBeenCalledWith(expect.objectContaining({
      body: {infoChannel: "activists", calendarChannel: "events-info", starboardChannel: "activists", claimRoleIds: ["222"]},
    }))
    expect(commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The bot settings are saved.")

    api.setDiscordBotSettings.mockResolvedValue({status: 503, error: {code: "DiscordUnreachable"}, response: {status: 503}})
    await wrapper.get('[data-testid="discord-settings-bot-form"]').trigger("submit")
    await settle()
    expect(wrapper.get('[data-testid="discord-settings-failure"]').text()).toContain("Discord cannot be reached now")
    commit.mockRestore()
  })

  it("says what could not be read, and offers no picker without Discord", async () => {
    api.listServerCohortRoles.mockResolvedValue({status: 500, error: {}, response: {status: 500}})
    api.findDiscordBotSettings.mockResolvedValue({status: 500, error: {}, response: {status: 500}})
    api.listKeptRoles.mockResolvedValue({status: 503, error: {}, response: {status: 503}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-settings-away"]').text()).toContain("Discord cannot be read now")
    expect(wrapper.get('[data-testid="discord-settings-cohorts-unread"]').text()).toBe("The cohorts could not be read.")
    expect(wrapper.get('[data-testid="discord-settings-bot-unread"]').text()).toBe("The bot settings could not be read.")
  })
})
