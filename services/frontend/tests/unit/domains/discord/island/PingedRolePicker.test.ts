import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
import PingedRolePicker from "@/domains/discord/island/PingedRolePicker.vue"

const {mockRoles} = vi.hoisted(() => ({mockRoles: vi.fn()}))
vi.mock("@/domains/discord/adapters/roles", () => ({listServerRoles: mockRoles}))

const throughField = {props: ["hint"], template: "<div :data-hint='hint'><slot :control-id=\"'c'\" :label-id=\"'l'\" /></div>"}

const mountPicker = async (modelValue: unknown = []) => {
  const wrapper = shallowMount(PingedRolePicker, {props: {modelValue}, global: {stubs: {FormField: throughField}}})
  await flushPromises()
  return wrapper
}

const picker = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "ChipPicker"})

describe("PingedRolePicker", () => {
  beforeEach(() => {
    mockRoles.mockReset()
    mockRoles.mockResolvedValue([{id: "901", name: "Gamers"}, {id: "902", name: "Board"}])
  })

  it("offers the server's roles, and adds the ones picked with their name", async () => {
    const wrapper = await mountPicker([{id: "902", name: "Board"}])

    expect(picker(wrapper).props("options")).toEqual([{key: "901", label: "Gamers"}, {key: "902", label: "Board"}])
    expect(picker(wrapper).props("sigil")).toBe("@")
    picker(wrapper).vm.$emit("add", ["901", "gone"])
    picker(wrapper).vm.$emit("add", ["gone"])

    expect(wrapper.emitted("update:modelValue")).toEqual([[[{id: "902", name: "Board"}, {id: "901", name: "Gamers"}]]])
  })

  it("shows each chosen role as a chip, and takes one away", async () => {
    const wrapper = await mountPicker([{id: "902", name: "Board"}, {id: "901", name: "Gamers"}])

    expect(picker(wrapper).props("chosen")).toEqual([{key: "902", label: "Board"}, {key: "901", label: "Gamers"}])
    expect(picker(wrapper).props("chipTestid")("902")).toBe("pinged-roles-902")
    expect(picker(wrapper).props("removeLabel")("@Board")).toBe("Stop pinging @Board")
    picker(wrapper).vm.$emit("remove", "902")

    expect(wrapper.emitted("update:modelValue")).toEqual([[[{id: "901", name: "Gamers"}]]])
  })

  it("keeps the chosen roles, unchangeable, while the bot is away, and says so", async () => {
    mockRoles.mockResolvedValue(null)
    const wrapper = await mountPicker([{id: "902", name: "Board"}])

    expect(picker(wrapper).props("disabled")).toBe(true)
    expect(wrapper.find("[data-hint]").attributes("data-hint")).toContain("unavailable")
  })

  it("starts with nothing chosen when the event has no roles yet", async () => {
    const wrapper = await mountPicker(null)

    expect(picker(wrapper).props("chosen")).toEqual([])
    expect(picker(wrapper).props("options")).toHaveLength(2)
  })
})
