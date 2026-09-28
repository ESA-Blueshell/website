import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  loadAssociationNumbers,
  loadCurrentContributionPeriod,
  loadEventsOnShow,
  loadUpcomingEvents,
} from "@/domains/association/adapters/association"
import {
  apiUrl,
  associationStatistics,
  findCurrentContributionPeriod,
  findEvents,
} from "@/services/api"
import type {Image} from "@/services/api"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"
import {aBanner, aContributionPeriod, anEvent, associationNumbers} from "../../../helpers/apiFixtures"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  associationStatistics: vi.fn(),
  findCurrentContributionPeriod: vi.fn(),
  findEvents: vi.fn(),
}))

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
    vi.mocked(associationStatistics).mockResolvedValue(answer(associationStatistics, associationNumbers({boards: 13})))

    expect(await loadAssociationNumbers()).toEqual(associationNumbers({boards: 13}))
  })

  it("answers with nothing where the api refused, and where it said nothing", async () => {
    vi.mocked(associationStatistics).mockResolvedValue(refusal(associationStatistics, {}))
    expect(await loadAssociationNumbers()).toBeNull()

    vi.mocked(associationStatistics).mockResolvedValue(emptyAnswer(associationStatistics))
    expect(await loadAssociationNumbers()).toBeNull()
  })
})

describe("loadCurrentContributionPeriod", () => {
  it("answers with the period the association is charging for", async () => {
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue(answer(findCurrentContributionPeriod, aContributionPeriod()))

    expect(await loadCurrentContributionPeriod()).toEqual(aContributionPeriod())
  })

  it("answers with nothing where none is recorded, and where the api refused", async () => {
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue(emptyAnswer(findCurrentContributionPeriod))
    expect(await loadCurrentContributionPeriod()).toBeNull()

    vi.mocked(findCurrentContributionPeriod).mockResolvedValue(refusal(findCurrentContributionPeriod, {}))
    expect(await loadCurrentContributionPeriod()).toBeNull()
  })
})

describe("loadEventsOnShow", () => {
  it("leaves out what the api left empty", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [anEvent({location: null, description: null})]}))

    const [one] = await loadEventsOnShow(6)

    expect(one?.location).toBeUndefined()
    expect(one?.description).toBeUndefined()
  })

  /* A picture the api stored before it recorded its own measurements. */
  it("draws a banner the api measured nothing about", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [// Stored before the api recorded a picture's path and measurements, which its type now requires.
      anEvent({banner: aBanner({image: {url: "/files/poster.webp"} as Image})})]}))

    expect((await loadEventsOnShow(6))[0]?.banner).toEqual({
      url: apiUrl("/files/poster.webp"),
      path: "",
      width: undefined,
      height: undefined,
      renditions: [],
    })
  })

  it("asks for the approved events with art that have already run", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [anEvent()]}))

    const events = await loadEventsOnShow(6, 2)

    const asked = vi.mocked(findEvents).mock.lastCall?.[0]?.query
    expect(asked).toMatchObject({approved: true, hasBanner: true, page: 2, size: 6})
    expect(asked?.sort).toEqual(["startTime,desc"])
    expect(events).toHaveLength(1)
    expect(events[0]?.banner?.renditions).toEqual([{url: apiUrl("/files/poster-800.webp"), width: 800}])
  })

  /* An event with no id cannot be linked to, so there is nothing a strip could do with it. */
  it("passes over an event the api answers without an id", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [anEvent({id: undefined}), anEvent()]}))

    expect(await loadEventsOnShow(6)).toHaveLength(1)
  })

  it("leaves out what the api left empty, banner and all", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [anEvent({
        location: null,
        description: null,
        banner: aBanner({image: {url: "/files/poster.webp"} as Image}),
      })]}))

    const [one] = await loadEventsOnShow(6)

    expect(one?.location).toBeUndefined()
    expect(one?.description).toBeUndefined()
    expect(one?.banner).toEqual({
      url: apiUrl("/files/poster.webp"),
      path: "",
      width: undefined,
      height: undefined,
      renditions: [],
    })
  })

  it("passes over an event whose banner record has lost its file", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [anEvent({banner: aBanner({image: null})})]}))

    expect((await loadEventsOnShow(6))[0]?.banner).toBeUndefined()
  })

  it("answers with nothing where the api answers with nothing", async () => {
    vi.mocked(findEvents).mockResolvedValue(refusal(findEvents, {}))

    expect(await loadEventsOnShow(6)).toEqual([])
  })
})

describe("loadUpcomingEvents", () => {
  it("asks for the approved events still to come, soonest first, and says how many there are", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {
      content: [anEvent({signUp: true, signUpCount: 3, signUpLimit: 20, signUpDeadline: "2026-10-01T12:00:00Z"})],
      page: {totalElements: 14},
    }))

    const {events, total} = await loadUpcomingEvents(8, 1)

    const asked = vi.mocked(findEvents).mock.lastCall?.[0]?.query
    expect(asked).toMatchObject({approved: true, page: 1, size: 8, sort: ["startTime,asc"]})
    expect(typeof asked?.from).toBe("string")
    expect(asked?.hasBanner).toBeUndefined()
    expect(total).toBe(14)
    expect(events[0]).toMatchObject({id: 7, signUp: true, signUpCount: 3, signUpLimit: 20})
    expect(events[0]?.banner?.url).toBe(apiUrl("/files/poster.webp"))
  })

  it("counts what it holds where the api gives no total, and leaves out what it left empty", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [anEvent({
      banner: null, location: null, description: null, signUp: false, signUpCount: 0,
      signUpLimit: null, signUpDeadline: null,
    })]}))

    const {events, total} = await loadUpcomingEvents(8)

    expect(total).toBe(1)
    expect(events[0]).toMatchObject({banner: undefined, location: undefined, signUpLimit: undefined})
  })

  it("answers an empty page when the api refuses, and when it answers nothing", async () => {
    vi.mocked(findEvents).mockResolvedValueOnce(refusal(findEvents, {status: 500}))
    expect(await loadUpcomingEvents(8)).toEqual({events: [], total: 0})

    vi.mocked(findEvents).mockResolvedValueOnce(answer(findEvents, {}))
    expect(await loadUpcomingEvents(8)).toEqual({events: [], total: 0})
  })
})
