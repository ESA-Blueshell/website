import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import PreviewFrame from "@/components/island/PreviewFrame.vue"

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe("a preview frame", () => {
  it("lays the part out at its page width, shrinks it to the column and keeps it inert", async () => {
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(640)
    vi.spyOn(HTMLElement.prototype, "offsetHeight", "get").mockReturnValue(400)
    const observed: Element[] = []
    const disconnect = vi.fn()
    vi.stubGlobal("ResizeObserver", class {
      constructor(readonly callback: () => void) {}
      observe(element: Element) { observed.push(element) }
      disconnect = disconnect
    })

    const wrapper = mount(PreviewFrame, {props: {width: 1280}, slots: {default: "<div data-testid=part />"}})
    await wrapper.vm.$nextTick()
    const stage = wrapper.get(".preview-frame__stage")

    expect(stage.attributes("style")).toContain("width: 1280px")
    expect(stage.attributes("style")).toContain("scale(0.5)")
    expect(stage.attributes("inert")).toBeDefined()
    expect(wrapper.attributes("style")).toContain("height: 200px")
    expect(observed).toHaveLength(2)
    wrapper.unmount()
    expect(disconnect).toHaveBeenCalled()
  })

  it("draws the part at its own size where there is room, or before the column is measured", () => {
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(0)
    vi.stubGlobal("ResizeObserver", undefined)

    const wrapper = mount(PreviewFrame, {slots: {default: "<div />"}})

    expect(wrapper.get(".preview-frame__stage").attributes("style")).toContain("width: 1280px")
    expect(wrapper.get(".preview-frame__stage").attributes("style")).toContain("scale(1)")
  })
})
