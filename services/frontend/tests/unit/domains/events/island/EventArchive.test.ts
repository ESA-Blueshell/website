import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import {DateTime} from "luxon"
import EventArchive from "@/domains/events/island/EventArchive.vue"
import {
  academicYearOf,
  academicYearsBetween,
  PAST_PAGE,
  SEARCH_REST_MS,
} from "@/domains/events/island/usePastEvents"

const {mockReadPage, mockCommittees} = vi.hoisted(() => ({mockReadPage: vi.fn(), mockCommittees: vi.fn()}))

vi.mock("@/domains/events/adapters/events", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  readEventPage: mockReadPage,
}))
vi.mock("@/domains/committees", () => ({listCommittees: mockCommittees}))

const NOW = "2026-09-23T12:00:00"

const event = (id: number, startTime: string, over: Record<string, unknown> = {}) => ({
  id, title: `Event ${id}`, startTime, endTime: startTime, approved: true, signUp: false,
  signUpCount: 0, membersOnly: false, committeeId: 3, ...over,
})

const FIRST = event(1, "2025-10-04T19:00:00")

/* The years read asks for one event, oldest first; every other read is the listing. */
const answer = (listing: (query: Record<string, unknown>) => unknown) => {
  mockReadPage.mockImplementation(async (query: Record<string, unknown>) =>
    query.size === 1 ? {events: [FIRST]} : listing(query))
}

const listings = () => mockReadPage.mock.calls.map(call => call[0]).filter(query => query.size !== 1)

const mountArchive = async () => {
  const wrapper = mount(EventArchive, {global: {stubs: {RouterLink: RouterLinkStub}}})
  await flushPromises()
  return wrapper
}

describe("academic years", () => {
  it("start on the first of September", () => {
    expect(academicYearOf(DateTime.fromISO("2026-08-31"))).toBe(2025)
    expect(academicYearOf(DateTime.fromISO("2026-09-01"))).toBe(2026)
  })

  it("run from the first event's year to this one, newest first", () => {
    const years = academicYearsBetween(DateTime.fromISO("2024-11-01"), DateTime.fromISO("2026-09-23"))

    expect(years.map(one => one.label)).toEqual(["2026–27", "2025–26", "2024–25"])
    expect(years[1]!.from.toISODate()).toBe("2025-09-01")
    expect(years[1]!.to.toISODate()).toBe("2026-09-01")
  })
})

