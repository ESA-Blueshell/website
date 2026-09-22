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
    vi.mocked(findEventById).mockResolvedValue({data: {id: 33, title: "Hackathon"}} as never)

    await expect(readEvent(33)).resolves.toEqual({id: 33, title: "Hackathon"})
  })

  it("throws on a refusal rather than answering with no event", async () => {
    vi.mocked(findEventById).mockRejectedValue(new Error("refused"))

    await expect(readEvent(33)).rejects.toBeDefined()
  })
})

describe("listEvents", () => {
  it("answers with the events the query names", async () => {
    vi.mocked(findEvents).mockResolvedValue({data: {content: [{id: 1}, {id: 2}]}} as never)

    await expect(listEvents({from: "2026-01-01"} as never)).resolves.toHaveLength(2)
    expect(findEvents).toHaveBeenCalledWith({query: {from: "2026-01-01"}, throwOnError: true})
  })

  it("asks for everything where no query is given", async () => {
    vi.mocked(findEvents).mockResolvedValue({data: {}} as never)

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
    vi.mocked(findEvents).mockResolvedValue({
      data: {content: [{id: 1}], page: {totalElements: 9}},
    } as never)

    await expect(readEventPage({page: 0} as never)).resolves.toEqual({
      events: [{id: 1}],
      page: {totalElements: 9},
    })
  })

  it("answers with an empty page where the read failed, so the pane stays empty", async () => {
    vi.mocked(findEvents).mockResolvedValue({error: {status: 500}} as never)

    await expect(readEventPage({} as never)).resolves.toEqual({events: [], page: undefined})
  })
})

describe("saveNewEvent", () => {
  it("answers with the recorded event", async () => {
    vi.mocked(createEvent).mockResolvedValue({data: {id: 7}} as never)

    await expect(saveNewEvent({title: "LAN"} as never)).resolves.toMatchObject({id: 7})
    expect(createEvent).toHaveBeenCalledWith({body: {title: "LAN"}, throwOnError: true})
  })
})

describe("saveEvent", () => {
  it("names the event on the path", async () => {
    vi.mocked(updateEvent).mockResolvedValue({data: {id: 7}} as never)

    await expect(saveEvent(7, {version: 2} as never)).resolves.toMatchObject({id: 7})
    expect(updateEvent).toHaveBeenCalledWith({
      path: {id: 7},
      body: {version: 2},
      throwOnError: true,
    })
  })
})

describe("setEventApproved", () => {
  it("carries the approval as the query the api reads it from", async () => {
    vi.mocked(approveEvent).mockResolvedValue({data: {id: 7, approved: true}} as never)

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
    vi.mocked(deleteEventById).mockResolvedValue({} as never)

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
    vi.mocked(downloadEventBanner).mockResolvedValue({data: blob} as never)

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
    vi.mocked(uploadEventBanner).mockResolvedValue({data: {id: 88}} as never)
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
