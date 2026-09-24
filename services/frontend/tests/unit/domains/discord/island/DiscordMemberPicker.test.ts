import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, shallowMount} from "@vue/test-utils"
import DiscordMemberPicker from "@/domains/discord/island/DiscordMemberPicker.vue"

const {mockSearch, mockUnclaimed} = vi.hoisted(() => ({mockSearch: vi.fn(), mockUnclaimed: vi.fn()}))
vi.mock("@/domains/discord/adapters/members", () => ({searchServerMembers: mockSearch, listUnclaimedMembers: mockUnclaimed}))

const nelly = {id: "803", name: "Nelly B", username: "nelly", avatar: "https://cdn/nelly.png"}
const anna = {id: "804", name: "Anna", username: "annie", avatar: "https://cdn/anna.png"}
const bea = {id: "805", name: "Bea", username: "bee", avatar: "https://cdn/bea.png"}
const throughField = {template: "<div><slot :control-id=\"'c'\" :label-id=\"'l'\" /></div>"}

const mountPicker = async (props: Record<string, unknown> = {}) => {
  const wrapper = shallowMount(DiscordMemberPicker, {
    props: {modelValue: "", ...props},
    global: {stubs: {FormField: throughField}},
  })
  await flushPromises()
  return wrapper
}

const picker = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "SearchPicker"})
const textField = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "FormControl"})

