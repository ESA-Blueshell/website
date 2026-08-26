/**
 * Cohort domain adapter — the only file in this domain that imports
 * from @/services/api (per frontend ADR-002). Everything else imports
 * from this module or from ../types.
 */
import {
  applyInboundReconcile,
  createTarget,
  enqueue,
  linkExistingTarget,
  linkUser,
  listCohortTargetFolders,
  listCohortTargetSystems,
  moveCohortTarget,
  moveCohortTargets,
  previewInboundReconcile,
  searchCohortTargets,
  switchTarget,
} from "@/services/api"
import type {
  CohortMapping as ApiCohortMapping,
  TargetSystem as ApiTargetSystem,
  ExternalTarget as ApiExternalTarget,
  InboundReconcileApplyResponse as ApiInboundReconcileApplyResponse,
  InboundReconcilePreview as ApiInboundReconcilePreview,
  TargetDescriptor as ApiTargetDescriptor,
} from "@/services/api"
import {parseBulkRejection, type BulkRejection} from "@/utils/bulkRejection"

export type TargetSystem = ApiTargetSystem

export type ExternalUserConflict = {
  existingUserId: number
  system: string
  existingUserFullName: string | null
}

export async function triggerReconcile(cohortId: number): Promise<number | null> {
  const res = await enqueue({ body: { jobType: "cohort.reconcile-list", payload: { cohortId } } })
  return res.data?.id ?? null
}

export async function removeExternalMember(
  cohortId: number,
  externalUserId: string,
): Promise<number | null> {
  const res = await enqueue({
    body: { jobType: "cohort.remove-external-member", payload: { cohortId, externalUserId } },
  })
  return res.data?.id ?? null
}

export type LinkUserResult = { type: "ok" } | { type: "conflict"; conflict: ExternalUserConflict }

export async function linkUserToExternal(
  subjectId: number,
  userId: number,
  system: TargetSystem,
  externalUserId: string,
): Promise<LinkUserResult> {
  try {
    await linkUser({
      path: { id: subjectId },
      body: { userId, system, externalUserId },
    })
    return { type: "ok" }
  } catch (err: unknown) {
    const resp = (err as { response?: { status?: number; data?: Record<string, unknown> } })?.response
    if (resp?.status === 409 && resp.data) {
      const d = resp.data
      return {
        type: "conflict",
        conflict: {
          existingUserId: d["existingUserId"] as number,
          system: d["system"] as string,
          existingUserFullName: (d["existingUserFullName"] as string | null) ?? null,
        },
      }
    }
    throw err
  }
}

export type TargetMapping = {
  cohortId: number
  system: string
  externalId: string | null
  label: string
}

export type AddTargetResult = { type: "ok"; mapping: TargetMapping } | { type: "conflict" }

export type TargetCapability = ApiTargetDescriptor["capabilities"][number]

export type TargetDescriptor = {
  system: TargetSystem
  kind: ApiTargetDescriptor["kind"]
  systemLabel: string
  targetLabel: string
  idLabel: string
  folderLabel: string | null
  capabilities: TargetCapability[]
}

export type ExternalTarget = {
  system: TargetSystem
  externalId: string
  kind: ApiExternalTarget["kind"]
  label: string
  folderLabel: string | null
  memberCount: number | null
  linkedCohortId: number | null
}

export type InboundReconcilePreview = ApiInboundReconcilePreview
export type InboundReconcileApplyResponse = ApiInboundReconcileApplyResponse

function toTargetMapping(raw: ApiCohortMapping): TargetMapping {
  return {
    cohortId: raw.cohortId,
    system: raw.system,
    externalId: raw.externalId ?? null,
    label: raw.label,
  }
}

function asConflict(err: unknown): AddTargetResult | null {
  const status = (err as { response?: { status?: number } })?.response?.status
  return status === 409 ? { type: "conflict" } : null
}

/** Maps the subject's per-system cohort to an external target that already exists. */
export async function linkExistingTargetForSubject(
  subjectId: number,
  system: TargetSystem,
  externalId: string,
): Promise<AddTargetResult> {
  try {
    const res = await linkExistingTarget({ path: { id: subjectId }, body: { system, externalId } })
    return { type: "ok", mapping: toTargetMapping(res.data!) }
  } catch (err: unknown) {
    return asConflict(err) ?? Promise.reject(err)
  }
}

