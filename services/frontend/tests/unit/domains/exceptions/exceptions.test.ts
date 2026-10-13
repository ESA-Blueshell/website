import {describe, expect, it, vi} from "vitest"
import {findException, listExceptions, resolveException} from "@/services/api"
import {concernLink, loadExceptions, markResolved, matchesSearch, readException, shortPlace, shortType} from "@/domains/exceptions"
import {answer, emptyAnswer, refusal} from "../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  listExceptions: vi.fn(),
  findException: vi.fn(),
  resolveException: vi.fn(),
}))

const fault = {
  id: 3,
  exceptionType: "java.lang.IllegalStateException",
  thrownAt: "net.blueshell.api.contact.Sync.run",
  firstSeenAt: "2026-09-30T08:00:00Z",
  lastSeenAt: "2026-09-30T09:00:00Z",
  occurrences: 4,
  latestMessage: "Brevo said no",
  latestSource: "JOB" as const,
  latestConcern: "contact.sync",
  latestJobExecutionId: 12,
}

describe("the exceptions adapter", () => {
  it("lists open, resolved or every fault", async () => {
    vi.mocked(listExceptions).mockResolvedValue(answer(listExceptions, [fault]))

    await expect(loadExceptions(false)).resolves.toEqual([fault])
    expect(listExceptions).toHaveBeenLastCalledWith({query: {resolved: false}})
    await loadExceptions(null)
    expect(listExceptions).toHaveBeenLastCalledWith({query: {}})
  })

  it("tells a list it could not read from an empty one", async () => {
    vi.mocked(listExceptions).mockResolvedValueOnce(refusal(listExceptions, {detail: "Forbidden"}))
    await expect(loadExceptions(null)).resolves.toBeNull()

    vi.mocked(listExceptions).mockResolvedValueOnce(answer(listExceptions, []))
    await expect(loadExceptions(null)).resolves.toEqual([])
  })

  it("tells a fault the api does not have from one it could not read", async () => {
    vi.mocked(findException).mockResolvedValueOnce(refusal(findException, {detail: "Not found"}, 404))
    await expect(readException(3)).resolves.toEqual({fault: null, missing: true})

    vi.mocked(findException).mockResolvedValueOnce(refusal(findException, null, 500))
    await expect(readException(3)).resolves.toEqual({fault: null, missing: false})
  })

  it("opens one fault", async () => {
    vi.mocked(findException).mockResolvedValue(answer(findException, fault))

    await expect(readException(3)).resolves.toMatchObject({fault: {id: 3}, missing: false})
    expect(findException).toHaveBeenCalledWith({path: {id: 3}})
  })

  it("marks a fault resolved, and says why when it cannot", async () => {
    vi.mocked(resolveException).mockResolvedValueOnce(answer(resolveException, fault))
    await expect(markResolved(3)).resolves.toMatchObject({ok: true})

    vi.mocked(resolveException).mockResolvedValueOnce(emptyAnswer(resolveException))
    await expect(markResolved(3)).resolves.toEqual({ok: false, reason: "That exception could not be marked resolved."})
  })
})

describe("reading a fault", () => {
  it("shortens its type and place, and finds it by any word", () => {
    expect(shortType("java.lang.IllegalStateException")).toBe("IllegalStateException")
    expect(shortPlace("net.blueshell.api.contact.Sync.run")).toBe("Sync.run")
    expect(shortPlace("unknown")).toBe("unknown")
    expect(matchesSearch(fault, "brevo sync")).toBe(true)
    expect(matchesSearch(fault, "discord")).toBe(false)
    expect(matchesSearch({...fault, latestMessage: null}, "")).toBe(true)
  })

  it("links a job's fault to the job, and a request's to nothing", () => {
    expect(concernLink(fault)).toBe("/management/jobs/12")
    expect(concernLink({...fault, latestJobExecutionId: null})).toBeNull()
  })
})
