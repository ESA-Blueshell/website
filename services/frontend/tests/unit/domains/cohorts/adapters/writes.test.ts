import {describe, expect, it, vi} from "vitest"
import {
  applyInboundReconcileSelection,
  createTargetForSubject,
  linkExistingTargetForSubject,
  linkUserToExternal,
  moveTargetToFolder,
  moveTargetsToFolder,
  removeExternalMember,
  switchCohortTarget,
  triggerReconcile,
} from "@/domains/cohorts/adapters/cohorts"
import {
  applyInboundReconcile,
  createTarget,
  enqueue,
  linkExistingTarget,
  linkUser,
  moveCohortTarget,
  moveCohortTargets,
  switchTarget,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  applyInboundReconcile: vi.fn(),
  createTarget: vi.fn(),
  enqueue: vi.fn(),
  linkExistingTarget: vi.fn(),
  linkUser: vi.fn(),
  moveCohortTarget: vi.fn(),
  moveCohortTargets: vi.fn(),
  switchTarget: vi.fn(),
}))

/** How the client reports a refusal once `throwOnError` is on: an error carrying the response. */
const thrown = (status: number, data?: Record<string, unknown>) => ({response: {status, data}})

const target = (over: Record<string, unknown> = {}) => ({
  system: "BREVO",
  externalId: "17",
  kind: "LIST",
  label: "Paid members",
  ...over,
})

describe("enqueued cohort work", () => {
  it("answers with the job it started, so the page can follow it", async () => {
    vi.mocked(enqueue).mockResolvedValue({data: {id: 88}} as never)

    await expect(triggerReconcile(4)).resolves.toBe(88)
    expect(enqueue).toHaveBeenCalledWith({
      body: {jobType: "cohort.reconcile-list", payload: {cohortId: 4}},
      throwOnError: true,
    })
  })

  it("names the member to drop in the job's own payload", async () => {
    vi.mocked(enqueue).mockResolvedValue({data: {id: 89}} as never)

    await expect(removeExternalMember(4, "ext-1")).resolves.toBe(89)
    expect(enqueue).toHaveBeenCalledWith({
      body: {jobType: "cohort.remove-external-member", payload: {cohortId: 4, externalUserId: "ext-1"}},
      throwOnError: true,
    })
  })

  it("answers with no job where the api named none", async () => {
    vi.mocked(enqueue).mockResolvedValue({data: undefined} as never)

    await expect(triggerReconcile(4)).resolves.toBeNull()
  })
})

/**
 * A 409 here means the external account is already somebody else's, which is a thing the
 * operator resolves rather than an error. Anything else stays an error.
 */
describe("linkUserToExternal", () => {
  it("answers that the link was made", async () => {
    vi.mocked(linkUser).mockResolvedValue({data: undefined} as never)

    await expect(linkUserToExternal(1, 2, "BREVO", "ext-1")).resolves.toEqual({type: "ok"})
    expect(linkUser).toHaveBeenCalledWith({
      path: {id: 1},
      body: {userId: 2, system: "BREVO", externalUserId: "ext-1"},
      throwOnError: true,
    })
  })

  it("answers with the account already holding the external id", async () => {
    vi.mocked(linkUser).mockRejectedValue(
      thrown(409, {existingUserId: 7, system: "BREVO", existingUserFullName: "Roos Kruk"}),
    )

    await expect(linkUserToExternal(1, 2, "BREVO", "ext-1")).resolves.toEqual({
      type: "conflict",
      conflict: {existingUserId: 7, system: "BREVO", existingUserFullName: "Roos Kruk"},
    })
  })

  it("reports a conflict with an account nobody named a name for", async () => {
    vi.mocked(linkUser).mockRejectedValue(thrown(409, {existingUserId: 7, system: "BREVO"}))

    await expect(linkUserToExternal(1, 2, "BREVO", "ext-1")).resolves.toMatchObject({
      conflict: {existingUserFullName: null},
    })
  })

  it("leaves anything that is not a conflict to the caller's error path", async () => {
    vi.mocked(linkUser).mockRejectedValue(thrown(500))
    await expect(linkUserToExternal(1, 2, "BREVO", "ext-1")).rejects.toMatchObject({response: {status: 500}})

    // A 409 without a body says nothing about who holds the id, so it is not a conflict
    // the operator can act on either.
    vi.mocked(linkUser).mockRejectedValue(thrown(409))
    await expect(linkUserToExternal(1, 2, "BREVO", "ext-1")).rejects.toMatchObject({response: {status: 409}})
  })
})

