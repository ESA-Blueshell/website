import {describe, expect, it, vi} from "vitest"
import {
  applyInboundReconcileSelection,
  applyTidy,
  fetchTidyPlan,
  archiveTarget,
  createFolderInSystem,
  createListInSystem,
  createTargetForCohort,
  deleteTarget,
  linkExistingTargetForCohort,
  linkDriftPeople,
  moveTargetToFolder,
  moveTargetsToFolder,
  proposeDriftLinks,
  pushDriftPeople,
  removeDriftPeople,
  setTargetEnforced,
  renameTarget,
  switchCohortTarget,
  evaluateMember,
  triggerReconcile,
} from "@/domains/cohorts/adapters/cohorts"
import {
  applyFolderTidy,
  applyInboundReconcile,
  archiveExternalTarget,
  createExternalTarget,
  createTarget,
  createTargetFolder,
  deleteExternalTarget,
  enforceTarget,
  evaluateUser,
  reconcileTarget,
  linkExistingTarget,
  linkDrift,
  moveCohortTarget,
  moveCohortTargets,
  previewFolderTidy,
  proposeLinks,
  pushDrift,
  removeDrift,
  renameExternalTarget,
  switchTarget,
} from "@/services/api"
import type {ExternalTarget} from "@/services/api"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"
import {TargetKind, TargetSystem} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  applyInboundReconcile: vi.fn(),
  applyFolderTidy: vi.fn(),
  previewFolderTidy: vi.fn(),
  archiveExternalTarget: vi.fn(),
  deleteExternalTarget: vi.fn(),
  createExternalTarget: vi.fn(),
  createTarget: vi.fn(),
  createTargetFolder: vi.fn(),
  renameExternalTarget: vi.fn(),
  enforceTarget: vi.fn(),
  evaluateUser: vi.fn(),
  reconcileTarget: vi.fn(),
  linkExistingTarget: vi.fn(),
  linkDrift: vi.fn(),
  proposeLinks: vi.fn(),
  pushDrift: vi.fn(),
  removeDrift: vi.fn(),
  moveCohortTarget: vi.fn(),
  moveCohortTargets: vi.fn(),
  switchTarget: vi.fn(),
}))

/** How the client reports a refusal once `throwOnError` is on: an error carrying the response. */
const thrown = (status: number, data?: Record<string, unknown>) => ({response: {status, data}})

const target = (over: Partial<ExternalTarget> = {}): ExternalTarget => ({
  system: TargetSystem.BREVO,
  externalId: "17",
  kind: TargetKind.LIST,
  label: "Paid members",
  path: [],
  ...over,
})

describe("work the board queues", () => {
  it("reconciles one target of a cohort, and looks at a member again", async () => {
    vi.mocked(reconcileTarget).mockResolvedValue(emptyAnswer(reconcileTarget))
    vi.mocked(evaluateUser).mockResolvedValue(emptyAnswer(evaluateUser))

    await expect(triggerReconcile(1, 4)).resolves.toEqual({ok: true})
    await expect(evaluateMember(7)).resolves.toEqual({ok: true})
    expect(reconcileTarget).toHaveBeenCalledWith({path: {id: 1, targetId: 4}})
    expect(evaluateUser).toHaveBeenCalledWith({path: {userId: 7}})
  })

  it("says why a reconcile was refused", async () => {
    vi.mocked(reconcileTarget).mockResolvedValue(refusal(reconcileTarget, {code: "TargetNotCreated", cohortId: 4}, 409))

    await expect(triggerReconcile(1, 4)).resolves.toEqual({ok: false, reason: "The target has not been created yet."})
  })
})

