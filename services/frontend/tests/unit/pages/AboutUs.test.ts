import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {mountInApp, unmountAll} from "./helpers"
import AboutUs from "@/pages/AboutUs.vue"
import {MILESTONES} from "@/domains/association/historyAxis"

const {mockLoadNumbers, mockLoadEvents} = vi.hoisted(() => ({
  mockLoadNumbers: vi.fn(),
  mockLoadEvents: vi.fn(),
}))

vi.mock("@/domains/association/adapters/association", () => ({
  loadAssociationNumbers: mockLoadNumbers,
  loadEventsOnShow: mockLoadEvents,
  loadCurrentContributionPeriod: vi.fn(),
}))

const mounted: VueWrapper[] = []

const hold = (wrapper: VueWrapper<any>) => {
  mounted.push(wrapper)
  return wrapper
}

afterEach(() => unmountAll(mounted, "About us page"))

const mountPage = () => hold(mountInApp(AboutUs, {
  global: {
    stubs: {
      "router-link": {template: "<a><slot /></a>"},
      HeroBand: true,
      SliceBand: true,
      CallBand: true,
      BandRule: true,
    },
  },
}))

describe("About us page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLoadNumbers.mockResolvedValue(null)
    mockLoadEvents.mockResolvedValue([])
  })

  it("runs the whole history down the page, oldest first", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const stops = wrapper.findAll('[data-testid="aboutus-history-stop"]')
    expect(stops).toHaveLength(MILESTONES.length)
    expect(stops[0].text()).toContain(MILESTONES[0].title)
    expect(stops[stops.length - 1].text()).toContain(MILESTONES[MILESTONES.length - 1].title)
  })

  /** Every milestone's telling is in the markup; which one is legible is the scroll's business. */
  it("carries the telling of every milestone", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const text = wrapper.get('[data-testid="aboutus-history"]').text()
    for (const milestone of MILESTONES) {
      expect(text).toContain(milestone.telling.slice(0, 40))
    }
  })

  /**
   * The two competitions are different things, and the page must not merge them.
   *
   * The teams stand in the Dutch College Esports Series; the three titles are the Dutch
   * Student League's. Saying either name for the other would be wrong.
   */
  it("keeps the two competitions apart", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain("Dutch College Esports Series")
    expect(text).toContain("Dutch Student League")
  })

  it("says nothing about whether either league is still running", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const text = wrapper.text().toLowerCase()
    expect(text).not.toContain("no longer")
    expect(text).not.toContain("defunct")
    expect(text).not.toContain("ended")
  })

  it("points at the pages it does not duplicate", async () => {
    const wrapper = mountPage()
    await flushPromises()

    const html = wrapper.html()
    expect(html).toContain("/committees")
    expect(html).toContain("/esports/competitive-scene")
    expect(html).toContain("/events")
  })

  it("draws no events band when too few events have art", async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="aboutus-events"]').exists()).toBe(false)
  })
})