/** Creates a fresh external target and maps the subject's per-system cohort to it. */
export async function createTargetForSubject(
  subjectId: number,
  system: TargetSystem,
  label: string,
  folderHint: string | null,
): Promise<AddTargetResult> {
  try {
    const res = await createTarget({
      path: { id: subjectId },
      body: { system, label, folderHint: folderHint ?? undefined },
    })
    return { type: "ok", mapping: toTargetMapping(res.data!) }
  } catch (err: unknown) {
    return asConflict(err) ?? Promise.reject(err)
  }
}

/** Repoints an existing cohort mapping at a different external target. */
export async function switchCohortTarget(
  subjectId: number,
  cohortId: number,
  externalId: string,
  deletePrevious: boolean,
  reconcileNow: boolean,
): Promise<TargetMapping> {
  const res = await switchTarget({
    path: { id: subjectId, cohortId },
    body: { externalId, deletePrevious, reconcileNow },
  })
  return toTargetMapping(res.data!)
}

export async function fetchTargetDescriptors(): Promise<TargetDescriptor[]> {
  const res = await listCohortTargetSystems()
  return (res.data ?? []).map(toTargetDescriptor)
}

export async function fetchTargetOptions(system: TargetSystem): Promise<ExternalTarget[]> {
  const res = await searchCohortTargets({ path: { system } })
  return (res.data ?? []).map(toExternalTarget)
}

/** Every folder the system has, including the ones holding nothing. */
export async function fetchTargetFolders(system: TargetSystem): Promise<string[]> {
  const res = await listCohortTargetFolders({path: {system}})
  return res.data ?? []
}

/** File a target under another folder; answers with where it ended up. */
export async function moveTargetToFolder(
  system: TargetSystem,
  externalId: string,
  folder: string,
): Promise<ExternalTarget> {
  const res = await moveCohortTarget({path: {system, externalId}, body: {folder}, throwOnError: true})
  return toExternalTarget(res.data)
}

/** One target an external system would not move, and what it said about it. */
export type FailedTargetMove = {
  externalId: string
  label: string
  message: string
}

export type BulkTargetMoveResult = {
  moved: ExternalTarget[]
  failed: FailedTargetMove[]
}

/**
 * What came back from a bulk move: either the api took the selection, or it refused the whole
 * of it. The two are different enough to the operator — one lists what happened, the other why
 * nothing did — that they are separate outcomes rather than a result with an error beside it.
 */
export type BulkTargetMoveOutcome =
  | {status: "moved"; result: BulkTargetMoveResult}
  | {status: "refused"; rejection: BulkRejection}

/**
 * File several targets under one folder.
 *
 * A `moved` outcome may still name failures: the selection was valid, but past that point the
 * moves are separate calls to a system that cannot roll them back.
 */
export async function moveTargetsToFolder(
  system: TargetSystem,
  externalIds: string[],
  folder: string,
): Promise<BulkTargetMoveOutcome> {
  const res = await moveCohortTargets({path: {system}, body: {externalIds, folder}})
  const refused = parseBulkRejection(res)
  if (refused) return {status: "refused", rejection: refused}
  if (res.error || !res.data) throw new Error("The move could not be sent.")
  return {
    status: "moved",
    result: {
      moved: (res.data.moved ?? []).map(toExternalTarget),
      failed: (res.data.failed ?? []).map((row) => ({
        externalId: row.externalId,
        label: row.label,
        message: row.message,
      })),
    },
  }
}

function toTargetDescriptor(raw: ApiTargetDescriptor): TargetDescriptor {
  return {
    system: raw.system,
    kind: raw.kind,
    systemLabel: raw.systemLabel,
    targetLabel: raw.targetLabel,
    idLabel: raw.idLabel,
    folderLabel: raw.folderLabel ?? null,
    capabilities: [...raw.capabilities],
  }
}

function toExternalTarget(raw: ApiExternalTarget): ExternalTarget {
  return {
    system: raw.system,
    externalId: raw.externalId,
    kind: raw.kind,
    label: raw.label,
    folderLabel: raw.folderLabel ?? null,
    memberCount: raw.memberCount ?? null,
    linkedCohortId: raw.linkedCohortId ?? null,
  }
}

export async function fetchInboundReconcilePreview(
  subjectId: number,
  cohortId: number,
): Promise<InboundReconcilePreview> {
  const res = await previewInboundReconcile({ path: { id: subjectId, cohortId } })
  return res.data! as InboundReconcilePreview
}

export async function applyInboundReconcileSelection(
  subjectId: number,
  cohortId: number,
  previewToken: string,
  selectedExternalUserIds: string[],
): Promise<InboundReconcileApplyResponse> {
  const res = await applyInboundReconcile({
    path: { id: subjectId, cohortId },
    body: { previewToken, selectedExternalUserIds },
  })
  return res.data!
}
