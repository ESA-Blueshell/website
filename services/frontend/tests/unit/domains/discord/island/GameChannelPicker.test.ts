import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
import GameChannelPicker from "@/domains/discord/island/GameChannelPicker.vue"

const {mockChannels} = vi.hoisted(() => ({mockChannels: vi.fn()}))
vi.mock("@/domains/discord/adapters/channels", () => ({listGameRooms: mockChannels, gameRoomUrl: vi.fn()}))

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
    expect(picker(wrapper).props("removeLabel")("#chess")).toBe("Take away #chess")
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
    expect(wrapper.get("[data-label]").attributes("data-label")).toBe("Esports channels")
  })
})
