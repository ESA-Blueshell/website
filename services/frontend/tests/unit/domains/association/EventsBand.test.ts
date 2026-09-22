import {beforeEach, describe, expect, it, vi} from "vitest"
import {DateTime} from "luxon"
import {flushPromises, mount} from "@vue/test-utils"
import EventsBand from "@/domains/association/island/EventsBand.vue"

const {mockLoadEvents, mockLoadEvent, mockReplace, mockRoute} = vi.hoisted(() => ({
  mockLoadEvents: vi.fn(),
  mockLoadEvent: vi.fn(),
  mockReplace: vi.fn(),
  // The query is made reactive in the mock factory: the band watches the address, and a plain
  // object would never tell it that the back button took the event out.
  mockRoute: {query: {}} as {query: Record<string, unknown>},
}))

/* The events band puts the opened event in the address, which wants a route and a router. */
vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  const {reactive} = await import("vue")
  mockRoute.query = reactive({})
  return withVueRouter(importOriginal, {route: mockRoute, router: {replace: mockReplace}})
})

vi.mock("@/domains/association/adapters/association", () => ({
  loadEventsOnShow: mockLoadEvents,
  loadEventOnShow: mockLoadEvent,
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
    mockLoadEvent.mockResolvedValue(undefined)
    for (const key of Object.keys(mockRoute.query)) delete mockRoute.query[key]
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

  it("carries the art and the hours where the event has them, and nothing where it does not", async () => {
    mockLoadEvents.mockResolvedValue([
      {
        ...eventWithArt(1),
        endTime: "2026-02-01T23:00:00Z",
        banner: {...eventWithArt(1).banner, renditions: [{url: "/art/1-800.webp", width: 800}]},
      },
      {id: 2, title: "Bare", startTime: "2026-02-01T19:00:00Z", membersOnly: false},
      {...eventWithArt(3)},
    ])

    const wrapper = mountBand()
    await flushPromises()

    // Written where the reader is, so the hours are the reader's own rather than the api's.
    const began = DateTime.fromISO("2026-02-01T19:00:00Z").toFormat("d LLLL - HH:mm")
    const ended = DateTime.fromISO("2026-02-01T23:00:00Z").toFormat("HH:mm")

    const items = wrapper.findComponent({name: "PosterStrip"}).props("items") as Record<string, unknown>[]
    expect(items[0]).toMatchObject({when: `${began}-${ended}`, width: 1600, height: 900})
    expect(items[0].srcset).toBeDefined()
    expect(items[1]).toMatchObject({when: began, said: "", banner: undefined})
    expect(items[1].srcset).toBeUndefined()
    expect(items[2].when).toBe(began)
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

  it("opens the event that was pressed, and puts its id in the address", async () => {
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))

    const wrapper = mountBand()
    await flushPromises()
    wrapper.findComponent({name: "PosterStrip"}).vm.$emit("open", 2)
    await flushPromises()

    const dialog = wrapper.findComponent({name: "EventPosterDialog"})
    expect(dialog.props("open")).toBe(true)
    expect(dialog.props("event")).toMatchObject({id: 2, title: "Event 2"})
    expect(mockReplace).toHaveBeenCalledWith({query: {event: "2"}})
  })

  it("takes the id out of the address again when the event is closed", async () => {
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))

    const wrapper = mountBand()
    await flushPromises()
    const strip = wrapper.findComponent({name: "PosterStrip"})
    strip.vm.$emit("open", 2)
    await flushPromises()

    const dialog = wrapper.findComponent({name: "EventPosterDialog"})
    // An open it is already at changes nothing, so only the close is answered.
    dialog.vm.$emit("update:open", true)
    dialog.vm.$emit("update:open", false)
    await flushPromises()

    expect(wrapper.findComponent({name: "EventPosterDialog"}).props("open")).toBe(false)
    expect(mockReplace).toHaveBeenLastCalledWith({query: {}})
  })

  /* An older event falls on a page of the events listing nobody can name in a link. */
  it("opens an event the address names but the strip has not read", async () => {
    mockRoute.query.event = "99"
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))
    mockLoadEvent.mockResolvedValue(eventWithArt(99))

    const wrapper = mountBand()
    await flushPromises()

    expect(mockLoadEvent).toHaveBeenCalledWith(99)
    expect(wrapper.findComponent({name: "EventPosterDialog"}).props("event"))
      .toMatchObject({id: 99})
  })

  it("drops the event the back button takes out of the address", async () => {
    mockRoute.query.event = "2"
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))

    const wrapper = mountBand()
    await flushPromises()
    delete mockRoute.query.event
    await flushPromises()

    expect(wrapper.findComponent({name: "EventPosterDialog"}).props("open")).toBe(false)
  })

  /* An address carrying the same name twice hands back a list, and the first is the answer. */
  it("opens the first event where the address names one more than once", async () => {
    mockRoute.query.event = ["99", "12"]
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))
    mockLoadEvent.mockResolvedValue(eventWithArt(99))

    mountBand()
    await flushPromises()

    expect(mockLoadEvent).toHaveBeenCalledWith(99)
  })

  it("holds the event open while the address names another", async () => {
    mockRoute.query.event = "2"
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))
    mockLoadEvent.mockResolvedValue(eventWithArt(2))

    const wrapper = mountBand()
    await flushPromises()
    mockRoute.query.event = "3"
    await flushPromises()

    expect(wrapper.findComponent({name: "EventPosterDialog"}).props("open")).toBe(true)
  })

  it("asks for nothing where the address names no event, or names nonsense", async () => {
    mockRoute.query.event = "not-an-event"
    mockLoadEvents.mockResolvedValue([1, 2, 3].map(eventWithArt))

    const wrapper = mountBand()
    await flushPromises()

    expect(mockLoadEvent).not.toHaveBeenCalled()
    expect(wrapper.findComponent({name: "EventPosterDialog"}).props("open")).toBe(false)
  })
})
