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
