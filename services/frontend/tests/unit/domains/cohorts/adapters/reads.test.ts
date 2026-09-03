import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  fetchInboundReconcilePreview,
  fetchTargetDescriptors,
  fetchTargetFolders,
  fetchTargetOptions,
} from "@/domains/cohorts/adapters/cohorts"
import {
  listCohortTargetFolders,
  listCohortTargetSystems,
  previewInboundReconcile,
  searchCohortTargets,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  listCohortTargetFolders: vi.fn(),
  listCohortTargetSystems: vi.fn(),
  previewInboundReconcile: vi.fn(),
  searchCohortTargets: vi.fn(),
}))

/**
 * The sdk resolves on 4xx/5xx by default, so each of these reads asks it to throw. Without
 * that a 500 arrives as an empty list and the page reports it as "nothing here".
 */
describe("cohort reads tell an empty answer from a failed one", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("an account with no target systems reads as empty", async () => {
    vi.mocked(listCohortTargetSystems).mockResolvedValue({data: []} as never)

    await expect(fetchTargetDescriptors()).resolves.toEqual([])
    expect(listCohortTargetSystems).toHaveBeenCalledWith({throwOnError: true})
  })

  it("target systems that could not be listed throw", async () => {
    vi.mocked(listCohortTargetSystems).mockRejectedValue(new Error("boom"))

    await expect(fetchTargetDescriptors()).rejects.toThrow("boom")
  })

  it("a system with no targets reads as empty, and a failed search throws", async () => {
    vi.mocked(searchCohortTargets).mockResolvedValue({data: []} as never)
    await expect(fetchTargetOptions("BREVO")).resolves.toEqual([])
    expect(searchCohortTargets).toHaveBeenCalledWith({path: {system: "BREVO"}, throwOnError: true})

    vi.mocked(searchCohortTargets).mockRejectedValue(new Error("boom"))
    await expect(fetchTargetOptions("BREVO")).rejects.toThrow("boom")
  })

  it("a system with no folders reads as empty, and a failed listing throws", async () => {
    vi.mocked(listCohortTargetFolders).mockResolvedValue({data: []} as never)
    await expect(fetchTargetFolders("BREVO")).resolves.toEqual([])
    expect(listCohortTargetFolders).toHaveBeenCalledWith({path: {system: "BREVO"}, throwOnError: true})

    vi.mocked(listCohortTargetFolders).mockRejectedValue(new Error("boom"))
    await expect(fetchTargetFolders("BREVO")).rejects.toThrow("boom")
  })

  it("a reconcile preview that could not be read throws rather than answering nothing", async () => {
    vi.mocked(previewInboundReconcile).mockRejectedValue(new Error("boom"))

    await expect(fetchInboundReconcilePreview(1, 2)).rejects.toThrow("boom")
    expect(previewInboundReconcile).toHaveBeenCalledWith({
      path: {id: 1, cohortId: 2},
      throwOnError: true,
    })
  })
})
