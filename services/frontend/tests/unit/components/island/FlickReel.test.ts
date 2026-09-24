import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import FlickReel, {type ReelItem} from "@/components/island/FlickReel.vue"
import PanChevron from "@/components/island/PanChevron.vue"

const ITEMS: ReelItem[] = [
  {id: "VALORANT", title: "Valorant", href: "/casual/valorant", accent: "#ff4655", banner: "/v.webp", srcset: "/v-640.webp 640w", icon: "/v-icon.webp", initials: "V", notes: ["#valorant"], chips: ["LanCie"]},
  {id: "CHESS", title: "Chess", href: "/casual/chess", accent: "#b58863", initials: "C", railLabel: "Chess"},
  {id: "WORDLE", title: "Wordle", href: "/casual/wordle", accent: "#6aaa64", initials: "W"},
  {id: "MINECRAFT", title: "Minecraft", href: "/casual/minecraft", accent: "#6cbf3f", initials: "M"},
]

/** Frames are run by hand, so a test says exactly how much time passes. */
let frames: ((now: number) => void)[] = []
let clock = 0

function runFrames(count: number) {
  for (let at = 0; at < count; at++) {
    clock += 16
    const due = frames
    frames = []
    due.forEach(callback => callback(clock))
  }
}

function mountReel(props: Partial<InstanceType<typeof FlickReel>["$props"]> = {}): VueWrapper {
  return mount(FlickReel, {props: {items: ITEMS, testidPrefix: "casual", drift: 0, ...props}, attachTo: document.body})
}

/** jsdom has no PointerEvent, and the reel reads nothing a MouseEvent lacks. */
const pointer = (target: Element, type: string, clientX: number) =>
  target.dispatchEvent(new MouseEvent(type, {clientX, bubbles: true}))

const slice = (wrapper: VueWrapper, id: string) => wrapper.get(`[data-testid=casual-slice-${id}]`)
const resting = (wrapper: VueWrapper) => wrapper.findAll("[aria-current=true]").map(one => one.attributes("data-testid"))

