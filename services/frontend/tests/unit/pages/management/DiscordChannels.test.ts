import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordChannels from "@/pages/management/DiscordChannels.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findTargetOverview: vi.fn(), listCataloguedChannels: vi.fn()}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => ({path: "/management/platforms/discord/channels"}),
}))
vi.mock("@/services/api", async (importOriginal) => ({...(await importOriginal<typeof import("@/services/api")>()), ...api}))

describe("the Discord channels page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordChannels)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findTargetOverview.mockResolvedValue({status: 200, data: {
      lists: [
        {externalId: "900", label: "Sitecie", cohortLabel: "Sitecie", cohortType: "COMMITTEE_MEMBERS", enforced: false},
        {externalId: "901", label: "Artists", enforced: false},
      ],
      missing: [],
      lastReconciledAt: null,
    }})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: [
      {id: "1", name: "sitecie", kind: "TEXT", category: "Committees", private: true, roleIds: ["900"], roleNames: {"900": "Sitecie"}},
      {id: "2", name: "general", kind: "TEXT", category: null, private: false, roleIds: [], roleNames: {}},
      {id: "3", name: "chess", kind: "VOICE", category: "Games", private: false, roleIds: ["901"], roleNames: {"901": "Artists"},
        game: {code: "CHESS", name: "Chess", kind: "CASUAL"}},
    ]})
  })

  afterEach(() => unmountAll(wrappers, "DiscordChannelsPage"))

  it("lists the channels in one table, the uncategorised first, with who can access each and what it belongs to", async () => {
    const wrapper = await mount()

    expect(wrapper.findAll("[data-testid^='discord-channel-']").map((one) => one.attributes("data-testid")))
      .toEqual(["discord-channel-2", "discord-channel-1", "discord-channel-3"])
    const sitecie = wrapper.get('[data-testid="discord-channel-1"]').text()
    expect(sitecie).toContain("Only @Sitecie")
    expect(sitecie).toContain("Committee members · Sitecie")
    expect(wrapper.get('[data-testid="discord-channel-2"]').text()).toContain("Everyone")
    expect(wrapper.get('[data-testid="discord-channel-3"]').text()).toContain("Chess, casual channel")
    expect(await sortByEveryHead(wrapper)).toBe(4)
  })

  it("says when the roles could not be read rather than that a channel belongs to nothing", async () => {
    api.findTargetOverview.mockResolvedValueOnce({status: 500, error: {}})
    const wrapper = await mount()

    expect(wrapper.find('[data-testid="discord-channels-roles-unread"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="discord-channel-1"]').text()).toContain("Not read")
    expect(wrapper.get('[data-testid="discord-channel-1"]').text()).toContain("Only @Sitecie")
  })

  it("says when the channels could not be read, and when the bot is away", async () => {
    api.listCataloguedChannels.mockResolvedValueOnce({status: 500, error: {}})
    api.findTargetOverview.mockResolvedValueOnce({status: 500, error: {}})
    expect((await mount()).find('[data-testid="discord-channels-unread"]').exists()).toBe(true)

    api.listCataloguedChannels.mockResolvedValueOnce({status: 200, data: []})
    expect((await mount()).find('[data-testid="discord-channels-none"]').exists()).toBe(true)
  })
})
