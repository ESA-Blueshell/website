import {afterEach, describe, expect, it} from "vitest"
import {defineComponent, h, nextTick, ref} from "vue"
import {mount, type VueWrapper} from "@vue/test-utils"
import ChipPicker from "@/components/island/ChipPicker.vue"

const CHANNELS = [
  {key: "1", label: "chess"},
  {key: "2", label: "valorant", note: "Esports"},
  {key: "3", label: "rocket-league"},
  {key: "4", label: "Pokémon"},
]

/** The picker as a page holds it: the chosen keys live outside and follow what it says. */
const host = (props: Record<string, unknown> = {}, start: string[] = []) => defineComponent({
  setup() {
    const keys = ref<string[]>(start)
    return () => h(ChipPicker, {
      options: CHANNELS,
      chosen: keys.value.map(key => CHANNELS.find(one => one.key === key)!),
      sigil: "#",
      testidPrefix: "pick",
      onAdd: (added: string[]) => { keys.value = [...keys.value, ...added] },
      onRemove: (key: string) => { keys.value = keys.value.filter(one => one !== key) },
      ...props,
    })
  },
})

let wrapper: VueWrapper
const mountHost = (props: Record<string, unknown> = {}, start: string[] = []) => {
  wrapper = mount(host(props, start), {attachTo: document.body})
  return wrapper
}
afterEach(() => wrapper?.unmount())

const search = () => wrapper.get("[data-testid=pick-search]")
const listed = () => [...document.querySelectorAll("[data-testid=pick-list] .chips__label")].map(one => one.textContent)
const chips = () => wrapper.findAll(".chips__chip-label").map(one => one.text())
const settle = async () => { await nextTick(); await nextTick() }

const paste = async (text: string) => {
  const event = new Event("paste", {bubbles: true, cancelable: true}) as ClipboardEvent
  Object.defineProperty(event, "clipboardData", {value: {getData: () => text}})
  search().element.dispatchEvent(event)
  await settle()
  return event
}