describe("resolving drift", () => {
  it("pushes and removes people, answering how many were still drifting", async () => {
    vi.mocked(pushDrift).mockResolvedValue(answer(pushDrift, {resolved: 2}))
    vi.mocked(removeDrift).mockResolvedValue(answer(removeDrift, {resolved: 1}))

    await expect(pushDriftPeople(1, 4, [5, 6])).resolves.toEqual({ok: true, saved: 2})
    await expect(removeDriftPeople(1, 4, ["ext-1"])).resolves.toEqual({ok: true, saved: 1})
    expect(pushDrift).toHaveBeenCalledWith({path: {id: 1, targetId: 4}, body: {userIds: [5, 6]}})
    expect(removeDrift).toHaveBeenCalledWith({path: {id: 1, targetId: 4}, body: {externalUserIds: ["ext-1"]}})
  })

  it("says why a target not yet created refuses", async () => {
    vi.mocked(pushDrift).mockResolvedValue(refusal(pushDrift, {code: "TargetNotCreated", targetId: 4}, 409))
    vi.mocked(removeDrift).mockResolvedValue(refusal(removeDrift, {code: "TargetNotOfCohort", targetId: 4}, 404))

    await expect(pushDriftPeople(1, 4, [5])).resolves.toEqual({ok: false, reason: "The target has not been created yet."})
    await expect(removeDriftPeople(1, 4, ["x"])).resolves.toEqual({ok: false, reason: "That target is not this cohort's; reload the page."})
  })

  it("proposes the account for each contact, and links the chosen ones", async () => {
    vi.mocked(proposeLinks).mockResolvedValue(answer(proposeLinks, [{externalUserId: "a", label: "a@example.com", userId: 5, userFullName: "Ada"}, {externalUserId: "b"}]))
    vi.mocked(linkDrift).mockResolvedValue(answer(linkDrift, {linked: 1, conflicts: []}))

    await expect(proposeDriftLinks(1, 4, ["a", "b"])).resolves.toEqual({
      ok: true,
      saved: [
        {externalUserId: "a", label: "a@example.com", userId: 5, userFullName: "Ada"},
        {externalUserId: "b", label: null, userId: null, userFullName: null},
      ],
    })
    await expect(linkDriftPeople(1, 4, [{externalUserId: "a", userId: 5}])).resolves.toEqual({ok: true, saved: {linked: 1, conflicts: []}})
    expect(linkDrift).toHaveBeenCalledWith({path: {id: 1, targetId: 4}, body: {links: [{externalUserId: "a", userId: 5}]}})
  })

  it("switches enforcement on a target", async () => {
    vi.mocked(enforceTarget).mockResolvedValue(emptyAnswer(enforceTarget))

    await expect(setTargetEnforced(1, 4, true)).resolves.toEqual({ok: true})
    expect(enforceTarget).toHaveBeenCalledWith({path: {id: 1, targetId: 4}, body: {enforced: true}})
  })

  it("answers a refused proposal as refused", async () => {
    vi.mocked(proposeLinks).mockResolvedValue(refusal(proposeLinks, {code: "TargetNotCreated", targetId: 4}, 409))

    await expect(proposeDriftLinks(1, 4, ["a"])).resolves.toMatchObject({ok: false})
  })
})

