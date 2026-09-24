import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
import EventGamesPicker from "@/domains/games/island/EventGamesPicker.vue"
import {forgetCasualGames} from "@/domains/games"

const findCasualGames = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCasualGames: () => findCasualGames(),
}))

const game = (code: string, name: string, archived = false) =>
  ({code, name, slug: code.toLowerCase(), accent: null, intro: null, banner: null, icon: null, sortIndex: 0, archived, inCompetition: false, channels: []})

const throughField = {template: "<div><slot :control-id=\"'c'\" :label-id=\"'l'\" /></div>"}

const mountPicker = async (modelValue: unknown = []) => {
  const wrapper = shallowMount(EventGamesPicker, {props: {modelValue}, global: {stubs: {FormField: throughField}}})
  await flushPromises()
  return wrapper
}

const picker = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "SearchPicker"})

describe("the games an event names", () => {
  beforeEach(() => {
    forgetCasualGames()
    findCasualGames.mockResolvedValue({data: [game("CHESS", "Chess"), game("WORDLE", "Wordle"), game("DOTA_2", "Dota 2", true)]})
  })

  it("offers the games played that are not named yet, and adds the one picked", async () => {
    const wrapper = await mountPicker(["WORDLE"])

    expect(picker(wrapper).props("options")).toEqual([{key: "CHESS", label: "Chess"}])
    picker(wrapper).vm.$emit("pick", "CHESS")

    expect(wrapper.emitted("update:modelValue")).toEqual([[["WORDLE", "CHESS"]]])
  })

  it("keeps an archived game already named, drops a removed one, and takes one away", async () => {
    const wrapper = await mountPicker(["DOTA_2", "GONE", "CHESS"])

    expect(wrapper.findAll(".event-games__game").map(one => one.text().replace(/\s*×$/u, ""))).toEqual(["Dota 2", "Chess"])
    await wrapper.get("[data-testid=event-games-DOTA_2] button").trigger("click")

    expect(wrapper.emitted("update:modelValue")).toEqual([[["CHESS"]]])
  })

  it("names nothing when the event has no games yet", async () => {
    const wrapper = await mountPicker(null)

    expect(wrapper.find(".event-games").exists()).toBe(false)
    expect(picker(wrapper).props("options")).toHaveLength(2)
  })
})
