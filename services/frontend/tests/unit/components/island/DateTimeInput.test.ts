import {afterEach, describe, expect, it} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import DateTimeInput from "@/components/island/DateTimeInput.vue"

const field = (props: Record<string, unknown> = {}) =>
  mount(DateTimeInput, {attachTo: document.body, props: {testid: "starts", ...props}})

afterEach(() => {
  document.body.innerHTML = ""
})

describe("a moment, a day and a time in one box", () => {
  it("splits what it was handed between its two halves", () => {
    const wrapper = field({modelValue: "2026-10-03T19:30"})
    const [day, time] = wrapper.findAll("input")

    expect(day!.element.value).toBe("03/10/2026")
    expect(time!.element.value).toBe("19:30")
  })

  it("tells the form nothing until both halves are there, then the moment they make", async () => {
    const wrapper = field()
    const [day, time] = wrapper.findAll("input")

    await day!.setValue("03/10/2026")
    expect(wrapper.emitted("update:modelValue")).toBeUndefined()

    await time!.setValue("19:30")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["2026-10-03T19:30"])
  })

  it("empties both halves when the form empties it", async () => {
    const wrapper = field({modelValue: "2026-10-03T19:30"})

    await wrapper.setProps({modelValue: ""})

    expect(wrapper.findAll("input").map(one => one.element.value)).toEqual(["", ""])
  })

  it("keeps its halves while the form holds something that is not a moment yet", async () => {
    const wrapper = field({modelValue: "2026-10-03T19:30"})

    await wrapper.setProps({modelValue: "half"})

    expect(wrapper.findAll("input")[1]!.element.value).toBe("19:30")
  })

  it("bars the days before the earliest moment, and hangs both panels from the shared box", async () => {
    const wrapper = field({min: "2026-10-03T12:00"})

    await wrapper.find(".island-date__open").trigger("click")
    await flushPromises()

    expect((document.querySelector("[data-testid='starts-day-2026-10-02']") as HTMLButtonElement).disabled).toBe(true)
    expect((document.querySelector("[data-testid='starts-day-2026-10-03']") as HTMLButtonElement).disabled).toBe(false)
    expect(wrapper.findAll(".island-date--bare, .island-time--bare")).toHaveLength(2)
  })

  it("says it is wrong where the form says so, and carries no names where the field has none", () => {
    expect(field({invalid: true}).find(".island-when").classes()).toContain("island-when--wrong")

    const bare = mount(DateTimeInput, {attachTo: document.body})
    expect(bare.find("[data-testid]").exists()).toBe(false)
  })
})
