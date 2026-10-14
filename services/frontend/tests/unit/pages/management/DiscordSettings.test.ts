import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordSettings from "@/pages/management/DiscordSettings.vue"
import store from "@/plugins/store"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
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
    api.findDiscordBotSettings.mockResolvedValue({status: 200, data: bot})
    api.listKeptRoles.mockResolvedValue({status: 200, data: [{id: "111", name: "Blueshell's Finest", assignable: true}, {id: "222", name: "Activist", assignable: true}, {id: "777", name: "Valorant", assignable: true}]})
    api.listKeptChannels.mockResolvedValue({status: 200, data: [
      {id: "900", name: "activists", kind: "TEXT", category: "Activists"},
      {id: "901", name: "events-info", kind: "TEXT", category: null},
      {id: "902", name: "Lounge", kind: "VOICE", category: null},
    ]})
  })

  afterEach(() => unmountAll(wrappers, "DiscordSettingsPage"))

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

    api.setDiscordBotSettings.mockResolvedValue({status: 500, error: {detail: "Broken"}, response: {status: 500}})
    await wrapper.get('[data-testid="discord-settings-bot-form"]').trigger("submit")
    await settle()
    expect(wrapper.get('[data-testid="discord-settings-failure"]').text()).toBeTruthy()
    commit.mockRestore()
  })

  it("says what could not be read, and offers no picker without Discord", async () => {
    api.findDiscordBotSettings.mockResolvedValue({status: 500, error: {}, response: {status: 500}})
    api.listKeptRoles.mockResolvedValue({status: 503, error: {}, response: {status: 503}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-settings-away"]').text()).toContain("Discord cannot be read now")
    expect(wrapper.get('[data-testid="discord-settings-bot-unread"]').text()).toBe("The bot settings could not be read.")
  })

  it("tells an empty server apart from one that could not be read", async () => {
    api.listKeptRoles.mockResolvedValue({status: 200, data: []})
    api.listKeptChannels.mockResolvedValue({status: 200, data: []})
    const wrapper = await mount()

    expect(wrapper.find('[data-testid="discord-settings-away"]').exists()).toBe(false)
  })
})
