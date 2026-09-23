import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  loadAssociationNumbers,
  loadCurrentContributionPeriod,
  loadEventOnShow,
  loadEventsOnShow,
  loadUpcomingEvents,
} from "@/domains/association/adapters/association"
import {
  apiUrl,
  associationStatistics,
  findCurrentContributionPeriod,
  findEventById,
  findEvents,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  associationStatistics: vi.fn(),
  findCurrentContributionPeriod: vi.fn(),
  findEventById: vi.fn(),
  findEvents: vi.fn(),
}))

/** A banner as the api answers with one: paths of its own, which the adapter has to resolve. */
const banner = {
  image: {
    url: "/files/poster.webp",
    path: "files/poster.webp",
    width: 1600,
    height: 900,
    renditions: [{url: "/files/poster-800.webp", width: 800}],
  },
}

const answered = (over: Record<string, unknown> = {}) => ({
  id: 7,
  title: "Ye Olde Quest for the Eleven Ales",
  startTime: "2026-10-03T12:00:00Z",
  endTime: "2026-10-03T21:59:00Z",
  location: "Witbreuksweg 401B",
  description: "Hear ye, hear ye",
  membersOnly: true,
  banner,
  ...over,
})

beforeEach(() => {
  vi.clearAllMocks()
})

/**
 * Nothing rather than zeroes.
 *
 * The pages these feed show honest floors until real figures land, and a zero would read as a
 * fact the association had stated about itself.
 */
describe("loadAssociationNumbers", () => {
  it("answers with the numbers the api states", async () => {
    vi.mocked(associationStatistics).mockResolvedValue({data: {members: 312}} as never)

    expect(await loadAssociationNumbers()).toEqual({members: 312})
  })

  it("answers with nothing where the api refused, and where it said nothing", async () => {
    vi.mocked(associationStatistics).mockResolvedValue({error: {}} as never)
    expect(await loadAssociationNumbers()).toBeNull()

    vi.mocked(associationStatistics).mockResolvedValue({data: undefined} as never)
    expect(await loadAssociationNumbers()).toBeNull()
  })
})

describe("loadCurrentContributionPeriod", () => {
  it("answers with the period the association is charging for", async () => {
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue({data: {id: 4}} as never)

    expect(await loadCurrentContributionPeriod()).toEqual({id: 4})
  })

  it("answers with nothing where none is recorded, and where the api refused", async () => {
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue({data: undefined} as never)
    expect(await loadCurrentContributionPeriod()).toBeNull()

    vi.mocked(findCurrentContributionPeriod).mockResolvedValue({error: {}} as never)
    expect(await loadCurrentContributionPeriod()).toBeNull()
  })
})

describe("loadEventOnShow", () => {
  it("reads one event by its id, with its banner resolved against the api", async () => {
    vi.mocked(findEventById).mockResolvedValue({data: answered()} as never)

    const one = await loadEventOnShow(7)

    expect(findEventById).toHaveBeenCalledWith({path: {id: 7}})
    expect(one).toMatchObject({
      id: 7,
      title: "Ye Olde Quest for the Eleven Ales",
      location: "Witbreuksweg 401B",
      description: "Hear ye, hear ye",
      membersOnly: true,
    })
    expect(one?.banner).toEqual({
      url: apiUrl("/files/poster.webp"),
      path: "files/poster.webp",
      width: 1600,
      height: 900,
      renditions: [{url: apiUrl("/files/poster-800.webp"), width: 800}],
    })
  })

  it("leaves out what the api left empty", async () => {
    vi.mocked(findEventById).mockResolvedValue({
      data: answered({location: null, description: null, banner: undefined}),
    } as never)

    const one = await loadEventOnShow(7)

    expect(one?.location).toBeUndefined()
    expect(one?.description).toBeUndefined()
    expect(one?.banner).toBeUndefined()
  })

  /* A picture the api stored before it recorded its own measurements. */
  it("draws a banner the api measured nothing about", async () => {
    vi.mocked(findEventById).mockResolvedValue({
      data: answered({banner: {image: {url: "/files/poster.webp"}}}),
    } as never)

    expect((await loadEventOnShow(7))?.banner).toEqual({
      url: apiUrl("/files/poster.webp"),
      path: "",
      width: undefined,
      height: undefined,
      renditions: [],
    })
  })

  it("answers with nothing where the api names no event", async () => {
    vi.mocked(findEventById).mockResolvedValue({error: {}} as never)

    expect(await loadEventOnShow(7)).toBeUndefined()
  })
})

