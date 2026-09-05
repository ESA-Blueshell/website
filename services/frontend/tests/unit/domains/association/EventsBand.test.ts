import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import EventsBand from "@/domains/association/island/EventsBand.vue"

const {mockLoadEvents} = vi.hoisted(() => ({mockLoadEvents: vi.fn()}))

vi.mock("@/domains/association/adapters/association", () => ({
  loadEventsOnShow: mockLoadEvents,
}))

const eventWithArt = (id: number) => ({
  id,
  title: `Event ${id}`,
  startTime: "2026-02-01T19:00:00Z",
  membersOnly: false,
  banner: {url: `/art/${id}.webp`, path: `art/${id}.webp`, width: 1600, height: 900, renditions: []},
})

const mountBand = () => mount(EventsBand, {
  props: {eyebrow: "Lately", heading: "What we have been up to", testid: "events"},
  global: {stubs: {SliceBand: true}},
})

describe("EventsBand", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLoadEvents.mockResolvedValue([])
  })

  it("draws the events it is given, under the page's own words", async () => {
    mockLoadEvents.mockResolvedValue([1, 2, 3, 4].map(eventWithArt))

    const wrapper = mountBand()
    await flushPromises()

    const band = wrapper.get('[data-testid="events"]')
    expect(band.text()).toContain("Lately")
    expect(band.text()).toContain("What we have been up to")
  })

  /**
   * Absent rather than short.
   *
   * A page that grows a heading promising what goes on here and then empties it reads worse
   * than one that never promised.
   */
  it("draws nothing at all when too few events have art", async () => {
    mockLoadEvents.mockResolvedValue([1, 2].map(eventWithArt))

    const wrapper = mountBand()
    await flushPromises()

    expect(wrapper.find('[data-testid="events"]').exists()).toBe(false)
  })

  it("draws nothing while the read is still in flight", () => {
    mockLoadEvents.mockReturnValue(new Promise(() => {}))

    expect(mountBand().find('[data-testid="events"]').exists()).toBe(false)
  })

  it("draws nothing when the read fails", async () => {
    mockLoadEvents.mockRejectedValue(new Error("no"))

    const wrapper = mountBand()
    await flushPromises()

    expect(wrapper.find('[data-testid="events"]').exists()).toBe(false)
  })
})