describe("EventArchive", () => {
  beforeEach(() => {
    vi.useFakeTimers({toFake: ["Date", "setTimeout", "clearTimeout"]})
    vi.setSystemTime(new Date(NOW))
    vi.clearAllMocks()
    mockCommittees.mockResolvedValue([{id: 3, name: "4Funcie"}, {id: 4}])
    answer(() => ({
      events: [
        event(12, "2026-09-20T19:00:00", {banner: {image: {url: "/files/poster.webp", width: 1080, height: 1080}}}),
        event(11, "2026-09-02T19:00:00", {committeeId: null}),
        event(10, "2026-06-05T19:00:00", {committeeId: 9}),
      ],
      page: {totalElements: 3},
    }))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it("reads what has happened, newest first, a page at a time", async () => {
    await mountArchive()

    expect(listings()[0]).toMatchObject({
      approved: true,
      from: undefined,
      titleContains: undefined,
      page: 0,
      size: PAST_PAGE,
      sort: ["startTime,desc"],
    })
    expect(DateTime.fromISO(listings()[0].to).toISO()).toBe(DateTime.fromISO(NOW).toISO())
  })

  it("groups the posters by month, each leading to its event with its day and committee", async () => {
    const wrapper = await mountArchive()

    expect(wrapper.findAll(".month__name").map(one => one.text())).toEqual(["September 2026", "June 2026"])
    expect(wrapper.findAll(".month__count").map(one => one.text())).toEqual(["2 events", "1 event"])
    const tiles = wrapper.findAllComponents(RouterLinkStub)
    expect(tiles.map(one => one.props("to"))).toEqual([
      {name: "event", params: {id: 12}},
      {name: "event", params: {id: 11}},
      {name: "event", params: {id: 10}},
    ])
    expect(tiles[0]!.find(".archive__day").text()).toBe("Sun 20 Sep")
    expect(wrapper.findAll(".archive__by").map(one => one.text())).toEqual(["4Funcie", "Member's initiative", ""])
    expect(tiles[0]!.find("img").attributes("alt")).toBe("Event 12")
    expect(tiles[1]!.find(".poster-art__day").text()).toBe("2")
    expect(tiles[1]!.find(".poster-art__month").text()).toBe("Sep")
    expect(tiles[1]!.find(".poster-art__title").text()).toBe("Event 11")
  })

  it("offers every academic year there are events in, and narrows the read to the one chosen", async () => {
    const wrapper = await mountArchive()

    const years = wrapper.findAll("[role=radio]")
    expect(years.map(one => one.text())).toEqual(["All years", "2026–27", "2025–26"])

    await wrapper.get("[data-testid=event-archive-year-2025]").trigger("click")
    await flushPromises()
    const chosen = listings().at(-1)
    expect(DateTime.fromISO(chosen.from).toISODate()).toBe("2025-09-01")
    expect(DateTime.fromISO(chosen.to).toISODate()).toBe("2026-09-01")

    await wrapper.get("[data-testid=event-archive-year-2026]").trigger("click")
    await flushPromises()
    expect(DateTime.fromISO(listings().at(-1).to).toISO()).toBe(DateTime.fromISO(NOW).toISO())
  })

  it("searches by title once the typing rests, and says so when nothing matches", async () => {
    const wrapper = await mountArchive()
    answer(() => ({events: [], page: {totalElements: 0}}))

    await wrapper.get("[data-testid=event-archive-search]").setValue("  pool")
    await wrapper.get("[data-testid=event-archive-search]").setValue("  pool ")
    expect(listings()).toHaveLength(1)

    vi.advanceTimersByTime(SEARCH_REST_MS)
    await flushPromises()
    expect(listings()).toHaveLength(2)
    expect(listings()[1]).toMatchObject({titleContains: "pool", page: 0})
    expect(wrapper.get("[data-testid=event-archive-none]").text()).toBe("No past event is called anything like “pool”.")
    expect(wrapper.find("[data-testid=event-archive-shown]").exists()).toBe(false)
  })

  it("says a year with nothing in it had nothing, and an association with nothing has nothing yet", async () => {
    answer(() => ({events: []}))
    const wrapper = await mountArchive()
    expect(wrapper.get("[data-testid=event-archive-none]").text()).toBe("No past events yet.")

    await wrapper.get("[data-testid=event-archive-year-2025]").trigger("click")
    await flushPromises()
    expect(wrapper.get("[data-testid=event-archive-none]").text()).toBe("Nothing happened in that year.")
  })

  it("shows older events a page further back, with how many are shown", async () => {
    const page = (at: number) => Array.from({length: at === 0 ? PAST_PAGE : 2}, (_, i) =>
      event(100 - at * PAST_PAGE - i, "2026-05-01T19:00:00"))
    answer(query => ({events: page(query.page as number), page: {totalElements: PAST_PAGE + 2}}))
    const wrapper = await mountArchive()

    expect(wrapper.get("[data-testid=event-archive-shown]").text()).toBe(`Showing ${PAST_PAGE} of ${PAST_PAGE + 2}`)
    await wrapper.get("[data-testid=event-archive-older]").trigger("click")
    await flushPromises()

    expect(listings().at(-1)).toMatchObject({page: 1})
    expect(wrapper.findAllComponents(RouterLinkStub)).toHaveLength(PAST_PAGE + 2)
    expect(wrapper.get("[data-testid=event-archive-shown]").text()).toBe(`Showing ${PAST_PAGE + 2} of ${PAST_PAGE + 2}`)
    expect(wrapper.find("[data-testid=event-archive-older]").exists()).toBe(false)
  })

  it("drops an answer a newer read overtook", async () => {
    let late: (value: unknown) => void = () => {}
    mockReadPage.mockImplementation((query: Record<string, unknown>) => {
      if (query.size === 1) return Promise.resolve({events: []})
      if (query.from === undefined && query.page === 0 && listings().length === 1) return new Promise(done => { late = done })
      return Promise.resolve({events: [event(5, "2026-01-01T19:00:00")], page: {totalElements: 1}})
    })
    const wrapper = mount(EventArchive, {global: {stubs: {RouterLink: RouterLinkStub}}})
    await flushPromises()

    await wrapper.get("[data-testid=event-archive-search]").setValue("x")
    vi.advanceTimersByTime(SEARCH_REST_MS)
    await flushPromises()
    late({events: [event(6, "2026-02-01T19:00:00")], page: {totalElements: 1}})
    await flushPromises()

    expect(wrapper.findAllComponents(RouterLinkStub).map(one => (one.props("to") as {params: {id: number}}).params.id)).toEqual([5])
    // No first event, so no years beyond all of them.
    expect(wrapper.findAll("[role=radio]")).toHaveLength(1)
  })

  it("draws the posters without committee names where those cannot be read", async () => {
    mockCommittees.mockRejectedValue(new Error("down"))
    const wrapper = await mountArchive()

    expect(wrapper.findAll(".archive__by").map(one => one.text())).toEqual(["", "Member's initiative", ""])
  })
})
