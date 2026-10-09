/**
 * The live progress panel: the pass meter and its ETA while the cluster runs, and the rate chart
 * that samples the live pps each second and draws the last window of them.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import PingerProgress from "@/components/pinger/PingerProgress.vue"

const props = (over: Record<string, unknown> = {}) => ({
  sent: 400,
  pps: 120,
  passTotal: 1000,
  running: true,
  ...over,
})

describe("PingerProgress", () => {
  const wrappers: VueWrapper[] = []
  const render = (over: Record<string, unknown> = {}) => {
    const wrapper = mount(PingerProgress, {props: props(over)})
    wrappers.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal("requestAnimationFrame", () => 1)
    vi.stubGlobal("cancelAnimationFrame", vi.fn())
    vi.stubGlobal("matchMedia", (q: string) => ({matches: false, media: q, addEventListener: vi.fn(), removeEventListener: vi.fn()}))
  })

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.unmount())
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it("reports the seconds left in the current pass while running", () => {
    const wrapper = render({pps: 120, passTotal: 1000, sent: 400})

    expect(wrapper.find(".meter__eta").text()).toMatch(/left in this pass/)
  })

  it("shows a dash for the ETA when nothing is running", () => {
    const wrapper = render({running: false})

    expect(wrapper.find(".meter__eta").text()).toContain("—")
  })

  it("draws the rate line once seconds have been sampled", async () => {
    const wrapper = render({pps: 300})

    vi.advanceTimersByTime(3000)
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".rate__area").attributes("points")).toMatch(/^0,100 /)
    expect(wrapper.find(".rate__stroke").attributes("points")).not.toBe("")
  })
})
