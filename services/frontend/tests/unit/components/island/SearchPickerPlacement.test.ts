import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import SearchPicker from "@/components/island/SearchPicker.vue"

const rows = (count: number) =>
  Array.from({length: count}, (_, at) => ({key: `k${at + 1}`, label: `Row ${at + 1}`}))

const picker = (props: Record<string, unknown> = {}) =>
  mount(SearchPicker, {
    props: {options: rows(400), testidPrefix: "pick", ...props},
    attachTo: document.body,
  })

const open = async (wrapper: ReturnType<typeof picker>) => {
  await wrapper.find('[data-testid="pick-search"]').trigger("click")
  await new Promise(resolve => setTimeout(resolve, 0))
  await wrapper.vm.$nextTick()
}

const list = () => document.querySelector('[data-testid="pick-list"]') as HTMLElement | null

/** jsdom lays nothing out, so the field says where it is and how much room is under it. */
const standingAt = (top: number, height = 40) => {
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue({
    top, bottom: top + height, left: 12, right: 212, width: 200, height,
    x: 12, y: top, toJSON: () => ({}),
  } as DOMRect)
}

afterEach(() => vi.restoreAllMocks())

describe("where the list is drawn", () => {
  it("hangs under the field when there is room", async () => {
    window.innerHeight = 800
    standingAt(100)
    const wrapper = picker()

    await open(wrapper)

    expect(list()?.style.top).toBe("140px")
    expect(list()?.classList.contains("picker__list--above")).toBe(false)
    wrapper.unmount()
  })

  it("opens upward where there is none", async () => {
    window.innerHeight = 300
    standingAt(250)
    const wrapper = picker()

    await open(wrapper)

    expect(list()?.style.top).toBe("250px")
    expect(list()?.classList.contains("picker__list--above")).toBe(true)
    wrapper.unmount()
  })

  it("is only as tall as the room it opened into", async () => {
    window.innerHeight = 400
    standingAt(100)
    const wrapper = picker()

    await open(wrapper)

    expect(list()?.style.maxHeight).toBe("240px")
    wrapper.unmount()
  })

  it("is drawn again when the page moves under it", async () => {
    window.innerHeight = 800
    standingAt(100)
    const wrapper = picker()
    await open(wrapper)

    standingAt(300)
    window.dispatchEvent(new Event("resize"))
    await wrapper.vm.$nextTick()

    expect(list()?.style.top).toBe("340px")
    wrapper.unmount()
  })
})

describe("a press away from the field", () => {
  it("takes the list down", async () => {
    const wrapper = picker()
    await open(wrapper)
    expect(list()).not.toBeNull()

    document.body.dispatchEvent(new Event("pointerdown", {bubbles: true}))
    await wrapper.vm.$nextTick()

    expect(list()).toBeNull()
    wrapper.unmount()
  })

  it("leaves it up when the press is on the list itself", async () => {
    const wrapper = picker()
    await open(wrapper)

    list()?.dispatchEvent(new Event("pointerdown", {bubbles: true}))
    await wrapper.vm.$nextTick()

    expect(list()).not.toBeNull()
    wrapper.unmount()
  })

  it("leaves it up when the press is on the field", async () => {
    const wrapper = picker()
    await open(wrapper)

    wrapper.find('[data-testid="pick-search"]').element
      .dispatchEvent(new Event("pointerdown", {bubbles: true}))
    await wrapper.vm.$nextTick()

    expect(list()).not.toBeNull()
    wrapper.unmount()
  })
})

describe("a list longer than a window", () => {
  it("draws a window of rows with the rest as height above and below", async () => {
    const wrapper = picker()
    await open(wrapper)

    const pads = [...(list()?.querySelectorAll("[data-picker-pad]") ?? [])] as HTMLElement[]
    const drawn = list()?.querySelectorAll('[data-testid^="pick-k"]').length ?? 0

    expect(drawn).toBeLessThan(400)
    expect(pads.length).toBeGreaterThan(0)
    wrapper.unmount()
  })

  it("moves the window as the list is scrolled", async () => {
    const wrapper = picker()
    await open(wrapper)
    const box = list() as HTMLElement

    const first = box.querySelector('[data-testid^="pick-k"]')?.getAttribute("data-testid")
    box.scrollTop = 900
    box.dispatchEvent(new Event("scroll"))
    await wrapper.vm.$nextTick()

    expect(box.querySelector('[data-testid^="pick-k"]')?.getAttribute("data-testid"))
      .not.toBe(first)
    wrapper.unmount()
  })
})
