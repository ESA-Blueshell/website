import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
import GameOrganisersPicker from "@/domains/committees/island/GameOrganisersPicker.vue"
import {forgetCommittees} from "@/domains/committees"

const findCommittees = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCommittees: () => findCommittees(),
}))

const committee = (id: number, name: string, over: Record<string, unknown> = {}) =>
  ({id, name, slug: name.toLowerCase(), description: "", listed: true, archived: false, banner: null, gameCodes: [], ...over})

const throughField = {template: "<div><slot :control-id=\"'c'\" :label-id=\"'l'\" /></div>"}

const mountPicker = async (modelValue: unknown = []) => {
  const wrapper = shallowMount(GameOrganisersPicker, {props: {modelValue}, global: {stubs: {FormField: throughField}}})
  await flushPromises()
  return wrapper
}

const picker = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "SearchPicker"})

describe("the committees that organise events for a game", () => {
  beforeEach(() => {
    forgetCommittees()
    findCommittees.mockResolvedValue({data: [committee(1, "LanCie"), committee(2, "YapCie"), committee(3, "OldCie", {archived: true}), committee(4, "Board", {listed: false})]})
  })

  it("offers the committees that run and are not picked yet, and adds the one picked", async () => {
    const wrapper = await mountPicker([2])

    expect(picker(wrapper).props("options")).toEqual([{key: "1", label: "LanCie"}])
    picker(wrapper).vm.$emit("pick", "1")

    expect(wrapper.emitted("update:modelValue")).toEqual([[[2, 1]]])
  })

  it("keeps an archived committee already picked, drops one that is gone, and takes one away", async () => {
    const wrapper = await mountPicker([3, 99, 1])

    expect(wrapper.findAll(".game-organisers__one").map(one => one.text().replace(/\s*×$/u, ""))).toEqual(["OldCie", "LanCie"])
    await wrapper.get("[data-testid=game-organisers-3] button").trigger("click")

    expect(wrapper.emitted("update:modelValue")).toEqual([[[1]]])
  })

  it("picks nothing when the game has no committees yet", async () => {
    const wrapper = await mountPicker(null)

    expect(wrapper.find(".game-organisers").exists()).toBe(false)
    expect(picker(wrapper).props("options")).toHaveLength(2)
  })
})
