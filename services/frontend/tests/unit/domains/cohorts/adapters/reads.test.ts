import {beforeEach, describe, expect, it, vi} from "vitest"
import {adoptPeople, fetchTargetFolders} from "@/domains/cohorts/adapters/cohorts"
import {applyInboundReconcile, listCohortTargetFolders, previewInboundReconcile} from "@/services/api"
import {answer} from "../../../helpers/sdkAnswers"
import {TargetSystem} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  listCohortTargetFolders: vi.fn(),
  previewInboundReconcile: vi.fn(),
  applyInboundReconcile: vi.fn(),
}))

/**
 * The sdk resolves on 4xx/5xx by default, so each of these reads asks it to throw. Without
 * that a 500 arrives as an empty list and the page reports it as "nothing here".
 */
describe("cohort reads tell an empty answer from a failed one", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("a system with no folders reads as empty, and a failed listing throws", async () => {
    vi.mocked(listCohortTargetFolders).mockResolvedValue(answer(listCohortTargetFolders, []))
    await expect(fetchTargetFolders(TargetSystem.BREVO)).resolves.toEqual([])
    expect(listCohortTargetFolders).toHaveBeenCalledWith({path: {system: TargetSystem.BREVO}, throwOnError: true})

    vi.mocked(listCohortTargetFolders).mockRejectedValue(new Error("boom"))
    await expect(fetchTargetFolders(TargetSystem.BREVO)).rejects.toThrow("boom")
  })
})

describe("taking people in from a list", () => {
  const row = (externalUserId: string, writable: boolean) => ({externalUserId, writable, alreadyMember: false})
  const preview = (writerSupported = true) => ({
    cohortLabel: "Paid", definitionKey: "PERIOD_PAYERS:4", previewToken: "tok", remoteCount: 3, skipped: [], writerSupported,
    matched: [row("a", true), row("b", false), row("c", true)],
  })

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("applies only the named contacts the preview can take in, under its token", async () => {
    vi.mocked(previewInboundReconcile).mockResolvedValue(answer(previewInboundReconcile, preview()))
    vi.mocked(applyInboundReconcile).mockResolvedValue(answer(applyInboundReconcile, {acceptedCount: 1, skippedCount: 0}))

    await expect(adoptPeople(1, 2, ["a", "b"])).resolves.toEqual({ok: true, saved: 1})
    expect(applyInboundReconcile).toHaveBeenCalledWith({path: {id: 1, targetId: 2}, body: {previewToken: "tok", selectedExternalUserIds: ["a"]}, throwOnError: true})
  })

  it("refuses where nobody named can be taken in, the cohort takes nobody in, or the preview fails", async () => {
    vi.mocked(previewInboundReconcile).mockResolvedValue(answer(previewInboundReconcile, preview()))
    await expect(adoptPeople(1, 2, ["b"])).resolves.toEqual({ok: false, reason: "None of them can be taken in from Brevo."})
    vi.mocked(previewInboundReconcile).mockResolvedValue(answer(previewInboundReconcile, preview(false)))
    await expect(adoptPeople(1, 2, ["a"])).resolves.toEqual({ok: false, reason: "None of them can be taken in from Brevo."})
    vi.mocked(previewInboundReconcile).mockRejectedValue(new Error("boom"))
    await expect(adoptPeople(1, 2, ["a"])).resolves.toEqual({ok: false, reason: "They could not be taken in."})
    expect(applyInboundReconcile).not.toHaveBeenCalled()
  })
})