describe("giving a cohort's cohort a target", () => {
  it("maps the cohort to a target that already exists", async () => {
    vi.mocked(linkExistingTarget).mockResolvedValue(answer(linkExistingTarget, {
        targetId: 4,
        system: TargetSystem.BREVO,
        kind: TargetKind.LIST,
        externalId: "17",
        label: "Paid members",
        lastReconciledAt: "2026-01-05T10:00:00Z",
        path: ["Members"],
        folderKnown: true,
        runs: [],
        enforced: false,
      }))

    await expect(linkExistingTargetForCohort(1, TargetSystem.BREVO, "17")).resolves.toEqual({
      type: "ok",
      mapping: {
        targetId: 4,
        system: TargetSystem.BREVO,
        kind: TargetKind.LIST,
        externalId: "17",
        label: "Paid members",
        lastReconciledAt: "2026-01-05T10:00:00Z",
        path: ["Members"],
        folderKnown: true,
        runs: [],
        enforced: false,
      },
    })
    expect(linkExistingTarget).toHaveBeenCalledWith({
      path: {id: 1},
      body: {system: TargetSystem.BREVO, externalId: "17"},
      throwOnError: true,
    })
  })

  // A system that files nothing sends no path, a mapping the api has not resolved an id for
  // sends none, and a target nothing has reconciled yet sends no time: all three read as absent
  // rather than as undefined leaking into the page.
  it("reads a mapping the api sent no path, id or reconcile time for", async () => {
    vi.mocked(createTarget).mockResolvedValue(answer(createTarget, {targetId: 4, system: TargetSystem.GOOGLE_CALENDAR, kind: TargetKind.GROUP, label: "Board", path: [], folderKnown: true, runs: [], enforced: false}))

    await expect(createTargetForCohort(1, TargetSystem.GOOGLE_CALENDAR, "Board", null)).resolves.toEqual({
      type: "ok",
      mapping: {
        targetId: 4,
        system: TargetSystem.GOOGLE_CALENDAR,
        kind: TargetKind.GROUP,
        externalId: null,
        label: "Board",
        lastReconciledAt: null,
        path: [],
        folderKnown: true,
        runs: [],
        enforced: false,
      },
    })
    expect(createTarget).toHaveBeenCalledWith({
      path: {id: 1},
      body: {system: TargetSystem.GOOGLE_CALENDAR, label: "Board", folderHint: undefined},
      throwOnError: true,
    })
  })

  // The cohort already has a target, which is a state the operator resolves, not an error.
  it("answers with a conflict where the cohort is already mapped", async () => {
    vi.mocked(linkExistingTarget).mockRejectedValue(thrown(409))
    await expect(linkExistingTargetForCohort(1, TargetSystem.BREVO, "17")).resolves.toEqual({type: "conflict"})

    vi.mocked(createTarget).mockRejectedValue(thrown(409))
    await expect(createTargetForCohort(1, TargetSystem.BREVO, "Paid members", "Members")).resolves.toEqual({type: "conflict"})
  })

  it("leaves anything else to the caller's error path", async () => {
    vi.mocked(linkExistingTarget).mockRejectedValue(thrown(500))
    await expect(linkExistingTargetForCohort(1, TargetSystem.BREVO, "17")).rejects.toMatchObject({response: {status: 500}})

    vi.mocked(createTarget).mockRejectedValue(new Error("boom"))
    await expect(createTargetForCohort(1, TargetSystem.BREVO, "Paid members", null)).rejects.toThrow("boom")
  })

  it("repoints a mapping at another target, carrying both choices the operator made", async () => {
    vi.mocked(switchTarget).mockResolvedValue(answer(switchTarget, {targetId: 4, system: TargetSystem.BREVO, kind: TargetKind.LIST, externalId: "18", label: "Paid members", path: [], folderKnown: true, runs: [], enforced: false}))

    await expect(switchCohortTarget(1, 4, "18", true, false)).resolves.toMatchObject({externalId: "18"})
    expect(switchTarget).toHaveBeenCalledWith({
      path: {id: 1, targetId: 4},
      body: {externalId: "18", deletePrevious: true, reconcileNow: false},
      throwOnError: true,
    })
  })
})

