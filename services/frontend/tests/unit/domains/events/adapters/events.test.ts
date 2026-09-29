import {describe, expect, it, vi} from "vitest"
import {
  deleteEvent,
  eventFileUrl,
  listEvents,
  readEvent,
  readEventBanner,
  readEventPage,
  saveEvent,
  saveEventBanner,
  saveNewEvent,
  setEventApproved,
} from "@/domains/events/adapters/events"
import {
  apiUrl,
  approveEvent,
  createEvent,
  deleteEventById,
  downloadEventBanner,
  findEventById,
  findEvents,
  updateEvent,
  uploadEventBanner,
} from "@/services/api"
import {FileType} from "@/services/api"
import type {CreateEventRequest} from "@/services/api"
import {anEvent} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

const lan: CreateEventRequest = {
  title: "LAN",
  description: "All night.",
  startTime: "2026-11-01T18:00:00Z",
  endTime: "2026-11-02T06:00:00Z",
  committeeId: 900,
  approved: false,
  membersOnly: false,
  signUp: false,
  gameCodes: [],
  pingedRoles: [],
}

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findEventById: vi.fn(),
  findEvents: vi.fn(),
  createEvent: vi.fn(),
  updateEvent: vi.fn(),
  approveEvent: vi.fn(),
  deleteEventById: vi.fn(),
  downloadEventBanner: vi.fn(),
  uploadEventBanner: vi.fn(),
  apiUrl: vi.fn(),
}))

describe("readEvent", () => {
  it("answers with the event behind the number", async () => {
    vi.mocked(findEventById).mockResolvedValue(answer(findEventById, anEvent({id: 33, title: "Hackathon"})))

    await expect(readEvent(33)).resolves.toEqual(anEvent({id: 33, title: "Hackathon"}))
  })

  it("throws on a refusal rather than answering with no event", async () => {
    vi.mocked(findEventById).mockRejectedValue(new Error("refused"))

    await expect(readEvent(33)).rejects.toBeDefined()
  })
})

describe("listEvents", () => {
  it("answers with the events the query names", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [anEvent({id: 1}), anEvent({id: 2})]}))

    await expect(listEvents({from: "2026-01-01"})).resolves.toHaveLength(2)
    expect(findEvents).toHaveBeenCalledWith({query: {from: "2026-01-01"}, throwOnError: true})
  })

  it("asks for everything where no query is given", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {}))

    await expect(listEvents()).resolves.toEqual([])
    expect(findEvents).toHaveBeenCalledWith({query: {}, throwOnError: true})
  })

  it("throws on a refusal rather than answering with an empty listing", async () => {
    vi.mocked(findEvents).mockRejectedValue(new Error("refused"))

    await expect(listEvents()).rejects.toBeDefined()
  })
})

describe("readEventPage", () => {
  it("answers with the page and what the api said about the rest", async () => {
    vi.mocked(findEvents).mockResolvedValue(answer(findEvents, {content: [anEvent({id: 1})], page: {totalElements: 9}}))

    await expect(readEventPage({page: 0})).resolves.toEqual({
      events: [anEvent({id: 1})],
      page: {totalElements: 9},
    })
  })

  it("answers with an empty page where the read failed, so the pane stays empty", async () => {
    vi.mocked(findEvents).mockResolvedValue(refusal(findEvents, {status: 500}))

    await expect(readEventPage({})).resolves.toEqual({events: [], page: undefined})
  })
})

describe("saveNewEvent", () => {
  it("answers with the recorded event", async () => {
    vi.mocked(createEvent).mockResolvedValue(answer(createEvent, anEvent({id: 7})))

    await expect(saveNewEvent(lan)).resolves.toMatchObject({id: 7})
    expect(createEvent).toHaveBeenCalledWith({body: lan, throwOnError: true})
  })
})

describe("saveEvent", () => {
  it("names the event on the path", async () => {
    vi.mocked(updateEvent).mockResolvedValue(answer(updateEvent, anEvent({id: 7})))

    await expect(saveEvent(7, {...lan, version: 2})).resolves.toMatchObject({id: 7})
    expect(updateEvent).toHaveBeenCalledWith({
      path: {id: 7},
      body: {...lan, version: 2},
      throwOnError: true,
    })
  })
})

describe("setEventApproved", () => {
  it("carries the approval as the query the api reads it from", async () => {
    vi.mocked(approveEvent).mockResolvedValue(answer(approveEvent, anEvent({id: 7, approved: true})))

    await expect(setEventApproved(7, true)).resolves.toMatchObject({approved: true})
    expect(approveEvent).toHaveBeenCalledWith({
      path: {id: 7},
      query: {approved: true},
      throwOnError: true,
    })
  })
})

describe("deleteEvent", () => {
  it("removes the event", async () => {
    vi.mocked(deleteEventById).mockResolvedValue(emptyAnswer(deleteEventById))

    await expect(deleteEvent(4)).resolves.toBeUndefined()
    expect(deleteEventById).toHaveBeenCalledWith({path: {eventId: 4}, throwOnError: true})
  })

  it("throws on a refusal so the card says the removal did not happen", async () => {
    vi.mocked(deleteEventById).mockRejectedValue(new Error("refused"))

    await expect(deleteEvent(4)).rejects.toBeDefined()
  })
})

describe("readEventBanner", () => {
  it("asks for the bytes a browser can draw", async () => {
    const blob = new Blob(["banner"])
    vi.mocked(downloadEventBanner).mockResolvedValue(answer(downloadEventBanner, blob))

    await expect(readEventBanner(4)).resolves.toBe(blob)
    expect(downloadEventBanner).toHaveBeenCalledWith({
      path: {eventId: 4},
      throwOnError: true,
      responseType: "blob",
    })
  })
})

describe("saveEventBanner", () => {
  it("answers with the file the banner was stored as", async () => {
    vi.mocked(uploadEventBanner).mockResolvedValue(answer(uploadEventBanner, {
      id: 88,
      name: "banner.png",
      path: "files/banner.png",
      mediaType: "image/png",
      type: FileType.EVENT_BANNER,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      version: 0,
    }))
    const file = new File(["bytes"], "banner.png")

    await expect(saveEventBanner(file)).resolves.toEqual({id: 88})
    expect(uploadEventBanner).toHaveBeenCalledWith({body: {file}, throwOnError: true})
  })
})

describe("eventFileUrl", () => {
  it("serves a stored path from wherever the api lives", () => {
    vi.mocked(apiUrl).mockReturnValue("https://api.example.com/files/1")

    expect(eventFileUrl("/files/1")).toBe("https://api.example.com/files/1")
    expect(apiUrl).toHaveBeenCalledWith("/files/1")
  })
})
