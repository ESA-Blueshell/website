import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import HistoryBand from "@/domains/association/island/HistoryBand.vue"
import type {Milestone} from "@/domains/association/historyAxis"

const MILESTONES: Milestone[] = [
  {year: "2017", title: "First", telling: "The first thing that happened."},
  {year: "2019", title: "Second", telling: "The second thing that happened."},
  {year: "Now", title: "Third", telling: "Where it stands today."},
]

const observed: Element[] = []
let fire: ((entries: {target: Element; isIntersecting: boolean}[]) => void) | null = null

const installObserver = () => {
  observed.length = 0
  fire = null
  vi.stubGlobal("IntersectionObserver", class {
    constructor(callback: (entries: unknown[]) => void) {
      fire = callback as never
    }

    observe(element: Element) {
      observed.push(element)
    }

    disconnect() {
      observed.length = 0
    }
  })
}

const mountBand = () => mount(HistoryBand, {props: {milestones: MILESTONES, testid: "history"}})

describe("HistoryBand", () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    installObserver()
  })

  it("draws every milestone, in the order they happened", () => {
    const wrapper = mountBand()

    const stops = wrapper.findAll('[data-testid="history-stop"]')
    expect(stops).toHaveLength(3)
    expect(stops[0].text()).toContain("First")
    expect(stops[2].text()).toContain("Third")
  })

  it("watches every milestone for the middle of the screen", () => {
    mountBand()

    expect(observed).toHaveLength(3)
  })

  /** The one at the middle of the screen is the one being read, and only that one. */
  it("reads whichever milestone reaches the middle", async () => {
    const wrapper = mountBand()
    const stops = wrapper.findAll('[data-testid="history-stop"]')

    fire?.([{target: stops[1].element, isIntersecting: true}])
    await wrapper.vm.$nextTick()

    expect(stops[1].classes()).toContain("history__stop--read")
    expect(stops[0].classes()).not.toContain("history__stop--read")
    expect(stops[2].classes()).not.toContain("history__stop--read")
  })

  it("stops reading a milestone that leaves the middle", async () => {
    const wrapper = mountBand()
    const stops = wrapper.findAll('[data-testid="history-stop"]')

    fire?.([{target: stops[1].element, isIntersecting: true}])
    await wrapper.vm.$nextTick()
    fire?.([{target: stops[1].element, isIntersecting: false}])
    await wrapper.vm.$nextTick()

    expect(stops[1].classes()).not.toContain("history__stop--read")
  })

  /**
   * A reader who asked for less motion gets the whole history at once.
   *
   * Nothing grows and nothing opens, so nothing is hidden behind an effect they turned off.
   */
  it("opens every milestone for a reader who asked for less motion", () => {
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: true,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    installObserver()

    const wrapper = mountBand()

    expect(wrapper.classes()).toContain("history--still")
    for (const stop of wrapper.findAll('[data-testid="history-stop"]')) {
      expect(stop.classes()).toContain("history__stop--read")
    }
    // Nothing to watch for: there is no growing to trigger.
    expect(observed).toHaveLength(0)
  })

  it("draws the whole history where the browser cannot watch the page", () => {
    vi.stubGlobal("IntersectionObserver", undefined)

    const wrapper = mountBand()

    expect(wrapper.findAll('[data-testid="history-stop"]')).toHaveLength(3)
  })
})