describe("ChipPicker", () => {
  it("draws each choice as a chip in the field, which its cross takes away", async () => {
    mountHost({}, ["1", "3"])

    expect(chips()).toEqual(["#chess", "#rocket-league"])
    expect(wrapper.find("[data-testid=pick-chip-1]").exists()).toBe(true)
    expect(search().attributes("placeholder")).toBe("")

    await wrapper.get("[aria-label='Take #chess away']").trigger("click")

    expect(chips()).toEqual(["#rocket-league"])
  })

  it("names chips with the test id the page gives them", () => {
    mountHost({chipTestid: (key: string) => `channels-${key}`}, ["2"])

    expect(wrapper.find("[data-testid=channels-2]").text()).toContain("#valorant")
  })

  it("offers what is not chosen yet, and finds the same with or without a mark in front", async () => {
    mountHost({}, ["1"])
    await search().trigger("focus")
    await settle()

    expect(listed()).toEqual(["#valorant", "#rocket-league", "#Pokémon"])

    for (const asked of ["rock", "#rock", "@rock"]) {
      await search().setValue(asked)
      expect(listed(), asked).toEqual(["#rocket-league"])
    }
    await search().setValue("pokemon")
    expect(listed()).toEqual(["#Pokémon"])
    await search().setValue("esports")
    expect(listed()).toEqual(["#valorant"])
  })

  it("picks from the list with the arrow keys and Enter, and with a press", async () => {
    mountHost()
    await search().trigger("focus")
    await search().trigger("keydown", {key: "ArrowDown"})
    await search().trigger("keydown", {key: "ArrowDown"})
    await search().trigger("keydown", {key: "ArrowUp"})
    await search().trigger("keydown", {key: "Enter"})
    await settle()

    expect(chips()).toEqual(["#valorant"])

    ;(document.querySelector("[data-testid=pick-3]") as HTMLElement).click()
    await settle()

    expect(chips()).toEqual(["#valorant", "#rocket-league"])
  })

  it("makes a chip of a typed name closed with a comma only when it is on the list", async () => {
    mountHost()
    await search().setValue("#CHESS")
    await search().trigger("keydown", {key: ","})
    await settle()

    expect(chips()).toEqual(["#chess"])
    expect((search().element as HTMLInputElement).value).toBe("")

    await search().setValue("tetris")
    await search().trigger("keydown", {key: ","})
    await settle()

    expect(chips()).toEqual(["#chess"])
    expect((search().element as HTMLInputElement).value).toBe("tetris")
    expect(wrapper.get("[data-testid=pick-refused]").text()).toBe("Not on the list: tetris")
  })

  it("takes a comma a phone keyboard types into the text", async () => {
    mountHost()
    await search().setValue("valorant,")
    await settle()

    expect(chips()).toEqual(["#valorant"])
  })

  it("makes Enter on an exact name a chip, whatever is lit in the list", async () => {
    mountHost()
    await search().setValue("rocket-league")
    await search().trigger("keydown", {key: "Enter"})
    await settle()

    expect(chips()).toEqual(["#rocket-league"])
  })

  it("turns a pasted run of names into a chip each and leaves the unknown ones as text", async () => {
    mountHost({}, ["1"])

    const event = await paste("#valorant #rocket-league, chess\nminecraft")

    expect(event.defaultPrevented).toBe(true)
    expect(chips()).toEqual(["#chess", "#valorant", "#rocket-league"])
    expect((search().element as HTMLInputElement).value).toBe("minecraft")
    expect(wrapper.get("[data-testid=pick-refused]").text()).toBe("Not on the list: minecraft")
  })

  it("leaves a pasted single word to the text", async () => {
    mountHost()

    const event = await paste("chess")

    expect(event.defaultPrevented).toBe(false)
    expect(chips()).toEqual([])
  })

  it("takes the last chip away with Backspace in an empty field", async () => {
    mountHost({}, ["1", "2"])
    await search().trigger("keydown", {key: "Backspace"})
    await settle()

    expect(chips()).toEqual(["#chess"])

    await search().setValue("x")
    await search().trigger("keydown", {key: "Backspace"})
    expect(chips()).toEqual(["#chess"])
  })

  it("closes on Escape and on a press elsewhere", async () => {
    mountHost()
    await search().trigger("focus")
    await settle()
    expect(listed()).toHaveLength(4)

    await search().trigger("keydown", {key: "Escape"})
    await settle()
    expect(listed()).toHaveLength(0)

    await search().trigger("focus")
    await settle()
    document.body.dispatchEvent(new Event("pointerdown", {bubbles: true}))
    await settle()
    expect(listed()).toHaveLength(0)
  })

  it("says so when nothing answers, and when everything is chosen", async () => {
    mountHost({}, [])
    await search().setValue("zzz")
    await settle()
    expect(document.querySelector("[data-testid=pick-no-matches]")?.textContent?.trim()).toBe("Nothing answers to that.")
    wrapper.unmount()

    mountHost({emptyNote: "Every channel is in."}, ["1", "2", "3", "4"])
    expect(wrapper.get("[data-testid=pick-none]").text()).toBe("Every channel is in.")
  })

  it("keeps its chips but offers nothing while disabled", async () => {
    mountHost({disabled: true}, ["1"])
    await search().trigger("focus")
    await settle()

    expect(chips()).toEqual(["#chess"])
    expect(wrapper.find(".chips__chip-remove").exists()).toBe(false)
    expect(listed()).toHaveLength(0)
  })

  it("hangs its list above the field where there is no room below", async () => {
    mountHost()
    const field = wrapper.get(".chips__field").element as HTMLElement
    field.getBoundingClientRect = () => ({top: 700, bottom: 740, left: 10, width: 300, height: 40, right: 310, x: 10, y: 700, toJSON: () => ({})})
    await search().trigger("focus")
    await settle()

    expect(document.querySelector("[data-testid=pick-list]")?.classList.contains("chips__list--above")).toBe(true)
  })
})
