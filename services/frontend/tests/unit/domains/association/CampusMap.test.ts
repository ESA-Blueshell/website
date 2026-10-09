import {afterEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {type DOMWrapper, mount, type VueWrapper} from "@vue/test-utils"
import CampusMap from "@/domains/association/island/CampusMap.vue"

const stage = (wrapper: VueWrapper) => wrapper.get(".campus-map__stage").attributes("style")

/** jsdom has no PointerEvent, and test-utils cannot set a button on the event it falls back to. */
async function point(target: DOMWrapper<Element>, type: string, init: ConstructorParameters<typeof MouseEvent>[1]) {
  target.element.dispatchEvent(new MouseEvent(type, {bubbles: true, ...init}))
  await nextTick()
}

/** jsdom lays nothing out, so the plate is given the box a drag and a scroll are measured in. */
function plateOf(wrapper: VueWrapper) {
  const plate = wrapper.get("[data-testid=campus-map]")
  plate.element.getBoundingClientRect = () => new DOMRect(0, 0, 600, 450)
  return plate
}

async function scroll(target: DOMWrapper<Element>, init: ConstructorParameters<typeof WheelEvent>[1]) {
  const event = new WheelEvent("wheel", {bubbles: true, cancelable: true, ...init})
  target.element.dispatchEvent(event)
  await nextTick()
  return event
}

describe("CampusMap", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("draws the campus in both themes, with the Lounge pinned and OpenStreetMap credited", () => {
    const wrapper = mount(CampusMap)

    expect(wrapper.findAll("img.campus-map__art")).toHaveLength(2)
    expect(wrapper.get("[data-testid=campus-map-lounge]").text()).toContain("Esports Lounge Twente")
    expect(wrapper.get(".campus-map__credit a").attributes("href")).toBe("https://www.openstreetmap.org/copyright")
  })

  it("offers the way there in the visitor's own maps app or on OpenStreetMap", () => {
    const wrapper = mount(CampusMap)

    expect(wrapper.get("[data-testid=campus-map-app]").attributes("href")).toMatch(/^geo:52\.243214,6\.851979\?q=/)
    expect(wrapper.get("[data-testid=campus-map-route]").attributes("href")).toContain("openstreetmap.org/directions")
  })

  it("zooms in and out about the middle, as far as it goes", async () => {
    const wrapper = mount(CampusMap)

    await wrapper.get("[aria-label='Zoom in']").trigger("click")
    expect(stage(wrapper)).toContain("scale(1.4)")

    await wrapper.get("[aria-label='Zoom out']").trigger("click")
    expect(stage(wrapper)).toContain("scale(1)")

    for (let i = 0; i < 6; i++) await wrapper.get("[aria-label='Zoom in']").trigger("click")
    expect(stage(wrapper)).toContain("scale(3)")
    expect(wrapper.get("[aria-label='Zoom in']").attributes()).toHaveProperty("disabled")
  })

  it("zooms in on a double press, keeping the pins their own size", async () => {
    const wrapper = mount(CampusMap)

    await wrapper.get("[data-testid=campus-map]").trigger("dblclick")
    await wrapper.get("[data-testid=campus-map]").trigger("dblclick")

    expect(wrapper.get(".campus-map__steady").attributes("style")).toContain("scale(0.5102)")
  })

  it("follows a drag, drops the hint once moved, and goes back to the Lounge", async () => {
    const wrapper = mount(CampusMap)
    const plate = plateOf(wrapper)

    await point(plate, "pointerdown", {button: 0, clientX: 100, clientY: 100})
    await point(plate, "pointermove", {clientX: 140, clientY: 70})
    await point(plate, "pointerup", {})
    await point(plate, "pointermove", {clientX: 400, clientY: 400})

    expect(stage(wrapper)).toContain("translate(40px, -30px)")
    expect(wrapper.get(".campus-map__hint").classes()).toContain("campus-map__hint--gone")

    await wrapper.get("[aria-label='Back to the Lounge']").trigger("click")
    expect(stage(wrapper)).toContain("translate(0px, 0px) scale(1)")
  })

  it("leaves a press on its own buttons, or with another button, alone", async () => {
    const wrapper = mount(CampusMap)
    const plate = plateOf(wrapper)

    await point(wrapper.get("[aria-label='Zoom out']"), "pointerdown", {button: 0, clientX: 0, clientY: 0})
    await point(plate, "pointermove", {clientX: 50, clientY: 50})
    await point(plate, "pointerdown", {button: 2, clientX: 0, clientY: 0})
    await point(plate, "pointermove", {clientX: 50, clientY: 50})

    expect(stage(wrapper)).toContain("translate(0px, 0px)")
  })

  it("starts closer on a phone, and goes back there", async () => {
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: true, media: query, addEventListener: () => {}, removeEventListener: () => {},
    }))
    const wrapper = mount(CampusMap)

    expect(stage(wrapper)).toContain("scale(1.8)")

    await wrapper.get("[aria-label='Zoom in']").trigger("click")
    await wrapper.get("[aria-label='Back to the Lounge']").trigger("click")
    expect(stage(wrapper)).toContain("scale(1.8)")
  })

  it("goes to the phone's start when the window narrows to one", async () => {
    const query = Object.assign(new EventTarget(), {matches: false})
    vi.stubGlobal("matchMedia", () => query)
    const wrapper = mount(CampusMap)
    await nextTick()
    expect(stage(wrapper)).toContain("scale(1)")

    query.dispatchEvent(Object.assign(new Event("change"), {matches: true}))
    await nextTick()

    expect(stage(wrapper)).toContain("scale(1.8)")
  })

  it("zooms under the pointer as it scrolls, without scrolling the page", async () => {
    vi.useFakeTimers()
    const wrapper = mount(CampusMap)
    const plate = plateOf(wrapper)

    // A notch up at the plate's right edge: one button press closer, the right edge held still.
    const event = await scroll(plate, {deltaY: -100, clientX: 600, clientY: 225})

    expect(event.defaultPrevented).toBe(true)
    expect(stage(wrapper)).toContain("scale(1.4)")
    expect(stage(wrapper)).toContain("translate(-120px, 0px)")
    expect(plate.classes()).toContain("campus-map__plate--still")

    vi.advanceTimersByTime(200)
    await nextTick()
    expect(plate.classes()).not.toContain("campus-map__plate--still")
    vi.useRealTimers()
  })

  it("counts a wheel that scrolls by lines as a mouse does", async () => {
    const wrapper = mount(CampusMap)
    const plate = plateOf(wrapper)

    await scroll(plate, {deltaY: 100 / 16, deltaMode: WheelEvent.DOM_DELTA_LINE, clientX: 300, clientY: 225})

    expect(stage(wrapper)).toContain("scale(0.714")
  })

  it("zooms in where it is double pressed", async () => {
    const wrapper = mount(CampusMap)
    const plate = plateOf(wrapper)

    await point(plate, "dblclick", {clientX: 400, clientY: 225})

    expect(stage(wrapper)).toContain("translate(-40px, 0px) scale(1.4)")
  })
})
