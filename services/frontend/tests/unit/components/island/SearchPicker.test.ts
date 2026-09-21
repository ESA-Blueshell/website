import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import SearchPicker from "@/components/island/SearchPicker.vue"

const options = (count: number) =>
  Array.from({length: count}, (_, at) => ({key: `k${at + 1}`, label: `Member ${at + 1}`}))

const picker = (count: number) =>
  mount(SearchPicker, {
    props: {options: options(count), testidPrefix: "pick"},
    attachTo: document.body,
  })

const open = async (wrapper: ReturnType<typeof picker>) => {
  await wrapper.find('[data-testid="pick-search"]').trigger("click")
  await new Promise(resolve => setTimeout(resolve, 0))
  await wrapper.vm.$nextTick()
}

const drawn = () => document.querySelectorAll('[data-testid^="pick-k"]').length

describe("SearchPicker", () => {
  it("draws every row of a short list, so tab reaches all of them", async () => {
    const wrapper = picker(20)

    await open(wrapper)

    expect(drawn()).toBe(20)
    wrapper.unmount()
  })

  it("draws a window of a long one rather than thousands of rows", async () => {
    const wrapper = picker(2400)

    await open(wrapper)

    const rows = drawn()
    expect(rows).toBeGreaterThan(0)
    expect(rows).toBeLessThan(60)
    wrapper.unmount()
  })

  it("keeps the full height under the window, so the scrollbar tells the truth", async () => {
    const wrapper = picker(2400)

    await open(wrapper)

    const list = document.querySelector('[data-testid="pick-list"]') as HTMLElement
    const pads = [...list.querySelectorAll("[data-picker-pad]")] as HTMLElement[]
    const padding = pads.reduce((sum, pad) => sum + Number.parseInt(pad.style.height, 10), 0)
    expect(padding).toBeGreaterThan(2400 * 20)
    wrapper.unmount()
  })

  it("narrows to what was typed", async () => {
    const wrapper = picker(2400)
    await open(wrapper)

    const field = wrapper.find('[data-testid="pick-search"]')
    await field.setValue("Member 1234")
    await wrapper.vm.$nextTick()

    expect(drawn()).toBe(1)
    wrapper.unmount()
  })
})

describe("SearchPicker's search", () => {
  const rows = [
    {key: "fr", label: "French", note: "FR", terms: ["France", "Française", "FRA"]},
    {key: "de", label: "German", note: "DE", terms: ["Germany", "Deutschland"]},
  ]

  const typed = async (into: string) => {
    const wrapper = mount(SearchPicker, {
      props: {options: rows, testidPrefix: "pick"},
      attachTo: document.body,
    })
    const field = wrapper.find('[data-testid="pick-search"]')
    await field.trigger("click")
    await field.setValue(into)
    await wrapper.vm.$nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))
    const list = document.querySelector('[data-testid="pick-list"]')
    const drawn = [...(list?.querySelectorAll(".picker__label") ?? [])].map(el => el.textContent)
    wrapper.unmount()
    return drawn
  }

  it("finds a row by a word it does not draw", async () => {
    expect(await typed("France")).toEqual(["French"])
    expect(await typed("deutschland")).toEqual(["German"])
  })

  it("ignores the accents on either side", async () => {
    expect(await typed("francaise")).toEqual(["French"])
  })

  it("still finds a row by what it says", async () => {
    expect(await typed("german")).toEqual(["German"])
    expect(await typed("fr")).toEqual(["French"])
  })
})