beforeEach(() => {
  frames = []
  clock = 0
  vi.stubGlobal("requestAnimationFrame", (callback: (now: number) => void) => frames.push(callback))
  vi.stubGlobal("cancelAnimationFrame", vi.fn())
  vi.spyOn(performance, "now").mockImplementation(() => clock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.body.innerHTML = ""
})

describe("FlickReel", () => {
  it("draws a slice per item, its art or its plate, and a rail cell per item", () => {
    const wrapper = mountReel()

    expect(slice(wrapper, "VALORANT").attributes("href")).toBe("/casual/valorant")
    expect(slice(wrapper, "VALORANT").find(".flick-reel__art").attributes("srcset")).toBe("/v-640.webp 640w")
    expect(slice(wrapper, "VALORANT").text()).toContain("#valorant")
    expect(slice(wrapper, "VALORANT").text()).toContain("LanCie")
    expect(slice(wrapper, "CHESS").find(".flick-reel__plate").text()).toBe("C")
    expect(wrapper.get("[data-testid=casual-rail-VALORANT] img").attributes("src")).toBe("/v-icon.webp")
    expect(wrapper.get("[data-testid=casual-rail-CHESS]").text()).toBe("Chess")
    expect(wrapper.get("[data-testid=casual-rail-WORDLE]").text()).toBe("W")
  })

  it("opens the first slice in the middle and paints every slice onto the band", () => {
    const wrapper = mountReel()
    const first = slice(wrapper, "VALORANT").element as HTMLElement

    expect(resting(wrapper)).toEqual(["casual-slice-VALORANT"])
    expect(first.style.getPropertyValue("--open")).toBe("1.000")
    expect(first.style.width).toBe("608px")
    expect(wrapper.get("[data-testid=casual-rail-VALORANT]").classes()).toContain("flick-reel__cell--on")
  })

  it("follows the open slice's link, and brings any other slice to the middle instead", async () => {
    const wrapper = mountReel()

    await slice(wrapper, "VALORANT").trigger("click", {button: 0})
    expect(wrapper.emitted("go")?.[0]).toEqual([ITEMS[0]])

    await slice(wrapper, "CHESS").trigger("click", {button: 0})
    runFrames(200)
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted("go")).toHaveLength(1)
    expect(resting(wrapper)).toEqual(["casual-slice-CHESS"])
  })

  it("leaves a click with a modifier to the browser", async () => {
    const wrapper = mountReel()
    const event = new MouseEvent("click", {button: 0, metaKey: true, cancelable: true})

    slice(wrapper, "VALORANT").element.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(false)
    expect(wrapper.emitted("go")).toBeUndefined()
  })

  it("moves with a drag and never follows a link the drag ended on", async () => {
    const wrapper = mountReel()
    const band = wrapper.get("[data-testid=casual-band]")
    const capture = vi.fn()
    Object.assign(band.element, {setPointerCapture: capture, hasPointerCapture: () => capture.mock.calls.length > 0})

    pointer(band.element, "pointerdown", 700)
    pointer(band.element, "pointermove", 550)
    const open = () => (slice(wrapper, "VALORANT").element as HTMLElement).style.getPropertyValue("--open")
    expect(open()).toBe("0.500")
    pointer(band.element, "pointermove", 540)
    pointer(band.element, "pointerup", 540)
    await slice(wrapper, "CHESS").trigger("click", {button: 0})

    expect(capture).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted("go")).toBeUndefined()
  })

  it("ignores a press that starts on a chevron, and a move with no press", async () => {
    const wrapper = mountReel()
    const band = wrapper.get("[data-testid=casual-band]")

    pointer(wrapper.get("[data-testid=casual-on]").element, "pointerdown", 10)
    pointer(band.element, "pointermove", 300)
    pointer(band.element, "pointerup", 300)

    expect((slice(wrapper, "VALORANT").element as HTMLElement).style.getPropertyValue("--open")).toBe("1.000")
  })

  it("steps with the chevrons and jumps from the rail", async () => {
    const wrapper = mountReel()
    const [back, on] = wrapper.findAllComponents(PanChevron)

    on.vm.$emit("pan")
    runFrames(200)
    await wrapper.vm.$nextTick()
    expect(resting(wrapper)).toEqual(["casual-slice-CHESS"])

    back.vm.$emit("pan")
    back.vm.$emit("pan")
    runFrames(200)
    await wrapper.vm.$nextTick()
    expect(resting(wrapper)).toEqual(["casual-slice-MINECRAFT"])

    await wrapper.get("[data-testid=casual-rail-WORDLE]").trigger("click")
    runFrames(200)
    await wrapper.vm.$nextTick()
    expect(resting(wrapper)).toEqual(["casual-slice-WORDLE"])
  })

  it("brings a slice reached by keyboard to the middle", async () => {
    const wrapper = mountReel()

    await slice(wrapper, "MINECRAFT").trigger("focus")
    runFrames(200)
    await wrapper.vm.$nextTick()

    expect(resting(wrapper)).toEqual(["casual-slice-MINECRAFT"])
  })

  it("swipes sideways with a trackpad and leaves vertical scrolling to the page", async () => {
    const wrapper = mountReel()
    const band = wrapper.get("[data-testid=casual-band]")
    const vertical = new WheelEvent("wheel", {deltaX: 1, deltaY: 40, cancelable: true})

    band.element.dispatchEvent(vertical)
    await band.trigger("wheel", {deltaX: 400, deltaY: 0})
    runFrames(300)
    await wrapper.vm.$nextTick()

    expect(vertical.defaultPrevented).toBe(false)
    expect(resting(wrapper)).toEqual(["casual-slice-CHESS"])
  })

  it("drifts on its own, and holds still under the pointer", async () => {
    const wrapper = mountReel({drift: 1})
    const band = wrapper.get("[data-testid=casual-band]")

    await band.trigger("mouseenter")
    runFrames(100)
    await wrapper.vm.$nextTick()
    expect(resting(wrapper)).toEqual(["casual-slice-VALORANT"])

    await band.trigger("mouseleave")
    runFrames(100)
    await wrapper.vm.$nextTick()
    expect(resting(wrapper)).not.toEqual(["casual-slice-VALORANT"])
  })

  it("narrows its slices on a phone-wide band", async () => {
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(390)
    const wrapper = mountReel()
    await wrapper.vm.$nextTick()

    expect(wrapper.get("[data-testid=casual-reel]").classes()).toContain("flick-reel--narrow")
    expect((slice(wrapper, "VALORANT").element as HTMLElement).style.width).toBe("289px")
  })

  it("starts over when the items change, and stops its frames when it goes", async () => {
    const wrapper = mountReel()

    await wrapper.setProps({items: ITEMS.slice(0, 2)})
    expect(wrapper.findAll(".flick-reel__slice")).toHaveLength(2)

    wrapper.unmount()
    expect(cancelAnimationFrame).toHaveBeenCalled()
  })

  it("falls back to timers where the browser has no animation frames", () => {
    vi.stubGlobal("requestAnimationFrame", undefined)
    vi.stubGlobal("cancelAnimationFrame", undefined)
    vi.useFakeTimers()
    const wrapper = mountReel()

    vi.advanceTimersByTime(100)
    wrapper.unmount()
    vi.useRealTimers()

    expect(wrapper.exists()).toBe(false)
  })
})
