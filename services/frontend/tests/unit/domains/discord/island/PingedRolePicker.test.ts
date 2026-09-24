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

const picker = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "SearchPicker"})

describe("PingedRolePicker", () => {
  beforeEach(() => {
    mockRoles.mockReset()
    mockRoles.mockResolvedValue([{id: "901", name: "Gamers"}, {id: "902", name: "Board"}])
  })

  it("offers the server's roles not yet chosen, and adds the one picked with its name", async () => {
    const wrapper = await mountPicker([{id: "902", name: "Board"}])

    expect(picker(wrapper).props("options")).toEqual([{key: "901", label: "Gamers"}])
    picker(wrapper).vm.$emit("pick", "901")
    picker(wrapper).vm.$emit("pick", "gone")

    expect(wrapper.emitted("update:modelValue")).toEqual([[[{id: "902", name: "Board"}, {id: "901", name: "Gamers"}]]])
  })

  it("lists each chosen role, and takes one away", async () => {
    const wrapper = await mountPicker([{id: "902", name: "Board"}, {id: "901", name: "Gamers"}])

    expect(wrapper.get("[data-testid=pinged-roles-902]").text()).toContain("@Board")
    await wrapper.get("[data-testid=pinged-roles-902] button").trigger("click")

    expect(wrapper.emitted("update:modelValue")).toEqual([[[{id: "901", name: "Gamers"}]]])
  })

  it("keeps the chosen roles, unchangeable, while the bot is away, and says so", async () => {
    mockRoles.mockResolvedValue(null)
    const wrapper = await mountPicker([{id: "902", name: "Board"}])

    expect(picker(wrapper).exists()).toBe(false)
    expect(wrapper.get("[data-testid=pinged-roles-902]").find("button").exists()).toBe(false)
    expect(wrapper.find("[data-hint]").attributes("data-hint")).toContain("unavailable")
  })

  it("starts with nothing chosen when the event has no roles yet", async () => {
    const wrapper = await mountPicker(null)

    expect(wrapper.find(".pinged-roles").exists()).toBe(false)
    expect(picker(wrapper).props("options")).toHaveLength(2)
  })
})
