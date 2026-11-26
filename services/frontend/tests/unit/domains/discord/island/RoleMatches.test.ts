import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import RoleMatches from "@/domains/discord/island/RoleMatches.vue"
import {mountInApp, settle, unmountAll} from "../../../pages/helpers"

const api = vi.hoisted(() => ({listDiscordMatches: vi.fn(), adoptDiscordMatches: vi.fn()}))
vi.mock("@/services/api", async (importOriginal) => ({...(await importOriginal<typeof import("@/services/api")>()), ...api}))

const sitecie = {key: "COMMITTEE_MEMBERS:1", label: "Sitecie", type: "COMMITTEE_MEMBERS", roleId: "901", roleName: "Sitecie",
  channels: [{id: "11", name: "sitecie", kind: "TEXT", category: "Committees"}]}
const valorant = {key: "TEAM_PLAYERS:2", label: "Blueshell Valorant", type: "TEAM_PLAYERS", roleId: "902", roleName: "Blueshell Valorant", channels: []}

describe("roles named as a committee or a team", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(RoleMatches)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.listDiscordMatches.mockResolvedValue({status: 200, data: [sitecie, valorant]})
  })

  afterEach(() => unmountAll(wrappers, "RoleMatches"))

  it("links the ticked matches as they were shown, says how many and which were refused, and reads the matches again", async () => {
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 1, refused: [
      {label: "Blueshell Valorant", code: "TargetLinkedElsewhere", facts: {cohort: "Valorant fans"}},
    ]}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="discord-match-COMMITTEE_MEMBERS:1"]').text()).toContain("sitecie")
    expect(wrapper.get('[data-testid="discord-matches-link"]').attributes("disabled")).toBeDefined()
    wrapper.findAllComponents({name: "CheckBox"})[0]!.vm.$emit("update:modelValue", true)
    wrapper.findAllComponents({name: "CheckBox"})[1]!.vm.$emit("update:modelValue", true)
    wrapper.findAllComponents({name: "CheckBox"})[1]!.vm.$emit("update:modelValue", false)
    wrapper.findAllComponents({name: "CheckBox"})[1]!.vm.$emit("update:modelValue", true)
    await settle()
    expect(wrapper.get('[data-testid="discord-matches-link"]').text()).toBe("Link 2 roles")
    await wrapper.get('[data-testid="discord-matches-link"]').trigger("click")
    await settle()

    expect(api.adoptDiscordMatches).toHaveBeenCalledWith({body: {matches: [
      {key: "COMMITTEE_MEMBERS:1", roleId: "901", channelIds: ["11"]},
      {key: "TEAM_PLAYERS:2", roleId: "902", channelIds: []},
    ]}})
    expect(wrapper.get('[data-testid="discord-matches-said"]').text()).toBe("1 linked. Blueshell Valorant: Valorant fans already follows that role.")
    expect(api.listDiscordMatches).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted("linked")).toHaveLength(1)
  })

  it("says why a link failed, and shows nothing where nothing matches", async () => {
    api.adoptDiscordMatches.mockResolvedValue({status: 503, error: {code: "TargetSystemUnavailable"}})
    const wrapper = await mount()
    wrapper.findAllComponents({name: "CheckBox"})[0]!.vm.$emit("update:modelValue", true)
    await settle()
    await wrapper.get('[data-testid="discord-matches-link"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="discord-matches-said"]').text()).toBe("Discord cannot be reached now.")

    api.listDiscordMatches.mockResolvedValueOnce({status: 500, error: {}})
    expect((await mount()).find('[data-testid="discord-matches"]').exists()).toBe(false)
  })

  it("words a refused match by its code, else by the reason it carries, else plainly", async () => {
    api.adoptDiscordMatches.mockResolvedValue({status: 200, data: {linked: 0, refused: [
      {label: "Sitecie", code: "ChannelOutsideCategory", facts: {channel: "general", category: "Committees"}},
      {label: "Blueshell Valorant", code: null, facts: {reason: "Another board member linked it."}},
      {label: "Compocie", code: "SomethingNew", facts: {}},
    ]}})
    const wrapper = await mount()
    wrapper.findAllComponents({name: "CheckBox"})[0]!.vm.$emit("update:modelValue", true)
    await settle()
    await wrapper.get('[data-testid="discord-matches-link"]').trigger("click")
    await settle()

    expect(wrapper.get('[data-testid="discord-matches-said"]').text()).toBe(
      "0 linked. Sitecie: #general is not under Committees, so it cannot be opened to the role. "
      + "Blueshell Valorant: Another board member linked it. Compocie: It could not be linked.",
    )
  })
})
