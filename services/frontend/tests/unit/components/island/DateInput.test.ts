import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import DateInput from "@/components/island/DateInput.vue"

const field = (props: Record<string, unknown> = {}) =>
  mount(DateInput, {attachTo: document.body, props: {testid: "dob", ...props}})

const panel = () => document.querySelector("[data-testid='dob-panel']")
const openIt = async (wrapper: ReturnType<typeof field>) => {
  await wrapper.find(".island-date__open").trigger("click")
  await flushPromises()
}

afterEach(() => {
  document.body.innerHTML = ""
  vi.useRealTimers()
})

describe("the box a date is written in", () => {
  it("writes the day it was handed the way a reader writes it", () => {
    expect((field({modelValue: "2026-03-07"}).find("input").element).value).toBe("07/03/2026")
  })

  it("keeps the box empty where the value is not a day at all", () => {
    expect((field({modelValue: "whenever"}).find("input").element).value).toBe("")
    expect((field().find("input").element).value).toBe("")
  })

  it("takes a day pasted the way the api stores it", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("2000-01-01")

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["2000-01-01"])
    expect((wrapper.find("input").element).value).toBe("01/01/2000")
  })

  it("takes a day typed with slashes, dots or dashes", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("07/03/2026")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["2026-03-07"])

    await wrapper.find("input").setValue("7.3.2026")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["2026-03-07"])
  })

  it("holds its peace while a day is half typed", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("07/0")

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })

  it("empties the value where the box is emptied", async () => {
    const wrapper = field({modelValue: "2026-03-07"})

    await wrapper.find("input").setValue("")

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""])
  })

  it("refuses a day that month does not have", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("31/02/2026")

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })
})

describe("the calendar the island draws", () => {
  it("opens on the month of the day that was chosen", async () => {
    const wrapper = field({modelValue: "2026-03-07"})
    await openIt(wrapper)

    expect(panel()?.querySelector(".island-date__month")?.textContent).toBe("March 2026")
    expect(document.querySelector("[data-testid='dob-2026-03-07']")?.className)
      .toContain("island-date__day--on")
  })

  it("opens on this month where nothing is chosen", async () => {
    vi.setSystemTime(new Date(2026, 8, 22, 12))
    const wrapper = field()
    await openIt(wrapper)

    expect(panel()?.querySelector(".island-date__month")?.textContent).toBe("September 2026")
    expect(document.querySelector("[data-testid='dob-2026-09-22']")?.className)
      .toContain("island-date__day--today")
  })

  it("steps a month either way", async () => {
    const wrapper = field({modelValue: "2026-01-15"})
    await openIt(wrapper)

    await (document.querySelector("[data-testid='dob-back']") as HTMLElement).click()
    await flushPromises()
    expect(panel()?.querySelector(".island-date__month")?.textContent).toBe("December 2025")

    const on = document.querySelector("[data-testid='dob-on']") as HTMLElement
    on.click()
    on.click()
    await flushPromises()
    expect(panel()?.querySelector(".island-date__month")?.textContent).toBe("February 2026")
  })

  it("takes the day that was pressed, and shuts", async () => {
    const wrapper = field({modelValue: "2026-03-07"})
    await openIt(wrapper)

    ;(document.querySelector("[data-testid='dob-2026-03-19']") as HTMLElement).click()
    await flushPromises()

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["2026-03-19"])
    expect(panel()).toBeNull()
  })

  it("empties the field from the panel", async () => {
    const wrapper = field({modelValue: "2026-03-07"})
    await openIt(wrapper)

    ;(document.querySelector("[data-testid='dob-clear']") as HTMLElement).click()
    await flushPromises()

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""])
    expect(panel()).toBeNull()
    expect((wrapper.find("input").element).value).toBe("")
  })

  it("draws the days outside the month it is showing, and the weekend apart", async () => {
    const wrapper = field({modelValue: "2026-03-07"})
    await openIt(wrapper)

    expect(document.querySelector("[data-testid='dob-2026-02-23']")?.className)
      .toContain("island-date__day--outside")
    expect(document.querySelector("[data-testid='dob-2026-03-07']")?.className)
      .toContain("island-date__day--weekend")
  })

  it("puts a day outside what the form allows out of reach", async () => {
    const wrapper = field({modelValue: "2026-03-07", min: "2026-03-05", max: "2026-03-20"})
    await openIt(wrapper)

    const before = document.querySelector("[data-testid='dob-2026-03-04']") as HTMLButtonElement
    const after = document.querySelector("[data-testid='dob-2026-03-25']") as HTMLButtonElement
    const within = document.querySelector("[data-testid='dob-2026-03-10']") as HTMLButtonElement

    expect(before.disabled).toBe(true)
    expect(after.disabled).toBe(true)
    expect(within.disabled).toBe(false)
  })

  it("goes on a press outside it, and stays on a press within", async () => {
    const wrapper = field({modelValue: "2026-03-07"})
    await openIt(wrapper)

    const inside = panel() as HTMLElement
    inside.dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    await flushPromises()
    expect(panel()).not.toBeNull()

    wrapper.find("input").element.dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    await flushPromises()
    expect(panel()).not.toBeNull()

    document.dispatchEvent(new MouseEvent("pointerdown", {bubbles: true}))
    await flushPromises()
    expect(panel()).toBeNull()
  })

  it("goes on Escape, and stays on any other key", async () => {
    const wrapper = field()
    await openIt(wrapper)

    document.dispatchEvent(new KeyboardEvent("keydown", {key: "a"}))
    await flushPromises()
    expect(panel()).not.toBeNull()

    document.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
    await flushPromises()
    expect(panel()).toBeNull()
  })

  it("opens upward where the window has no room under the field", async () => {
    const tall = vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(
      {bottom: 900, height: 40, left: 20, top: 860, width: 300} as DOMRect)
    const wrapper = field()
    await openIt(wrapper)
    tall.mockRestore()

    expect(panel()?.className).toContain("island-date__panel--above")
  })

  it("says it is wrong where the form says so, and closes the way it opened", async () => {
    const wrapper = field({invalid: true})
    await openIt(wrapper)
    expect(panel()).not.toBeNull()

    await wrapper.find(".island-date__open").trigger("click")
    await flushPromises()

    expect(wrapper.find(".island-date").classes()).toContain("island-date--wrong")
    expect(panel()).toBeNull()
  })

  it("carries no name of its own where the field has none", async () => {
    const wrapper = mount(DateInput, {attachTo: document.body, props: {modelValue: "2026-03-07"}})
    await openIt(wrapper)

    expect(wrapper.find("input").attributes("data-testid")).toBeUndefined()
    expect(wrapper.find("button").attributes("data-testid")).toBeUndefined()
    const drawn = document.querySelector(".island-date__panel") as HTMLElement
    expect(drawn.querySelectorAll(".island-date__day").length).toBe(42)
    expect(drawn.querySelector("[data-testid]")).toBeNull()
  })

  it("keeps a press of its own rather than letting it reach the page", async () => {
    const wrapper = field({modelValue: "2026-03-07"})
    await openIt(wrapper)

    const drawn = panel() as HTMLElement
    const press = new MouseEvent("mousedown", {bubbles: true, cancelable: true})
    let reached = false
    document.addEventListener("mousedown", () => { reached = true }, {once: true})
    drawn.dispatchEvent(press)
    await flushPromises()

    expect(reached).toBe(false)
    expect(panel()).not.toBeNull()
  })
})
