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

const picker = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "SearchPicker"})

describe("GameChannelPicker", () => {
  beforeEach(() => {
    mockChannels.mockReset()
    mockChannels.mockResolvedValue([CHESS, FIGHTING])
  })

  it("offers the category's channels not yet chosen, and adds the one picked with its server and name", async () => {
    const wrapper = await mountPicker([CHESS])

    expect(picker(wrapper).props("options")).toEqual([{key: "901", label: "#fighting-games"}])
    picker(wrapper).vm.$emit("pick", "901")
    picker(wrapper).vm.$emit("pick", "gone")

    expect(wrapper.emitted("update:modelValue")).toEqual([[[CHESS, FIGHTING]]])
  })

  it("lists each chosen channel, and takes one away", async () => {
    const wrapper = await mountPicker([CHESS, FIGHTING])

    expect(wrapper.get("[data-testid=game-channels-900]").text()).toContain("#chess")
    await wrapper.get("[data-testid=game-channels-900] button").trigger("click")

    expect(wrapper.emitted("update:modelValue")).toEqual([[[FIGHTING]]])
  })

  it("keeps the chosen channels, unchangeable, while the bot is away, and says so", async () => {
    mockChannels.mockResolvedValue(null)
    const wrapper = await mountPicker([CHESS])

    expect(picker(wrapper).exists()).toBe(false)
    expect(wrapper.get("[data-testid=game-channels-900]").find("button").exists()).toBe(false)
    expect(wrapper.find("[data-hint]").attributes("data-hint")).toContain("unavailable")
  })

  it("starts with nothing chosen when the game has no channels yet", async () => {
    const wrapper = await mountPicker(null)

    expect(wrapper.find(".game-channels").exists()).toBe(false)
    expect(picker(wrapper).props("options")).toHaveLength(2)
  })
})
