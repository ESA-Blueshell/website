import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import Timeline from "@/components/island/Timeline.vue"

const stops = Array.from({length: 12}, (_, at) => ({id: at + 1, label: `Season ${at + 1}`}))

/* jsdom lays nothing out, so the strip is told how wide it is and how far it has scrolled. */
const scrolled = (box: HTMLElement, left: number) => {
  Object.defineProperty(box, "clientWidth", {configurable: true, value: 400})
  Object.defineProperty(box, "scrollWidth", {configurable: true, value: 2000})
  Object.defineProperty(box, "scrollLeft", {configurable: true, value: left, writable: true})
}

describe("the strip's own way sideways", () => {
  it("moves a screenful each way when a chevron is pressed", async () => {
    const wrapper = mount(Timeline, {
      attachTo: document.body,
      props: {stops, selectedId: 6, testidPrefix: "strip"},
    })
    const box = wrapper.find(".timeline__scroll").element as HTMLElement
    scrolled(box, 500)
    box.scrollBy = vi.fn()
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const chevrons = wrapper.findAllComponents({name: "PanChevron"})
    expect(chevrons.length).toBe(2)

    chevrons[0]!.vm.$emit("pan")
    chevrons[1]!.vm.$emit("pan")
    await flushPromises()

    const asked = (box.scrollBy as ReturnType<typeof vi.fn>).mock.calls.map(
      ([one]) => (one as {left: number}).left)
    expect(asked[0]).toBeLessThan(0)
    expect(asked[1]).toBeGreaterThan(0)
  })
})

describe("the strip on a wide screen", () => {
  it("is drawn at its widest and scaled up whole, so its height keeps pace", async () => {
    let seen: (entries: Array<{contentRect: {width: number}}>) => void = () => {}
    vi.stubGlobal("ResizeObserver", class {
      constructor(callback: typeof seen) { seen = callback }
      observe() {}
      disconnect() {}
    })
    const wrapper = mount(Timeline, {props: {stops, selectedId: 6, testidPrefix: "strip"}})

    seen([{contentRect: {width: 3840}}])
    await flushPromises()

    const style = (wrapper.get("[data-testid=strip-timeline]").element as HTMLElement).style
    expect(style.getPropertyValue("--k")).toBe("2")
    expect(style.getPropertyValue("--track")).toBe(`${12 * (1920 / 7)}px`)
    vi.unstubAllGlobals()
  })
})

describe("a stop on the strip", () => {
  it("is chosen by a click, lit by the pointer or focus, and stepped from with the arrows", async () => {
    const wrapper = mount(Timeline, {props: {stops, selectedId: 6, testidPrefix: "strip"}})
    const node = wrapper.get("[data-testid=strip-node-6]")

    await node.element.closest(".stop-slot")!.dispatchEvent(new MouseEvent("mouseenter"))
    await node.trigger("focus")
    await node.trigger("click")
    await node.trigger("keydown", {key: "ArrowLeft"})
    await node.trigger("keydown", {key: "ArrowRight"})

    expect(node.classes()).toContain("stop--lit")
    expect(wrapper.emitted("select")).toEqual([[6], [5], [7]])
  })
})
