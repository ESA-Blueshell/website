import {beforeEach, describe, expect, it, vi} from "vitest"
import {DateTime} from "luxon"
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
  location: "Esports Lounge Twente",
  description: "What the art cannot say.",
  membersOnly: false,
  banner: {url: `/art/${id}.webp`, path: `art/${id}.webp`, width: 1600, height: 900, renditions: []},
})

const mountBand = () => mount(EventsBand, {
  props: {eyebrow: "Lately", heading: "What we have been up to", testid: "events"},
  global: {stubs: {PosterStrip: true}},
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
    expect(wrapper.findComponent({name: "PosterStrip"}).props("items")).toHaveLength(4)
  })

  it("carries the way on a page gives it beside the heading, and no empty room without one", async () => {
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))

    const bare = mountBand()
    const given = mount(EventsBand, {
      props: {eyebrow: "", heading: "Past events", testid: "events"},
      slots: {default: "<a href='/events/past'>Every past event</a>"},
      global: {stubs: {PosterStrip: true}},
    })
    await flushPromises()

    expect(bare.find(".band-head__way").exists()).toBe(false)
    expect(given.get(".band-head__way a").attributes("href")).toBe("/events/past")
  })

  /**
   * Absent rather than short.
   *
   * A page that grows a heading promising what goes on here and then empties it reads worse
   * than one that never promised.
   */
  /* What a poster carries is the strip's to draw and this band's to say. */
  it("says the year only where it is not this one, and who the event was for", async () => {
    const older = DateTime.now().minus({years: 2}).toISO()
    mockLoadEvents.mockResolvedValue([
      {...eventWithArt(1), startTime: older, membersOnly: true},
      {...eventWithArt(2), membersOnly: false},
      {...eventWithArt(3), membersOnly: true},
    ])

    const wrapper = mountBand()
    await flushPromises()

    const items = wrapper.findComponent({name: "PosterStrip"}).props("items") as {meta: string}[]
    expect(items.map(one => one.meta)).toEqual([
      `${DateTime.now().year - 2} · members only`,
      "",
      "members only",
    ])
  })

  it("carries the art where the event has it, the date plate's words where not, and leads to the event", async () => {
    mockLoadEvents.mockResolvedValue([
      {
        ...eventWithArt(1),
        endTime: "2026-02-01T22:00:00Z",
        banner: {...eventWithArt(1).banner, renditions: [{url: "/art/1-800.webp", width: 800}]},
      },
      {id: 2, title: "Bare", startTime: "2026-02-01T19:00:00Z", membersOnly: false},
      {...eventWithArt(3)},
    ])

    const wrapper = mountBand()
    await flushPromises()

    // Written where the reader is, so the hours are the reader's own rather than the api's.
    const began = DateTime.fromISO("2026-02-01T19:00:00Z")
    const ended = DateTime.fromISO("2026-02-01T22:00:00Z").toFormat("HH:mm")

    const items = wrapper.findComponent({name: "PosterStrip"}).props("items") as Record<string, unknown>[]
    expect(items[0]).toMatchObject({when: `${began.toFormat("HH:mm")}-${ended}`, width: 1600, height: 900, href: "/events/1"})
    expect(items[0].srcset).toBeDefined()
    expect(items[1]).toMatchObject({
      day: began.toFormat("d"), month: began.toFormat("LLL"), when: began.toFormat("HH:mm"),
      said: "", banner: undefined, href: "/events/2",
    })
    expect(items[1].srcset).toBeUndefined()
    expect(items[2].where).toBe("Esports Lounge Twente")
  })

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

  it("asks for the next page when the strip is near its end, and stops at a short one", async () => {
    mockLoadEvents.mockResolvedValueOnce([1, 2, 3, 4, 5, 6].map(eventWithArt))
    mockLoadEvents.mockResolvedValueOnce([7, 8].map(eventWithArt))

    const wrapper = mountBand()
    await flushPromises()
    const strip = wrapper.findComponent({name: "PosterStrip"})

    strip.vm.$emit("needs-more")
    await flushPromises()
    expect(mockLoadEvents).toHaveBeenNthCalledWith(2, 6, 1)
    expect(strip.props("items")).toHaveLength(8)

    // A page that answers short is the end of the events there are.
    strip.vm.$emit("needs-more")
    await flushPromises()
    expect(mockLoadEvents).toHaveBeenCalledTimes(2)
  })

  it("draws an event no further than once, however the pages overlap", async () => {
    mockLoadEvents.mockResolvedValueOnce([1, 2, 3, 4, 5, 6].map(eventWithArt))
    mockLoadEvents.mockResolvedValueOnce([6, 7, 8, 9, 10, 11].map(eventWithArt))

    const wrapper = mountBand()
    await flushPromises()
    wrapper.findComponent({name: "PosterStrip"}).vm.$emit("needs-more")
    await flushPromises()

    expect(wrapper.findComponent({name: "PosterStrip"}).props("items")).toHaveLength(11)
  })

  it("counts what the page says there is beside its heading, and nothing where it says nothing", async () => {
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))

    const counted = mount(EventsBand, {
      props: {eyebrow: "", heading: "Past events", testid: "events", count: 56, countSaid: "past events"},
      global: {stubs: {PosterStrip: true}},
    })
    const bare = mountBand()
    await flushPromises()

    expect(counted.get("[data-testid=events-head-count]").text()).toContain("56")
    expect(bare.find("[data-testid=events-head-count]").exists()).toBe(false)
  })
})
