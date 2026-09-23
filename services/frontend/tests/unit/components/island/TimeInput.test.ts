import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import TimeInput from "@/components/island/TimeInput.vue"

const field = (props: Record<string, unknown> = {}) =>
  mount(TimeInput, {attachTo: document.body, props: {testid: "starts", ...props}})

const list = () => document.querySelector("[data-testid='starts-panel']")
const press = async (id: string) => {
  ;(document.querySelector(`[data-testid='starts-${id}']`) as HTMLElement).click()
  await flushPromises()
}
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

  it("steps the hour by one and the minute by the step, around the clock", async () => {
    const wrapper = field({modelValue: "23:45"})
    await openIt(wrapper)

    await press("hour-up")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["00:45"])
    await wrapper.setProps({modelValue: "00:45"})

    await press("minute-up")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["01:00"])
    await wrapper.setProps({modelValue: "00:00"})

    await press("minute-down")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["23:45"])
    await press("hour-down")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["22:45"])
    expect(document.querySelector("[data-testid='starts-hour']")?.textContent).toBe("22")
  })

  it("lands a minute off the step on the step first, either way", async () => {
    const wrapper = field({modelValue: "19:07", step: 15})
    await openIt(wrapper)

    await press("minute-up")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["19:15"])
    await press("minute-down")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["19:00"])

    document.body.innerHTML = ""
    const down = field({modelValue: "19:07", step: 15})
    await openIt(down)
    await press("minute-down")
    expect(down.emitted("update:modelValue")?.at(-1)).toEqual(["19:00"])
  })

  it("names the step it moves by, and steps from now on the step where it holds nothing", async () => {
    vi.useFakeTimers({now: new Date(2026, 8, 21, 19, 38), toFake: ["Date"]})
    const wrapper = field({step: 1})
    await openIt(wrapper)

    expect(document.querySelector("[data-testid='starts-minute-up']")?.getAttribute("aria-label")).toBe("1 minute later")
    expect(document.querySelector("[data-testid='starts-minute']")?.textContent).toBe("38")
    await press("hour-up")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["20:38"])
    vi.useRealTimers()
  })

  it("sets now on the step, or clears, and shuts either way", async () => {
    vi.useFakeTimers({now: new Date(2026, 8, 21, 19, 38), toFake: ["Date"]})
    const wrapper = field({modelValue: "08:00"})
    await openIt(wrapper)

    await press("now")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["19:30"])
    expect(list()).toBeNull()

    await openIt(wrapper)
    await press("clear")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""])
    expect(wrapper.find("input").element.value).toBe("")
    expect(list()).toBeNull()
    vi.useRealTimers()
  })

  it("hangs from the box it is handed, as wide as that box", async () => {
    const shared = document.createElement("div")
    document.body.append(shared)
    vi.spyOn(shared, "getBoundingClientRect").mockReturnValue({bottom: 100, height: 40, left: 10, top: 60, width: 420} as DOMRect)
    const wrapper = field({anchorTo: shared, bare: true})
    await openIt(wrapper)

    expect(wrapper.find(".island-time").classes()).toContain("island-time--bare")
    expect((list() as HTMLElement).style.width).toBe("420px")
    expect((list() as HTMLElement).style.left).toBe("10px")
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

    expect(list()?.className).toContain("island-time__panel--above")
  })

  it("says it is wrong where the form says so, and carries no name where the field has none",
    async () => {
      const wrapper = field({invalid: true})
      expect(wrapper.find(".island-time").classes()).toContain("island-time--wrong")

      const bare = mount(TimeInput, {attachTo: document.body})
      await bare.find(".island-time__open").trigger("click")
      await flushPromises()

      expect(bare.find("input").attributes("data-testid")).toBeUndefined()
      expect(document.querySelector(".island-time__panel [data-testid]")).toBeNull()
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