describe("loadEventsOnShow", () => {
  it("asks for the approved events with art that have already run", async () => {
    vi.mocked(findEvents).mockResolvedValue({data: {content: [answered()]}} as never)

    const events = await loadEventsOnShow(6, 2)

    const asked = vi.mocked(findEvents).mock.calls[0][0] as {query: Record<string, unknown>}
    expect(asked.query).toMatchObject({approved: true, hasBanner: true, page: 2, size: 6})
    expect(asked.query.sort).toEqual(["startTime,desc"])
    expect(events).toHaveLength(1)
    expect(events[0].banner?.renditions).toEqual([{url: apiUrl("/files/poster-800.webp"), width: 800}])
  })

  /* An event with no id cannot be linked to, so there is nothing a strip could do with it. */
  it("passes over an event the api answers without an id", async () => {
    vi.mocked(findEvents).mockResolvedValue({
      data: {content: [answered({id: undefined}), answered()]},
    } as never)

    expect(await loadEventsOnShow(6)).toHaveLength(1)
  })

  it("leaves out what the api left empty, banner and all", async () => {
    vi.mocked(findEvents).mockResolvedValue({
      data: {content: [answered({
        location: null,
        description: null,
        banner: {image: {url: "/files/poster.webp"}},
      })]},
    } as never)

    const [one] = await loadEventsOnShow(6)

    expect(one.location).toBeUndefined()
    expect(one.description).toBeUndefined()
    expect(one.banner).toEqual({
      url: apiUrl("/files/poster.webp"),
      path: "",
      width: undefined,
      height: undefined,
      renditions: [],
    })
  })

  it("passes over an event whose banner record has lost its file", async () => {
    vi.mocked(findEvents).mockResolvedValue({
      data: {content: [answered({banner: {}})]},
    } as never)

    expect((await loadEventsOnShow(6))[0].banner).toBeUndefined()
  })

  it("answers with nothing where the api answers with nothing", async () => {
    vi.mocked(findEvents).mockResolvedValue({error: {}} as never)

    expect(await loadEventsOnShow(6)).toEqual([])
  })
})

describe("loadUpcomingEvents", () => {
  it("asks for the approved events still to come, soonest first, and says how many there are", async () => {
    vi.mocked(findEvents).mockResolvedValue({data: {
      content: [answered({signUp: true, signUpCount: 3, signUpLimit: 20, signUpDeadline: "2026-10-01T12:00:00Z"})],
      page: {totalElements: 14},
    }} as never)

    const {events, total} = await loadUpcomingEvents(8, 1)

    const asked = vi.mocked(findEvents).mock.calls[0][0] as {query: Record<string, unknown>}
    expect(asked.query).toMatchObject({approved: true, page: 1, size: 8, sort: ["startTime,asc"]})
    expect(typeof asked.query.from).toBe("string")
    expect(asked.query.hasBanner).toBeUndefined()
    expect(total).toBe(14)
    expect(events[0]).toMatchObject({id: 7, signUp: true, signUpCount: 3, signUpLimit: 20})
    expect(events[0].banner?.url).toBe(apiUrl("/files/poster.webp"))
  })

  it("counts what it holds where the api gives no total, and leaves out what it left empty", async () => {
    vi.mocked(findEvents).mockResolvedValue({data: {content: [answered({
      banner: null, location: null, description: null, signUp: false, signUpCount: 0,
      signUpLimit: null, signUpDeadline: null,
    })]}} as never)

    const {events, total} = await loadUpcomingEvents(8)

    expect(total).toBe(1)
    expect(events[0]).toMatchObject({banner: undefined, location: undefined, signUpLimit: undefined})
  })

  it("answers an empty page when the api refuses, and when it answers nothing", async () => {
    vi.mocked(findEvents).mockResolvedValueOnce({error: {status: 500}} as never)
    expect(await loadUpcomingEvents(8)).toEqual({events: [], total: 0})

    vi.mocked(findEvents).mockResolvedValueOnce({data: {}} as never)
    expect(await loadUpcomingEvents(8)).toEqual({events: [], total: 0})
  })
})
