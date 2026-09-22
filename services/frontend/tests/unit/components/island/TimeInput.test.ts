import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import TimeInput from "@/components/island/TimeInput.vue"

const field = (props: Record<string, unknown> = {}) =>
  mount(TimeInput, {attachTo: document.body, props: {testid: "starts", ...props}})

const list = () => document.querySelector("[data-testid='starts-list']")
const openIt = async (wrapper: ReturnType<typeof field>) => {
  await wrapper.find(".island-time__open").trigger("click")
  await flushPromises()
}

afterEach(() => {
  document.body.innerHTML = ""
})

describe("a time of day", () => {
  it("shows what it was handed, and reads what is typed", async () => {
    const wrapper = field({modelValue: "19:30"})
    expect((wrapper.find("input").element).value).toBe("19:30")

    await wrapper.find("input").setValue("9:05")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["09:05"])

    await wrapper.find("input").setValue("2100")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["21:00"])
  })

  it("holds its peace while a time is half typed, and empties on an empty box", async () => {
    const wrapper = field({modelValue: "19:30"})

    await wrapper.find("input").setValue("19:")
    expect(wrapper.emitted("update:modelValue")).toBeUndefined()

    await wrapper.find("input").setValue("")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""])
  })

  it("refuses an hour or a minute no clock has", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("25:00")
    await wrapper.find("input").setValue("12:75")

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })

  it("offers the quarter hours, and as many as the step asks for", async () => {
    const wrapper = field({modelValue: "19:30"})
    await openIt(wrapper)
    expect(list()?.querySelectorAll("[role='option']").length).toBe(96)
    expect(document.querySelector("[data-testid='starts-19:30']")?.className)
      .toContain("island-time__row--on")

    document.body.innerHTML = ""
    const hourly = field({step: 60})
    await openIt(hourly)
    expect(list()?.querySelectorAll("[role='option']").length).toBe(24)
  })

  it("takes the time that was pressed, and shuts", async () => {
    const wrapper = field()
    await openIt(wrapper)

    ;(document.querySelector("[data-testid='starts-08:15']") as HTMLElement).click()
    await flushPromises()

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["08:15"])
    expect(list()).toBeNull()
  })

  it("opens where the time already chosen is", async () => {
    const wrapper = field({modelValue: "12:00"})
    await openIt(wrapper)

    expect((list() as HTMLElement).scrollTop).toBeGreaterThan(0)
  })

  it("goes on a press outside it and on Escape, and stays otherwise", async () => {
    const wrapper = field()
    await openIt(wrapper)

    ;(list() as HTMLElement).dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    document.dispatchEvent(new KeyboardEvent("keydown", {key: "a"}))
    await flushPromises()
    expect(list()).not.toBeNull()

    document.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
    await flushPromises()
    expect(list()).toBeNull()

    await openIt(wrapper)
    wrapper.find("input").element.dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    await flushPromises()
    expect(list()).not.toBeNull()

    document.dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    await flushPromises()
    expect(list()).toBeNull()
  })

  it("opens upward where the window has no room under it", async () => {
    const tall = vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(
      {bottom: 900, height: 40, left: 20, top: 860, width: 300} as DOMRect)
    const wrapper = field()
    await openIt(wrapper)
    tall.mockRestore()

    expect(list()?.className).toContain("island-time__list--above")
  })

  it("says it is wrong where the form says so, and carries no name where the field has none",
    async () => {
      const wrapper = field({invalid: true})
      expect(wrapper.find(".island-time").classes()).toContain("island-time--wrong")

      const bare = mount(TimeInput, {attachTo: document.body})
      await bare.find(".island-time__open").trigger("click")
      await flushPromises()

      expect(bare.find("input").attributes("data-testid")).toBeUndefined()
      expect(document.querySelector(".island-time__list [data-testid]")).toBeNull()
    })

  it("keeps a press of its own rather than letting it reach the page", async () => {
    const wrapper = field()
    await openIt(wrapper)

    let reached = false
    document.addEventListener("mousedown", () => { reached = true }, {once: true})
    ;(list() as HTMLElement).dispatchEvent(new MouseEvent("mousedown", {bubbles: true}))
    await flushPromises()

    expect(reached).toBe(false)
  })
})
