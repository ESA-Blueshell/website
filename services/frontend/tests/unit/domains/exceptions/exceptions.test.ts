import {describe, expect, it, vi} from "vitest"
import {findException, listExceptions, resolveException} from "@/services/api"
import {concernLink, loadException, loadExceptions, markResolved, matchesSearch, shortPlace, shortType} from "@/domains/exceptions"
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

  it("reads an unreadable list as none, and an unreadable fault as nothing", async () => {
    vi.mocked(listExceptions).mockResolvedValue(refusal(listExceptions, {detail: "Forbidden"}))
    vi.mocked(findException).mockResolvedValue(refusal(findException, {detail: "Not found"}))

    await expect(loadExceptions(null)).resolves.toEqual([])
    await expect(loadException(3)).resolves.toBeNull()
  })

  it("opens one fault", async () => {
    vi.mocked(findException).mockResolvedValue(answer(findException, fault))

    await expect(loadException(3)).resolves.toMatchObject({id: 3})
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
