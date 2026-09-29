import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import DriftRow, {type DriftItem} from "@/components/island/DriftRow.vue"

const ITEMS: DriftItem[] = [
  {id: "DOTA_2", title: "Dota 2", href: "/casual/dota-2", accent: "#c23c2a", banner: "/dota.webp", srcset: "/dota-640.webp 640w", initials: "D2", sub: "#dota"},
  {id: "OVERWATCH", title: "Overwatch", href: "/casual/overwatch", accent: "#f99e1a", initials: "O"},
]

/** jsdom has no PointerEvent, and the row reads nothing a MouseEvent lacks. */
const pointer = (target: Element, type: string, clientX: number) =>
  target.dispatchEvent(new MouseEvent(type, {clientX, bubbles: true}))

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe("DriftRow", () => {
  it("names each tile once for a reader, and repeats the row silently for the loop", () => {
    const wrapper = mount(DriftRow, {props: {items: ITEMS, testidPrefix: "olden"}})
    const tiles = wrapper.findAll(".drift-row__tile")

    // Two games, repeated until a pass holds eight tiles, drawn twice for the loop.
    expect(tiles).toHaveLength(16)
    expect(tiles.filter(one => one.attributes("aria-hidden") !== "true")).toHaveLength(2)
    expect(wrapper.get("[data-testid=olden-tile-DOTA_2]").attributes("href")).toBe("/casual/dota-2")
    expect(wrapper.get("[data-testid=olden-tile-DOTA_2]").text()).toContain("#dota")
    expect(wrapper.get("[data-testid=olden-tile-DOTA_2] img").attributes("srcset")).toBe("/dota-640.webp 640w")
    expect(wrapper.get("[data-testid=olden-tile-OVERWATCH] .drift-row__plate").text()).toBe("O")
    expect(tiles[2].attributes("tabindex")).toBe("-1")
  })

  it("hands a pressed tile to its page, and leaves a click with a modifier to the browser", async () => {
    const wrapper = mount(DriftRow, {props: {items: ITEMS, testidPrefix: "olden"}})

    await wrapper.get("[data-testid=olden-tile-OVERWATCH]").trigger("click", {button: 0})
    const opened = new MouseEvent("click", {button: 0, ctrlKey: true, cancelable: true})
    wrapper.get("[data-testid=olden-tile-DOTA_2]").element.dispatchEvent(opened)

    expect(wrapper.emitted("go")).toEqual([[ITEMS[1]]])
    expect(opened.defaultPrevented).toBe(false)
  })

  it("moves the row with a drag, and a tile under a drag is not followed", async () => {
    const wrapper = mount(DriftRow, {props: {items: ITEMS, testidPrefix: "olden"}})
    const row = wrapper.get("[data-testid=olden-drift]")
    const run = wrapper.get(".drift-row__run").element as HTMLElement

    pointer(row.element, "pointerdown", 300)
    pointer(row.element, "pointermove", 250)
    pointer(row.element, "pointerup", 250)
    await wrapper.get("[data-testid=olden-tile-OVERWATCH]").trigger("click", {button: 0})

    expect(run.style.transform).toBe("translate3d(-50.0px,0,0)")
    expect(wrapper.emitted("go")).toBeUndefined()
    await wrapper.get("[data-testid=olden-tile-OVERWATCH]").trigger("click", {button: 0})
    expect(wrapper.emitted("go")).toEqual([[ITEMS[1]]])
  })

  it("scrolls with a sideways swipe and leaves an upright one to the page", async () => {
    const wrapper = mount(DriftRow, {props: {items: ITEMS, testidPrefix: "olden"}})
    const row = wrapper.get("[data-testid=olden-drift]")
    const run = wrapper.get(".drift-row__run").element as HTMLElement

    await row.trigger("wheel", {deltaX: 0, deltaY: 40})
    expect(run.style.transform).not.toBe("translate3d(-30.0px,0,0)")
    await row.trigger("wheel", {deltaX: 30, deltaY: 0})
    expect(run.style.transform).toBe("translate3d(-30.0px,0,0)")
  })

  it("holds still under a mouse or focus, but not under a finger", async () => {
    const wrapper = mount(DriftRow, {props: {items: ITEMS, testidPrefix: "olden"}})
    const row = wrapper.get("[data-testid=olden-drift]")

    await row.trigger("pointerenter", {pointerType: "touch"})
    await row.trigger("pointerenter", {pointerType: "mouse"})
    await row.trigger("pointerleave", {pointerType: "mouse"})
    await row.trigger("focusin")
    await row.trigger("focusout")
    await row.trigger("pointercancel")
    wrapper.unmount()
  })

  it("drifts on its own frame by frame once laid out, and stops asking for frames while held", async () => {
    // jsdom lays out nothing: each tile stands 100px on from the one before it.
    vi.spyOn(HTMLElement.prototype, "offsetLeft", "get").mockImplementation(function (this: HTMLElement) {
      return Array.from(this.parentElement?.children ?? []).indexOf(this) * 100
    })
    const frames: ((now: number) => void)[] = []
    vi.stubGlobal("requestAnimationFrame", (callback: (now: number) => void) => frames.push(callback))
    vi.stubGlobal("cancelAnimationFrame", () => undefined)
    const wrapper = mount(DriftRow, {props: {items: ITEMS, testidPrefix: "olden"}})
    await flushPromises()
    const run = wrapper.get(".drift-row__run").element as HTMLElement

    frames.shift()?.(1000)
    frames.shift()?.(1016)
    expect(run.style.transform).not.toBe("translate3d(0.0px,0,0)")

    await wrapper.get("[data-testid=olden-drift]").trigger("pointerenter", {pointerType: "mouse"})
    frames.splice(0).forEach(frame => frame(1032))
    expect(frames).toHaveLength(0)
    wrapper.unmount()
  })

  it("draws nothing without tiles", () => {
    expect(mount(DriftRow, {props: {items: [], testidPrefix: "olden"}}).find(".drift-row").exists()).toBe(false)
  })
})
