import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, shallowMount} from "@vue/test-utils"
import GameChannelPicker from "@/domains/discord/island/GameChannelPicker.vue"

const {mockChannels, mockEvery} = vi.hoisted(() => ({mockChannels: vi.fn(), mockEvery: vi.fn()}))
vi.mock("@/domains/discord/adapters/channels", () => ({listGameRooms: mockChannels, listEveryRoom: mockEvery, gameRoomUrl: vi.fn()}))
const {mockMake, mockStore} = vi.hoisted(() => ({mockMake: vi.fn(), mockStore: {commit: vi.fn()}}))
vi.mock("@/domains/discord/adapters/channelAccess", () => ({makeGameChannel: mockMake}))
vi.mock("@/plugins/store", () => ({default: mockStore}))

const throughField = {props: ["hint"], template: "<div :data-hint='hint'><slot :control-id=\"'c'\" :label-id=\"'l'\" /></div>"}

const CHESS = {id: "900", guildId: "324", name: "chess"}
const FIGHTING = {id: "901", guildId: "324", name: "fighting-games"}

const mountPicker = async (modelValue: unknown = []) => {
  const wrapper = shallowMount(GameChannelPicker, {props: {modelValue}, global: {stubs: {FormField: throughField}}})
  await flushPromises()
  return wrapper
}

const picker = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "ChipPicker"})

describe("GameChannelPicker", () => {
  beforeEach(() => {
    mockChannels.mockReset()
    mockChannels.mockResolvedValue([CHESS, FIGHTING])
    mockEvery.mockResolvedValue(null)
  })

  it("offers every other text channel after the category's own, under the category it is filed in", async () => {
    const VALORANT = {id: "910", guildId: "324", name: "bs-valorant", category: "Valorant"}
    mockEvery.mockResolvedValue([{...CHESS, category: "Games"}, VALORANT, {id: "911", guildId: "324", name: "rules", category: null}])
    const wrapper = await mountPicker([CHESS])

    expect(picker(wrapper).props("options")).toEqual([
      {key: "900", label: "chess"},
      {key: "901", label: "fighting-games"},
      {key: "910", label: "bs-valorant", note: "Valorant"},
      {key: "911", label: "rules", note: "No category"},
    ])
    picker(wrapper).vm.$emit("add", ["910"])
    expect(wrapper.emitted("update:modelValue")).toEqual([[[CHESS, {id: "910", guildId: "324", name: "bs-valorant"}]]])
  })

  it("offers the category's channels, and adds the ones picked with their server and name", async () => {
    const wrapper = await mountPicker([CHESS])

    expect(picker(wrapper).props("options")).toEqual([{key: "900", label: "chess"}, {key: "901", label: "fighting-games"}])
    expect(picker(wrapper).props("sigil")).toBe("#")
    picker(wrapper).vm.$emit("add", ["901", "gone"])
    picker(wrapper).vm.$emit("add", ["gone"])

    expect(wrapper.emitted("update:modelValue")).toEqual([[[CHESS, FIGHTING]]])
  })

  it("shows each chosen channel as a chip, and takes one away", async () => {
    const wrapper = await mountPicker([CHESS, FIGHTING])

    expect(picker(wrapper).props("chosen")).toEqual([{key: "900", label: "chess"}, {key: "901", label: "fighting-games"}])
    expect(picker(wrapper).props("chipTestid")("900")).toBe("game-channels-900")
    expect(picker(wrapper).props("removeLabel")("#chess")).toBe("Remove #chess")
    picker(wrapper).vm.$emit("remove", "900")

    expect(wrapper.emitted("update:modelValue")).toEqual([[[FIGHTING]]])
  })

  it("keeps the chosen channels, unchangeable, while the bot is away, and says so", async () => {
    mockChannels.mockResolvedValue(null)
    const wrapper = await mountPicker([CHESS])

    expect(picker(wrapper).props("disabled")).toBe(true)
    expect(picker(wrapper).props("chosen")).toEqual([{key: "900", label: "chess"}])
    expect(wrapper.find("[data-hint]").attributes("data-hint")).toContain("unavailable")
  })

  it("starts with nothing chosen when the game has no channels yet, under the label it is given", async () => {
    const wrapper = shallowMount(GameChannelPicker, {
      props: {modelValue: null, label: "Esports channels", emptyNote: "None left."},
      global: {stubs: {FormField: {props: ["label"], template: "<div :data-label='label'><slot :control-id=\"'c'\" :label-id=\"'l'\" /></div>"}}},
    })
    await flushPromises()

    expect(picker(wrapper).props("chosen")).toEqual([])
    expect(picker(wrapper).props("emptyNote")).toBe("None left.")
    expect(mockChannels).toHaveBeenLastCalledWith("GAMES")
    expect(wrapper.get("[data-label]").attributes("data-label")).toBe("Esports channels")
  })

  it("offers the esports category's channels where it picks a game's esports channels", async () => {
    shallowMount(GameChannelPicker, {props: {category: "ESPORTS"}, global: {stubs: {FormField: throughField}}})
    await flushPromises()

    expect(mockChannels).toHaveBeenLastCalledWith("ESPORTS")
  })
})

describe("making a game's channel", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockChannels.mockResolvedValue([CHESS])
    mockEvery.mockResolvedValue(null)
  })

  it("makes a channel named after the game and adds it", async () => {
    const VALO = {id: "902", guildId: "324", name: "valo"}
    mockMake.mockResolvedValueOnce({ok: true, saved: VALO})
    const wrapper = shallowMount(GameChannelPicker, {
      props: {modelValue: [CHESS], createName: " valo ", category: "ESPORTS"},
      global: {stubs: {FormField: throughField, CutButton: {props: ["testid", "disabled"], template: "<button :data-testid='testid' @click=\"$emit('click')\"><slot /></button>"}}},
    })
    await flushPromises()

    await wrapper.get("[data-testid=game-channels-make]").trigger("click")
    await flushPromises()
    expect(mockMake).toHaveBeenCalledWith("valo", "ESPORTS")
    expect(wrapper.emitted("update:modelValue")).toEqual([[[CHESS, VALO]]])

    mockMake.mockResolvedValueOnce({ok: false, reason: "Discord cannot be reached now; try again in a moment."})
    await wrapper.get("[data-testid=game-channels-make]").trigger("click")
    await flushPromises()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Discord cannot be reached now; try again in a moment.")
  })

  it("offers no making without a name", async () => {
    const wrapper = await mountPicker([CHESS])
    expect(wrapper.find("[data-testid=game-channels-make]").exists()).toBe(false)
  })
  it("draws each chosen channel and each one on offer as a channel mark", async () => {
    mockChannels.mockResolvedValue([CHESS, FIGHTING])
    mockEvery.mockResolvedValue(null)
    const wrapper = mount(GameChannelPicker, {props: {modelValue: [CHESS]}, global: {stubs: {FormField: throughField}}, attachTo: document.body})
    await flushPromises()

    expect(wrapper.findAll(".chips__chip .channel-mark").map((one) => one.text())).toEqual(["chess"])
    await wrapper.get("input").trigger("focus")
    await flushPromises()
    expect([...document.querySelectorAll(".chips__row .channel-mark")].map((one) => one.textContent?.trim())).toEqual(["fighting-games"])
    wrapper.unmount()
  })
})