describe("moving targets between folders", () => {
  it("answers with the target where it ended up", async () => {
    vi.mocked(moveCohortTarget).mockResolvedValue(answer(moveCohortTarget, target({folderLabel: "Archive"})))

    await expect(moveTargetToFolder(TargetSystem.BREVO, "17", "Archive")).resolves.toMatchObject({
      externalId: "17",
      folderLabel: "Archive",
      memberCount: null,
      linkedTargetId: null,
      path: [],
    })
    expect(moveCohortTarget).toHaveBeenCalledWith({
      path: {system: TargetSystem.BREVO, externalId: "17"},
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
    vi.mocked(moveCohortTargets).mockResolvedValue(refusal(
      moveCohortTargets,
      {errors: [{code: "UnknownTargetIds", field: "externalIds", message: "Gone", refs: ["17"]}]},
      409,
    ))

    const outcome = await moveTargetsToFolder(TargetSystem.BREVO, ["17"], "Archive")

    expect(outcome.status).toBe("refused")
    expect(outcome).toMatchObject({rejection: {namedRefs: ["17"], requiresReload: true, status: 409}})
  })

  it("reports the ones that moved and the ones the system would not move", async () => {
    vi.mocked(moveCohortTargets).mockResolvedValue(answer(moveCohortTargets, {
        moved: [target({externalId: "17", folderLabel: "Archive", memberCount: 12, linkedTargetId: 4, path: ["Archive"]})],
        failed: [{externalId: "18", label: "Guests", message: "The folder is full."}],
      }))

    const outcome = await moveTargetsToFolder(TargetSystem.BREVO, ["17", "18"], "Archive")

    expect(outcome).toEqual({
      status: "moved",
      result: {
        moved: [{
          system: TargetSystem.BREVO,
          externalId: "17",
          kind: TargetKind.LIST,
          label: "Paid members",
          folderLabel: "Archive",
          memberCount: 12,
          linkedTargetId: 4,
          path: ["Archive"],
        }],
        failed: [{externalId: "18", label: "Guests", message: "The folder is full."}],
      },
    })
  })

  it("reads a move the api answered nothing about as a move that did not happen", async () => {
    vi.mocked(moveCohortTargets).mockResolvedValue(answer(moveCohortTargets, {moved: [], failed: []}))
    await expect(moveTargetsToFolder(TargetSystem.BREVO, ["17"], "Archive")).resolves.toEqual({
      status: "moved",
      result: {moved: [], failed: []},
    })

    // Not a refusal the parser recognises and not an answer either, so neither outcome would
    // be true; a throw is what keeps the page from reporting a move nobody made.
    vi.mocked(moveCohortTargets).mockResolvedValue(refusal(moveCohortTargets, {}, 500))
    await expect(moveTargetsToFolder(TargetSystem.BREVO, ["17"], "Archive")).rejects.toThrow("The move could not be sent.")
  })
})

describe("applyInboundReconcileSelection", () => {
  it("applies only the accounts the operator ticked, under the token they were previewed with", async () => {
    vi.mocked(applyInboundReconcile).mockResolvedValue(answer(applyInboundReconcile, {acceptedCount: 2, skippedCount: 0}))

    await expect(applyInboundReconcileSelection(1, 4, "tok", ["ext-1", "ext-2"])).resolves.toEqual({acceptedCount: 2, skippedCount: 0})
    expect(applyInboundReconcile).toHaveBeenCalledWith({
      path: {id: 1, targetId: 4},
      body: {previewToken: "tok", selectedExternalUserIds: ["ext-1", "ext-2"]},
      throwOnError: true,
    })
  })
})

describe("making and renaming lists on the system", () => {
  it("answers with the new list as the system has it", async () => {
    vi.mocked(createExternalTarget).mockResolvedValue(answer(createExternalTarget, target({externalId: "9", label: "Pub quiz", folderLabel: "Projects"})))

    await expect(createListInSystem(TargetSystem.BREVO, "Pub quiz", "Projects")).resolves.toMatchObject({
      ok: true,
      saved: {externalId: "9", label: "Pub quiz", folderLabel: "Projects", linkedTargetId: null},
    })
    expect(createExternalTarget).toHaveBeenCalledWith({path: {system: TargetSystem.BREVO}, body: {name: "Pub quiz", folder: "Projects"}})
  })

  it("says the system's reason when it refuses", async () => {
    vi.mocked(createExternalTarget).mockResolvedValue(
      refusal(createExternalTarget, {code: "TargetSystemRefused", system: "Brevo", reason: "Failed to create list"}, 502),
    )

    await expect(createListInSystem(TargetSystem.BREVO, "Pub quiz", null)).resolves.toEqual({
      ok: false,
      reason: "Brevo refused it: Failed to create list",
    })
  })

  it("renames a list and answers with its new name", async () => {
    vi.mocked(renameExternalTarget).mockResolvedValue(answer(renameExternalTarget, target({label: "Paid 2026"})))

    await expect(renameTarget(TargetSystem.BREVO, "17", "Paid 2026")).resolves.toMatchObject({ok: true, saved: {label: "Paid 2026"}})
  })

  it("says a list the system does not have needs a reload", async () => {
    vi.mocked(renameExternalTarget).mockResolvedValue(
      refusal(renameExternalTarget, {code: "TargetNotFound", system: "Brevo", externalId: "17"}, 404),
    )

    await expect(renameTarget(TargetSystem.BREVO, "17", "Gone")).resolves.toEqual({
      ok: false,
      reason: "Brevo has no list 17; reload the lists.",
    })
  })

  it("answers every folder after making one", async () => {
    vi.mocked(createTargetFolder).mockResolvedValue(answer(createTargetFolder, ["Members", "Projects"]))

    await expect(createFolderInSystem(TargetSystem.BREVO, "Projects")).resolves.toEqual({ok: true, saved: ["Members", "Projects"]})
  })
})

describe("archiving and deleting lists", () => {
  it("answers with the list in its archive folder", async () => {
    vi.mocked(archiveExternalTarget).mockResolvedValue(answer(archiveExternalTarget, target({folderLabel: "Archive"})))

    await expect(archiveTarget(TargetSystem.BREVO, "17")).resolves.toMatchObject({ok: true, saved: {folderLabel: "Archive"}})
  })

  it("says why a linked list cannot be deleted", async () => {
    vi.mocked(deleteExternalTarget).mockResolvedValue(
      refusal(deleteExternalTarget, {code: "TargetStillLinked", system: "Brevo", externalId: "17"}, 409),
    )

    await expect(deleteTarget(TargetSystem.BREVO, "17", "Paid members")).resolves.toEqual({
      ok: false,
      reason: "A list linked to a cohort is archived, not deleted.",
    })
    expect(deleteExternalTarget).toHaveBeenCalledWith({path: {system: TargetSystem.BREVO, externalId: "17"}, body: {name: "Paid members"}})
  })

  it("says a mistyped name is why a delete was refused", async () => {
    vi.mocked(deleteExternalTarget).mockResolvedValue(refusal(deleteExternalTarget, {code: "TargetNameMismatch", name: "paid"}, 400))

    await expect(deleteTarget(TargetSystem.BREVO, "17", "paid")).resolves.toEqual({
      ok: false,
      reason: "That is not the list's name; type it exactly to delete it.",
    })
  })

  it("answers that the delete went through", async () => {
    vi.mocked(deleteExternalTarget).mockResolvedValue(emptyAnswer(deleteExternalTarget))

    await expect(deleteTarget(TargetSystem.BREVO, "18", "Loose")).resolves.toEqual({ok: true})
  })
})

describe("the folder tidy", () => {
  it("reads the proposal, with a list at the top level as no folder", async () => {
    vi.mocked(previewFolderTidy).mockResolvedValue(answer(previewFolderTidy, {
      moves: [{externalId: "7", label: "Sitecie", to: "Committees"}],
      foldersToCreate: ["Committees"],
    }))

    await expect(fetchTidyPlan(TargetSystem.BREVO)).resolves.toEqual({
      moves: [{externalId: "7", label: "Sitecie", from: null, to: "Committees"}],
      foldersToCreate: ["Committees"],
    })
  })

  it("answers what moved and what was refused", async () => {
    vi.mocked(applyFolderTidy).mockResolvedValue(answer(applyFolderTidy, {
      moved: [target({externalId: "7", folderLabel: "Committees"})],
      failed: [{externalId: "8", label: "Board", message: "Brevo said no"}],
    }))

    const result = await applyTidy(TargetSystem.BREVO, ["7", "8"])

    expect(result.moved.map((t) => t.externalId)).toEqual(["7"])
    expect(result.failed).toEqual([{externalId: "8", label: "Board", message: "Brevo said no"}])
  })
})
