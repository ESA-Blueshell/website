import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordPlaceFields from "@/domains/discord/island/DiscordPlaceFields.vue"
import {findCommitteeDiscord} from "@/services/api"
import {mountInApp, settle, unmountAll} from "../../../pages/helpers"

const api = vi.hoisted(() => ({findCommitteeDiscord: vi.fn(), listKeptRoles: vi.fn(), listCataloguedChannels: vi.fn(), listRoleOpenings: vi.fn()}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const channels = [
  {id: "10", name: "Committees", kind: "CATEGORY"},
  {id: "1", name: "sitecie", kind: "TEXT", category: "Committees"},
  {id: "2", name: "sitecie-voice", kind: "VOICE"},
  {id: "3", name: "sitecie-2023", kind: "TEXT", category: "Archive"},
]

describe("a committee's Discord on its form", () => {
  const wrappers: VueWrapper[] = []
  const mount = async (props: {committeeId: number | null, name: string, slug: string}) => {
    const wrapper = mountInApp(DiscordPlaceFields, {props: {...props, read: props.committeeId == null ? null : async () => (await findCommitteeDiscord({path: {id: props.committeeId!}})).data ?? null}})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }
  const choice = (wrapper: VueWrapper) => wrapper.emitted("update:modelValue")?.at(-1)?.[0]

  beforeEach(() => {
    vi.clearAllMocks()
    api.listKeptRoles.mockResolvedValue({status: 200, data: [{id: "900", name: "Sitecie", assignable: true}, {id: "905", name: "Admin", assignable: false}]})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: channels})
    api.listRoleOpenings.mockResolvedValue({status: 200, data: []})
    api.findCommitteeDiscord.mockResolvedValue({status: 200, data: {available: true, roleId: "900", roleName: "Sitecie", channels: [channels[1]]}})
  })

  afterEach(() => unmountAll(wrappers, "DiscordPlaceFields"))

  it("asks a new committee for a new role and a private channel by default, or links a role and channels instead", async () => {
    const wrapper = await mount({committeeId: null, name: "Pub Quiz", slug: "pub-quiz"})

    expect(choice(wrapper)).toEqual({roleId: null, createRole: true, channelIds: [], createChannel: "pub-quiz"})
    const picker = wrapper.findComponent({name: "SearchPicker"})
    expect(picker.props("options")).toEqual([{key: "__new__", label: "A new role, @Pub Quiz"}, {key: "900", label: "@Sitecie"}])
    picker.vm.$emit("pick", "900")
    wrapper.findComponent({name: "ChipPicker"}).vm.$emit("add", ["2"])
    wrapper.findComponent({name: "CheckBox"}).vm.$emit("update:modelValue", false)
    await settle()
    expect(wrapper.findComponent({name: "ChipPicker"}).props("chosen")).toEqual([{key: "2", label: "sitecie-voice", note: undefined}])
    expect(choice(wrapper)).toEqual({roleId: "900", createRole: false, channelIds: ["2"], createChannel: null})
    wrapper.findComponent({name: "ChipPicker"}).vm.$emit("remove", "2")
    await settle()
    expect(choice(wrapper)?.channelIds).toEqual([])
  })

  it("fills in the channels a picked role already has access to and unticks the new channel", async () => {
    api.listRoleOpenings.mockResolvedValue({status: 200, data: [
      {channel: channels[0], actual: "READ", differs: false},
      {channel: channels[1], actual: "READ", differs: false},
      {channel: channels[2], differs: false},
    ]})
    const wrapper = await mount({committeeId: null, name: "Pub Quiz", slug: "pub-quiz"})
    expect(wrapper.find('[data-testid="committee-edit-discord-already"]').exists()).toBe(false)

    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "900")
    await settle()

    expect(api.listRoleOpenings).toHaveBeenCalledWith({path: {roleId: "900"}})
    const already = wrapper.get('[data-testid="committee-edit-discord-already"]')
    expect(already.text()).toContain("already has access to sitecie.")
    // Drawn as a channel mark, glyph and all, as every channel on the site is.
    expect(already.find(".channel-mark").exists()).toBe(true)
    expect(wrapper.findAll(".chips__chip .channel-mark").map((one) => one.text())).toEqual(["sitecie"])
    await wrapper.get('[data-testid="committee-edit-discord-channel-picker-search"]').trigger("focus")
    await settle()
    expect([...document.querySelectorAll(".chips__row .channel-mark")].map((one) => one.textContent?.trim())).toEqual(["sitecie-voice"])
    expect(choice(wrapper)).toEqual({roleId: "900", createRole: false, channelIds: ["1"], createChannel: null})
  })

  it("shows the role an existing committee holds with the channels it opens, and offers no new role", async () => {
    const wrapper = await mount({committeeId: 7, name: "Sitecie", slug: " "})

    expect(api.findCommitteeDiscord).toHaveBeenCalledWith({path: {id: 7}})
    expect(wrapper.emitted("loaded")?.[0]?.[0]).toMatchObject({roleId: "900"})
    expect(wrapper.get('[data-testid="committee-edit-discord-role"]').text()).toContain("@Sitecie")
    expect(wrapper.findComponent({name: "SearchPicker"}).exists()).toBe(false)
    expect(choice(wrapper)).toEqual({roleId: null, createRole: false, channelIds: ["1"], createChannel: null})
    wrapper.findComponent({name: "CheckBox"}).vm.$emit("update:modelValue", true)
    await settle()
    expect(choice(wrapper)?.createChannel).toBeNull()
    expect(wrapper.findComponent({name: "CheckBox"}).props("label")).toBe("Create a new private channel #named after it")
  })

  it("stays hidden and asks nothing where Discord is away", async () => {
    api.findCommitteeDiscord.mockResolvedValue({status: 200, data: {available: false, channels: []}})
    api.listKeptRoles.mockResolvedValue({status: 200, data: []})
    const editing = await mount({committeeId: 7, name: "Sitecie", slug: "sitecie"})
    const adding = await mount({committeeId: null, name: "", slug: ""})

    expect(editing.find('[data-testid="committee-edit-discord"]').exists()).toBe(false)
    expect(adding.find('[data-testid="committee-edit-discord"]').exists()).toBe(false)
    expect(choice(adding) ?? null).toBeNull()
  })

  it("names a linked role by its id where Discord did not name it, and a new role after the committee", async () => {
    api.findCommitteeDiscord.mockResolvedValue({status: 200, data: {available: true, roleId: "900", channels: []}})
    const wrapper = await mount({committeeId: 7, name: "Sitecie", slug: "sitecie"})
    expect(wrapper.get('[data-testid="committee-edit-discord-role"]').text()).toContain("@900")
    const adding = await mount({committeeId: null, name: "  ", slug: "x"})
    expect(adding.findComponent({name: "SearchPicker"}).props("options")[0].label).toBe("A new role, @named after it")
  })
})
