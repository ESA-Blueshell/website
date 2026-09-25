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

const picker = (wrapper: Awaited<ReturnType<typeof mountPicker>>) => wrapper.findComponent({name: "ChipPicker"})

describe("the games an event names", () => {
  beforeEach(() => {
    forgetCasualGames()
    findCasualGames.mockResolvedValue({data: [game("CHESS", "Chess"), game("WORDLE", "Wordle"), game("DOTA_2", "Dota 2", true)]})
  })

  it("offers the games played, and adds the ones picked", async () => {
    const wrapper = await mountPicker(["WORDLE"])

    expect(picker(wrapper).props("options")).toEqual([{key: "CHESS", label: "Chess"}, {key: "WORDLE", label: "Wordle"}])
    picker(wrapper).vm.$emit("add", ["CHESS"])

    expect(wrapper.emitted("update:modelValue")).toEqual([[["WORDLE", "CHESS"]]])
  })

  it("keeps an archived game already named, drops a removed one, and takes one away", async () => {
    const wrapper = await mountPicker(["DOTA_2", "GONE", "CHESS"])

    expect(picker(wrapper).props("chosen")).toEqual([{key: "DOTA_2", label: "Dota 2"}, {key: "CHESS", label: "Chess"}])
    expect(picker(wrapper).props("chipTestid")("CHESS")).toBe("event-games-CHESS")
    expect(picker(wrapper).props("removeLabel")("Chess")).toBe("Stop naming Chess")
    picker(wrapper).vm.$emit("remove", "DOTA_2")

    expect(wrapper.emitted("update:modelValue")).toEqual([[["CHESS"]]])
  })

  it("names nothing when the event has no games yet", async () => {
    const wrapper = await mountPicker(null)

    expect(picker(wrapper).props("chosen")).toEqual([])
    expect(picker(wrapper).props("options")).toHaveLength(2)
  })
})
