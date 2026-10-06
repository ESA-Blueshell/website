import {afterEach, beforeEach, describe, expect, it, type Mock, vi} from "vitest"
import {flushPromises, mount, type VueWrapper} from "@vue/test-utils"
import DiscordBulkAdd from "@/domains/discord/island/DiscordBulkAdd.vue"

const api = vi.hoisted(() => ({findBotStanding: vi.fn(), planDiscord: vi.fn(), listCataloguedChannels: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const ModalDialog = {
  name: "ModalDialog",
  props: ["open", "title", "testid", "cancel", "wide"],
  emits: ["update:open"],
  template: "<div v-if='open' :data-testid='testid'><slot /><slot name='footer' /></div>",
}

const standing = (fields: Record<string, unknown> = {}) => ({
  connected: true, manageRoles: true, manageChannels: true, botRole: null, above: [], claimed: [], permissions: [], hidden: [], ...fields,
})

const plan = [
  {key: "COMMITTEE_MEMBERS:1", label: "Sitecie", role: {roleId: "901", name: "SiteCie", kept: false, opens: ["10"]}, channel: {channelId: "10", name: "sitecie", category: "Committees"}, category: "Committees"},
  {key: "COMMITTEE_MEMBERS:2", label: "LanCie", role: {roleId: null, name: "LanCie", kept: false, opens: []}, channel: {channelId: null, name: "lancie", category: "Committees"}, category: "Committees"},
  {key: "COMMITTEE_MEMBERS:3", label: "Chess", role: {roleId: "903", name: "Chess", kept: true, opens: ["13"]}, channel: null, category: "Committees"},
]

const rows = [
  {key: "COMMITTEE_MEMBERS:1", name: "Sitecie", channel: "sitecie"},
  {key: "COMMITTEE_MEMBERS:2", name: "LanCie", channel: "lancie"},
  {key: "COMMITTEE_MEMBERS:3", name: "Chess", channel: null},
]

describe("adding Discord roles and channels in bulk", () => {
  const wrappers: VueWrapper[] = []
  type Save = (key: string, choice: unknown) => Promise<{ok: true} | {ok: false; reason: string}>
  const mounted = async (save: Mock<Save> = vi.fn<Save>(async () => ({ok: true}))) => {
    const wrapper = mount(DiscordBulkAdd, {
      props: {open: false, title: "Add Discord roles and channels", noun: ["committee", "committees"] as [string, string], rows, skipped: [{name: "Board", why: "Has a role already"}], save, testid: "bulk"},
      global: {stubs: {ModalDialog}},
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    await wrapper.setProps({open: true})
    await flushPromises()
    return {wrapper, save}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findBotStanding.mockResolvedValue({status: 200, data: standing()})
    api.planDiscord.mockResolvedValue({status: 200, data: plan})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: [
      {id: "10", name: "sitecie", kind: "TEXT", category: "Committees", private: true, roleIds: []},
      {id: "11", name: "general", kind: "TEXT", category: null, private: false, roleIds: []},
      {id: "12", name: "Lounge", kind: "VOICE", category: null, private: false, roleIds: []},
    ]})
  })

  afterEach(() => {
    for (const one of wrappers.splice(0)) one.unmount()
  })

  it("says per row whether the role is linked, created or kept, and what the channel will be", async () => {
    const {wrapper} = await mounted()

    expect(api.planDiscord).toHaveBeenCalledWith({body: {rows: rows.map(({key, channel}) => ({key, channel}))}})
    expect(wrapper.get('[data-testid="bulk-row-COMMITTEE_MEMBERS:1"]').text()).toContain("Links")
    expect(wrapper.get('[data-testid="bulk-row-COMMITTEE_MEMBERS:1"]').text()).toContain("sitecie")
    expect(wrapper.get('[data-testid="bulk-row-COMMITTEE_MEMBERS:2"]').text()).toContain("Creates")
    expect((wrapper.get('[data-testid="bulk-channel-name-COMMITTEE_MEMBERS:2"]').element as HTMLInputElement).value).toBe("lancie")
    expect(wrapper.get('[data-testid="bulk-row-COMMITTEE_MEMBERS:3"]').text()).toContain("Keeps")
    expect(wrapper.get('[data-testid="bulk-row-COMMITTEE_MEMBERS:3"]').text()).toContain("No channel")
    expect(wrapper.get('[data-testid="bulk-skipped"]').text()).toContain("Board (has a role already)")
    const options = wrapper.findAllComponents({name: "SearchPicker"})[0]!.props("options") as Array<{key: string}>
    // Voice channels are not offered: a role's own channel is a text channel.
    expect(options.map((one) => one.key)).toEqual(["__new__", "10", "11", "__none__"])
  })

  it("applies each row as planned, keeping what an existing role opens, and ends with what happened", async () => {
    const save = vi.fn<Save>(async (key) => (key.endsWith(":2") ? {ok: false, reason: "Discord refused it."} : {ok: true}))
    const {wrapper} = await mounted(save)

    await wrapper.get('[data-testid="bulk-channel-name-COMMITTEE_MEMBERS:2"]').setValue(" lan-party ")
    const pickers = wrapper.findAllComponents({name: "SearchPicker"})
    pickers[0]!.vm.$emit("pick", "11")
    pickers[2]!.vm.$emit("pick", "__new__")
    await flushPromises()
    await wrapper.get('[data-testid="bulk-continue"]').trigger("click")
    expect(wrapper.get('[data-testid="bulk-ask"]').text()).toContain("Add these to 3 committees now?")
    await wrapper.get('[data-testid="bulk-back"]').trigger("click")
    await wrapper.get('[data-testid="bulk-continue"]').trigger("click")
    await wrapper.get('[data-testid="bulk-go"]').trigger("click")
    await flushPromises()

    expect(save).toHaveBeenNthCalledWith(1, "COMMITTEE_MEMBERS:1", {roleId: "901", createRole: false, channelIds: ["10", "11"], createChannel: null})
    expect(save).toHaveBeenNthCalledWith(2, "COMMITTEE_MEMBERS:2", {roleId: null, createRole: true, channelIds: [], createChannel: "lan-party"})
    expect(save).toHaveBeenNthCalledWith(3, "COMMITTEE_MEMBERS:3", {roleId: null, createRole: false, channelIds: ["13"], createChannel: "chess"})
    expect(wrapper.get('[data-testid="bulk-summary"]').text()).toBe("2 committees done, 1 failed.")
    expect(wrapper.get('[data-testid="bulk-done-COMMITTEE_MEMBERS:1"]').text()).toContain("Linked @SiteCie · Linked #general")
    expect(wrapper.get('[data-testid="bulk-done-COMMITTEE_MEMBERS:2"]').text()).toContain("Failed: Discord refused it.")
    expect(wrapper.get('[data-testid="bulk-done-COMMITTEE_MEMBERS:3"]').text()).toContain("Kept @Chess · #chess under Committees")
    expect(wrapper.emitted("done")).toHaveLength(1)
    await wrapper.get('[data-testid="bulk-close"]').trigger("click")
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)
    expect(wrapper.emitted("update:open")).toEqual([[false], [false]])
  })

  it("leaves a row without a channel, and a created role without a channel says so", async () => {
    const {wrapper, save} = await mounted()
    const pickers = wrapper.findAllComponents({name: "SearchPicker"})
    pickers[1]!.vm.$emit("pick", "__none__")
    await flushPromises()
    await wrapper.get('[data-testid="bulk-continue"]').trigger("click")
    await wrapper.get('[data-testid="bulk-go"]').trigger("click")
    await flushPromises()

    expect(save).toHaveBeenNthCalledWith(2, "COMMITTEE_MEMBERS:2", {roleId: null, createRole: true, channelIds: [], createChannel: null})
    expect(wrapper.get('[data-testid="bulk-done-COMMITTEE_MEMBERS:2"]').text()).toContain("Created @LanCie · No channel")
  })

  it("stops before the first row where the bot is away or may not do the work, or the plan cannot be read", async () => {
    api.findBotStanding.mockResolvedValue({status: 200, data: standing({manageRoles: false, manageChannels: false})})
    let {wrapper} = await mounted()
    expect(wrapper.get('[data-testid="bulk-blocked"]').text()).toContain("The bot lacks Manage Roles and Manage Channels")
    expect(wrapper.find('[data-testid="bulk-continue"]').exists()).toBe(false)
    expect(api.planDiscord).not.toHaveBeenCalled()

    api.findBotStanding.mockResolvedValue({status: 200, data: standing({connected: false})})
    ;({wrapper} = await mounted())
    expect(wrapper.get('[data-testid="bulk-blocked"]').text()).toContain("The bot is not in the server")

    api.findBotStanding.mockResolvedValue({status: 200, data: standing()})
    api.planDiscord.mockResolvedValue({status: 503, error: {code: "TargetSystemUnavailable"}, response: {status: 503}})
    ;({wrapper} = await mounted())
    expect(wrapper.get('[data-testid="bulk-failure"]').text()).toBe("Discord cannot be reached now; try again in a moment.")
  })
})
