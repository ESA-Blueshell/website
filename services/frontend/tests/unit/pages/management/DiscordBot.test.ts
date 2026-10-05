import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordBot from "@/pages/management/DiscordBot.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findBotStanding: vi.fn()}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => ({path: "/management/platforms/discord/bot"}),
}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const standing = (fields: Record<string, unknown> = {}) => ({
  connected: true, manageRoles: true, manageChannels: false, botRole: {id: "904", name: "Blueshell bot"}, claimed: [],
  above: [{id: "905", name: "Admin"}], hidden: [
    {id: "907", guildId: "324", name: "mods", category: "Moderation", voice: false},
    {id: "908", guildId: "324", name: "board-room", category: null, voice: true},
  ],
  permissions: [
    {name: "View Channels", neededFor: "Read the server's channels", granted: true},
    {name: "Manage Channels", neededFor: "Make a channel, archive it and remove it", granted: false},
  ],
  ...fields,
})

describe("the Discord bot's page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordBot)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findBotStanding.mockResolvedValue({status: 200, data: standing()})
  })

  afterEach(() => unmountAll(wrappers, "DiscordBotPage"))

  it("says which permissions the bot holds and lacks, what it cannot see or add, and what to change on Discord", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-bot-role"]').text()).toContain("@Blueshell bot")
    expect(wrapper.get('[data-testid="discord-bot-held"]').text()).toContain("1 of 2")
    expect(wrapper.get('[data-testid="discord-bot-held"]').text()).toContain("1 missing")
    expect(wrapper.get('[data-testid="discord-bot-lacking"]').text()).toContain("The bot lacks a permission")
    expect(wrapper.get('[data-testid="discord-bot-permission-Manage Channels"]').text()).toContain("Missing")
    expect(wrapper.get('[data-testid="discord-bot-permission-View Channels"]').text()).toContain("Granted")
    expect(wrapper.get('[data-testid="discord-bot-steps"]').findAll("li")).toHaveLength(5)
    expect(wrapper.get('[data-testid="discord-bot-steps"]').text()).toContain("Turn on Manage Channels")
    // Each hidden channel is a Discord mention that opens the channel itself, under its category.
    expect(wrapper.get('[data-testid="discord-bot-hidden-907"]').attributes("href")).toBe("https://discord.com/channels/324/907")
    expect(wrapper.get('[data-testid="discord-bot-hidden"]').text()).toContain("Moderation")
    expect(wrapper.get('[data-testid="discord-bot-hidden-908"]').text()).toBe("board-room")
    expect(wrapper.get('[data-testid="discord-bot-above"]').text()).toContain("@Admin")
    expect(await sortByEveryHead(wrapper)).toBe(3)
  })

  it("says nothing is missing where the bot may do everything, and counts several missing", async () => {
    api.findBotStanding.mockResolvedValue({status: 200, data: standing({
      botRole: null, above: [], hidden: [], permissions: [{name: "View Channels", neededFor: "Read", granted: true}],
    })})
    const whole = await mount()

    expect(whole.get('[data-testid="discord-bot-role"]').text()).toContain("None")
    expect(whole.get('[data-testid="discord-bot-held"]').text()).toContain("Everything the site needs")
    for (const testid of ["discord-bot-lacking", "discord-bot-steps", "discord-bot-hidden", "discord-bot-above"]) {
      expect(whole.find(`[data-testid="${testid}"]`).exists()).toBe(false)
    }

    api.findBotStanding.mockResolvedValue({status: 200, data: standing({permissions: [
      {name: "Manage Roles", neededFor: "Make a role", granted: false}, {name: "Manage Channels", neededFor: "Make a channel", granted: false},
    ]})})
    expect((await mount()).get('[data-testid="discord-bot-lacking"]').text()).toContain("The bot lacks 2 permissions")
  })

  it("says nothing can be checked while the bot is away or cannot be read", async () => {
    api.findBotStanding.mockResolvedValue({status: 200, data: standing({connected: false, permissions: []})})
    const away = await mount()
    expect(away.get('[data-testid="discord-bot-away"]').text()).toContain("nothing can be checked")
    expect(away.find('[data-testid="discord-bot-permissions"]').exists()).toBe(false)

    api.findBotStanding.mockResolvedValue({status: 503, error: {message: "down"}, response: {status: 503}})
    expect((await mount()).find('[data-testid="discord-bot-away"]').exists()).toBe(true)
  })
})
