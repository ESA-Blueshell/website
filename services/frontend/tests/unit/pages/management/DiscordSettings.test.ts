import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordSettings from "@/pages/management/DiscordSettings.vue"
import store from "@/plugins/store"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  listServerCohortRoles: vi.fn(),
  setServerCohortRole: vi.fn(),
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
  {key: "CURRENT_MEMBERS", type: "CURRENT_MEMBERS", label: "Members", roleId: memberRole, roleName: memberRole ? "Blueshell's Finest" : null, defaultChannels: []},
  {key: "CURRENT_COMMITTEE_MEMBERS", type: "CURRENT_COMMITTEE_MEMBERS", label: "Committee members", roleId: null, roleName: null, defaultChannels: ["activists", "gone"]},
]
const bot = {infoChannel: "events-info", calendarChannel: "events-calendar", starboardChannel: "starboard", claimRoleIds: ["777"]}

describe("the Discord settings page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordSettings)
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
    expect(wrapper.get('[data-testid="discord-settings-default-CURRENT_COMMITTEE_MEMBERS-activists"]').attributes("href")).toContain("/900")
    expect(wrapper.get('[data-testid="discord-settings-default-CURRENT_COMMITTEE_MEMBERS-gone"]').text()).toContain("gone")
    expect(wrapper.get('[data-testid="discord-settings-cohort-CURRENT_MEMBERS"]').text()).toContain("None")
    expect(wrapper.find('[data-testid="discord-settings-create-CURRENT_MEMBERS"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="discord-settings-cohort-CURRENT_MEMBERS"]').classes()).toContain("mg-table__row--opens")
    expect(await sortByEveryHead(wrapper)).toBe(1)
  })

  it("points a cohort at a picked role or a new one, and says why Discord refused", async () => {
    api.setServerCohortRole.mockResolvedValue({status: 200, data: cohorts()})
    const commit = vi.spyOn(store, "commit")
    const wrapper = await mount()

    wrapper.getComponent({name: "SearchPicker"}).vm.$emit("pick", "222")
    await settle()
    expect(api.setServerCohortRole).toHaveBeenCalledWith(expect.objectContaining({path: {key: "CURRENT_MEMBERS"}, body: {roleId: "222", create: false}}))
    expect(commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Members follows Blueshell's Finest now.")

    await wrapper.get('[data-testid="discord-settings-create-CURRENT_COMMITTEE_MEMBERS"]').trigger("click")
    await settle()
    expect(api.setServerCohortRole).toHaveBeenLastCalledWith(expect.objectContaining({path: {key: "CURRENT_COMMITTEE_MEMBERS"}, body: {create: true}}))

    api.setServerCohortRole.mockResolvedValue({status: 503, error: {code: "TargetSystemUnavailable"}, response: {status: 503}})
    await wrapper.get('[data-testid="discord-settings-create-CURRENT_COMMITTEE_MEMBERS"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="discord-settings-failure"]').text()).toBe("Discord cannot be reached now; try again in a moment.")
    commit.mockRestore()
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