describe("DiscordMemberPicker", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockSearch.mockReset()
    mockSearch.mockResolvedValue([])
    mockUnclaimed.mockReset()
    mockUnclaimed.mockResolvedValue([anna, bea])
  })

  it("is a picker over the server once the api says its bot is there", async () => {
    const wrapper = await mountPicker()

    expect(mockSearch).toHaveBeenCalledWith("")
    expect(picker(wrapper).exists()).toBe(true)
    expect(textField(wrapper).exists()).toBe(false)
  })

  it("stays the text field without the bot, and a typed name links nobody", async () => {
    mockSearch.mockResolvedValue(null)
    const wrapper = await mountPicker({modelValue: "old#0001", discordId: "803"})

    expect(picker(wrapper).exists()).toBe(false)
    textField(wrapper).vm.$emit("update:modelValue", "nelly")
    textField(wrapper).vm.$emit("update:modelValue", null)

    expect(wrapper.emitted("update:modelValue")).toEqual([["nelly"], [""]])
    expect(wrapper.emitted("update:discordId")).toEqual([[null], [null]])
  })

  it("shows each member found with their avatar, server name and username, and hands back who was picked", async () => {
    const wrapper = await mountPicker()
    mockSearch.mockResolvedValue([nelly])

    picker(wrapper).vm.$emit("search", "nel")
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(mockSearch).toHaveBeenLastCalledWith("nel")
    expect(picker(wrapper).props("options")).toEqual([{key: "803", label: "Nelly B", note: "@nelly", avatar: "https://cdn/nelly.png"}])
    picker(wrapper).vm.$emit("pick", "803")
    picker(wrapper).vm.$emit("pick", "unknown")
    expect(wrapper.emitted("update:modelValue")).toEqual([["Nelly B"]])
    expect(wrapper.emitted("update:discordId")).toEqual([["803"]])
  })

  it("searches the name already typed first, but not over a member already linked", async () => {
    expect(picker(await mountPicker({modelValue: "nelly#0001"})).props("firstSearch")).toBe("nelly#0001")
    expect(picker(await mountPicker({modelValue: ""})).props("firstSearch")).toBeUndefined()

    const linked = await mountPicker({modelValue: "Nelly B", discordId: "803"})
    expect(picker(linked).props("firstSearch")).toBeUndefined()
    expect(picker(linked).props("selectedKey")).toBe("803")
    expect(picker(linked).props("options")).toEqual([{key: "803", label: "Nelly B"}])
  })

  it("keeps a newer answer over an older one, and falls back when Discord stops answering", async () => {
    const wrapper = await mountPicker({discordId: "803", modelValue: "Nelly B"})
    let answerOld: (value: unknown) => void = () => {}
    mockSearch.mockImplementationOnce(() => new Promise(resolve => { answerOld = resolve }))
    mockSearch.mockResolvedValueOnce([nelly])

    picker(wrapper).vm.$emit("search", "ne")
    await vi.advanceTimersByTimeAsync(300)
    picker(wrapper).vm.$emit("search", "nel")
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    answerOld([])
    await flushPromises()
    expect(picker(wrapper).props("options")).toHaveLength(1)

    mockSearch.mockResolvedValueOnce(null)
    picker(wrapper).vm.$emit("search", "nell")
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(textField(wrapper).exists()).toBe(true)
    wrapper.unmount()
  })

  it("says in the list that somebody is not in the server only when a search found nobody", async () => {
    const wrapper = await mountPicker()
    const missing = () => picker(wrapper).vm.$slots.missing

    picker(wrapper).vm.$emit("search", "n")
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(mockSearch).toHaveBeenCalledTimes(1)
    expect(missing()).toBeUndefined()

    mockSearch.mockResolvedValue([])
    picker(wrapper).vm.$emit("search", "zz")
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(missing()).toBeDefined()

    mockSearch.mockResolvedValue([nelly])
    picker(wrapper).vm.$emit("search", "nel")
    await wrapper.vm.$nextTick()
    expect(missing()).toBeUndefined()
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(missing()).toBeUndefined()
  })

  it("points somebody who is not in the server to its invite, and asks them to search again", async () => {
    const wrapper = mount(DiscordMemberPicker, {
      props: {modelValue: ""},
      global: {stubs: {FormField: throughField}},
      attachTo: document.body,
    })
    await flushPromises()
    await wrapper.find('[data-testid="discord-picker-search"]').setValue("zz")
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    const said = document.querySelector('[data-testid="discord-picker-missing"]') as HTMLElement
    expect(said.textContent).toContain("It seems you're not in our Discord server yet.")
    expect(said.querySelector("a")?.getAttribute("href")).toBe("http://localhost:3000/api/discord/invite/welcome")
    wrapper.unmount()
  })

  it("opens on everybody nobody has linked, read once, and narrows it for a single letter", async () => {
    const wrapper = await mountPicker()

    picker(wrapper).vm.$emit("opened")
    await flushPromises()
    picker(wrapper).vm.$emit("opened")
    await flushPromises()
    expect(mockUnclaimed).toHaveBeenCalledTimes(1)
    expect((picker(wrapper).props("options") as {label: string}[]).map(one => one.label)).toEqual(["Anna", "Bea"])

    picker(wrapper).vm.$emit("search", "b")
    await wrapper.vm.$nextTick()
    expect((picker(wrapper).props("options") as {label: string}[]).map(one => one.label)).toEqual(["Bea"])
    picker(wrapper).vm.$emit("search", "n")
    await wrapper.vm.$nextTick()
    expect((picker(wrapper).props("options") as {label: string}[]).map(one => one.label)).toEqual(["Anna"])
  })

  it("keeps a search's answer over the unclaimed list arriving late, and opens empty without one", async () => {
    let listed: (value: unknown) => void = () => {}
    mockUnclaimed.mockImplementationOnce(() => new Promise(resolve => { listed = resolve }))
    const wrapper = await mountPicker()
    mockSearch.mockResolvedValue([nelly])

    picker(wrapper).vm.$emit("opened")
    picker(wrapper).vm.$emit("search", "nel")
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    listed([anna])
    await flushPromises()
    expect((picker(wrapper).props("options") as {label: string}[]).map(one => one.label)).toEqual(["Nelly B"])

    mockUnclaimed.mockResolvedValue(null)
    const offline = await mountPicker()
    picker(offline).vm.$emit("opened")
    await flushPromises()
    expect(picker(offline).props("options")).toEqual([])
  })

  it("takes the choice away when the box is emptied and left", async () => {
    const wrapper = await mountPicker({modelValue: "Nelly B", discordId: "803"})
    picker(wrapper).vm.$emit("opened")
    await flushPromises()

    picker(wrapper).vm.$emit("clear")
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("update:modelValue")).toEqual([[""]])
    expect(wrapper.emitted("update:discordId")).toEqual([[null]])
    expect((picker(wrapper).props("options") as {label: string}[]).map(one => one.label)).toEqual(["Nelly B", "Anna", "Bea"])
  })
})