describe("giving a subject's cohort a target", () => {
  it("maps the cohort to a target that already exists", async () => {
    vi.mocked(linkExistingTarget).mockResolvedValue({
      data: {cohortId: 4, system: "BREVO", externalId: "17", label: "Paid members", path: ["Members"]},
    } as never)

    await expect(linkExistingTargetForSubject(1, "BREVO", "17")).resolves.toEqual({
      type: "ok",
      mapping: {cohortId: 4, system: "BREVO", externalId: "17", label: "Paid members", path: ["Members"]},
    })
    expect(linkExistingTarget).toHaveBeenCalledWith({
      path: {id: 1},
      body: {system: "BREVO", externalId: "17"},
      throwOnError: true,
    })
  })

  // A system that files nothing sends no path, and a mapping the api has not resolved an id
  // for sends none: both read as absent rather than as undefined leaking into the page.
  it("reads a mapping the api sent no path or id for", async () => {
    vi.mocked(createTarget).mockResolvedValue({
      data: {cohortId: 4, system: "GOOGLE_CALENDAR", label: "Board"},
    } as never)

    await expect(createTargetForSubject(1, "GOOGLE_CALENDAR", "Board", null)).resolves.toEqual({
      type: "ok",
      mapping: {cohortId: 4, system: "GOOGLE_CALENDAR", externalId: null, label: "Board", path: []},
    })
    expect(createTarget).toHaveBeenCalledWith({
      path: {id: 1},
      body: {system: "GOOGLE_CALENDAR", label: "Board", folderHint: undefined},
      throwOnError: true,
    })
  })

  // The cohort already has a target, which is a state the operator resolves, not an error.
  it("answers with a conflict where the cohort is already mapped", async () => {
    vi.mocked(linkExistingTarget).mockRejectedValue(thrown(409))
    await expect(linkExistingTargetForSubject(1, "BREVO", "17")).resolves.toEqual({type: "conflict"})

    vi.mocked(createTarget).mockRejectedValue(thrown(409))
    await expect(createTargetForSubject(1, "BREVO", "Paid members", "Members")).resolves.toEqual({type: "conflict"})
  })

  it("leaves anything else to the caller's error path", async () => {
    vi.mocked(linkExistingTarget).mockRejectedValue(thrown(500))
    await expect(linkExistingTargetForSubject(1, "BREVO", "17")).rejects.toMatchObject({response: {status: 500}})

    vi.mocked(createTarget).mockRejectedValue(new Error("boom"))
    await expect(createTargetForSubject(1, "BREVO", "Paid members", null)).rejects.toThrow("boom")
  })

  it("repoints a mapping at another target, carrying both choices the operator made", async () => {
    vi.mocked(switchTarget).mockResolvedValue({
      data: {cohortId: 4, system: "BREVO", externalId: "18", label: "Paid members", path: []},
    } as never)

    await expect(switchCohortTarget(1, 4, "18", true, false)).resolves.toMatchObject({externalId: "18"})
    expect(switchTarget).toHaveBeenCalledWith({
      path: {id: 1, cohortId: 4},
      body: {externalId: "18", deletePrevious: true, reconcileNow: false},
      throwOnError: true,
    })
  })
})

describe("moving targets between folders", () => {
  it("answers with the target where it ended up", async () => {
    vi.mocked(moveCohortTarget).mockResolvedValue({data: target({folderLabel: "Archive"})} as never)

    await expect(moveTargetToFolder("BREVO", "17", "Archive")).resolves.toMatchObject({
      externalId: "17",
      folderLabel: "Archive",
      memberCount: null,
      linkedCohortId: null,
      path: [],
    })
    expect(moveCohortTarget).toHaveBeenCalledWith({
      path: {system: "BREVO", externalId: "17"},
      body: {folder: "Archive"},
      throwOnError: true,
    })
  })

  /*
   * The bulk move is the one write here that can half succeed. The api validates the whole
   * selection first, so a refusal means nothing moved; past that point each move is a separate
   * call to a system that cannot roll the earlier ones back, and the operator is told which.
   */
  it("reports a refused selection as nothing having moved", async () => {
    vi.mocked(moveCohortTargets).mockResolvedValue({
      response: {status: 409},
      error: {errors: [{code: "UnknownTargetIds", field: "externalIds", message: "Gone", refs: ["17"]}]},
      data: undefined,
    } as never)

    const outcome = await moveTargetsToFolder("BREVO", ["17"], "Archive")

    expect(outcome.status).toBe("refused")
    expect(outcome).toMatchObject({rejection: {namedRefs: ["17"], requiresReload: true, status: 409}})
  })

  it("reports the ones that moved and the ones the system would not move", async () => {
    vi.mocked(moveCohortTargets).mockResolvedValue({
      data: {
        moved: [target({externalId: "17", folderLabel: "Archive", memberCount: 12, linkedCohortId: 4, path: ["Archive"]})],
        failed: [{externalId: "18", label: "Guests", message: "The folder is full."}],
      },
    } as never)

    const outcome = await moveTargetsToFolder("BREVO", ["17", "18"], "Archive")

    expect(outcome).toEqual({
      status: "moved",
      result: {
        moved: [{
          system: "BREVO",
          externalId: "17",
          kind: "LIST",
          label: "Paid members",
          folderLabel: "Archive",
          memberCount: 12,
          linkedCohortId: 4,
          path: ["Archive"],
        }],
        failed: [{externalId: "18", label: "Guests", message: "The folder is full."}],
      },
    })
  })

  it("reads a move the api answered nothing about as a move that did not happen", async () => {
    vi.mocked(moveCohortTargets).mockResolvedValue({data: {}} as never)
    await expect(moveTargetsToFolder("BREVO", ["17"], "Archive")).resolves.toEqual({
      status: "moved",
      result: {moved: [], failed: []},
    })

    // Not a refusal the parser recognises and not an answer either, so neither outcome would
    // be true; a throw is what keeps the page from reporting a move nobody made.
    vi.mocked(moveCohortTargets).mockResolvedValue({response: {status: 500}, error: {}, data: undefined} as never)
    await expect(moveTargetsToFolder("BREVO", ["17"], "Archive")).rejects.toThrow("The move could not be sent.")
  })
})

describe("applyInboundReconcileSelection", () => {
  it("applies only the accounts the operator ticked, under the token they were previewed with", async () => {
    vi.mocked(applyInboundReconcile).mockResolvedValue({data: {linked: 2}} as never)

    await expect(applyInboundReconcileSelection(1, 4, "tok", ["ext-1", "ext-2"])).resolves.toEqual({linked: 2})
    expect(applyInboundReconcile).toHaveBeenCalledWith({
      path: {id: 1, cohortId: 4},
      body: {previewToken: "tok", selectedExternalUserIds: ["ext-1", "ext-2"]},
      throwOnError: true,
    })
  })
})
