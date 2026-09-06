/**
 * Cohort domain adapter — the only file in this domain that imports from @/services/api (per
 * frontend ADR-002). Everything else imports from here, and anything outside the domain reads
 * it through `index.ts`.
 */
import {
  applyInboundReconcile,
  createTarget,
  enqueue,
  findCohortSubjectById,
  findCohortSubjects,
  findCohorts,
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
  CohortSubjectDetail as ApiCohortSubjectDetail,
  CohortSubjectMember as ApiCohortSubjectMember,
  CohortSubjectSummary as ApiCohortSubjectSummary,
  CohortSummary as ApiCohortSummary,
  ExternalTarget as ApiExternalTarget,
  InboundReconcileApplyResponse as ApiInboundReconcileApplyResponse,
  InboundReconcilePreview as ApiInboundReconcilePreview,
  TargetDescriptor as ApiTargetDescriptor,
} from "@/services/api"
import {CohortKind, CohortSubjectCategory, CohortSubjectType, TargetSystem} from "@/services/api"
import {parseBulkRejection, type BulkRejection} from "@/utils/bulkRejection"

/*
 * The enums are re-exported rather than re-declared: what a picker offers and what a category
 * route matches are the values the api declares, and a copy in a page drifts from them.
 */
export {CohortKind, CohortSubjectCategory, CohortSubjectType, TargetSystem}

export type ExternalUserConflict = {
  existingUserId: number
  system: string
  existingUserFullName: string | null
}

export async function triggerReconcile(cohortId: number): Promise<number | null> {
  const res = await enqueue({
    body: { jobType: "cohort.reconcile-list", payload: { cohortId } },
    throwOnError: true,
  })
  return res.data?.id ?? null
}

export async function removeExternalMember(
  cohortId: number,
  externalUserId: string,
): Promise<number | null> {
  const res = await enqueue({
    body: { jobType: "cohort.remove-external-member", payload: { cohortId, externalUserId } },
    throwOnError: true,
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
    // `throwOnError` is what makes the conflict below reachable: without it the client
    // resolves with the refusal and the caller reports a link that never happened.
    await linkUser({
      path: { id: subjectId },
      body: { userId, system, externalUserId },
      throwOnError: true,
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

// Mirrors the API's CohortMapping. A field added there has to be added here too.
export type TargetMapping = {
  cohortId: number
  system: TargetSystem
  kind: CohortKind
  externalId: string | null
  label: string
  /** When the target was last confirmed to agree with us, or nothing where it never has. */
  lastReconciledAt: string | null
  /** Where the target sits on its system, outside in. Empty when the system files nothing. */
  path: string[]
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

// Mirrors the API's ExternalTarget. A field added there has to be added here too.
export type ExternalTarget = {
  system: TargetSystem
  externalId: string
  kind: ApiExternalTarget["kind"]
  label: string
  folderLabel: string | null
  memberCount: number | null
  linkedCohortId: number | null
  /** Where the target sits on its system, outside in. Empty when the system files nothing. */
  path: string[]
}

export type InboundReconcilePreview = ApiInboundReconcilePreview
export type InboundReconcileApplyResponse = ApiInboundReconcileApplyResponse

function toTargetMapping(raw: ApiCohortMapping): TargetMapping {
  return {
    cohortId: raw.cohortId,
    system: raw.system,
    kind: raw.kind,
    externalId: raw.externalId ?? null,
    label: raw.label,
    lastReconciledAt: raw.lastReconciledAt ?? null,
    path: raw.path ?? [],
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
    const res = await linkExistingTarget({
      path: { id: subjectId },
      body: { system, externalId },
      throwOnError: true,
    })
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
      throwOnError: true,
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
    throwOnError: true,
  })
  return toTargetMapping(res.data!)
}

/*
 * The four reads below throw rather than answering with nothing.
 *
 * Their callers all hold an error path already, and "no targets" is a real answer a cohort can
 * give — so a read that failed has to be told apart from one that came back empty, or the page
 * states an emptiness nobody confirmed.
 */
export async function fetchTargetDescriptors(): Promise<TargetDescriptor[]> {
  const res = await listCohortTargetSystems({throwOnError: true})
  return (res.data ?? []).map(toTargetDescriptor)
}

export async function fetchTargetOptions(system: TargetSystem): Promise<ExternalTarget[]> {
  const res = await searchCohortTargets({path: {system}, throwOnError: true})
  return (res.data ?? []).map(toExternalTarget)
}

/** Every folder the system has, including the ones holding nothing. */
export async function fetchTargetFolders(system: TargetSystem): Promise<string[]> {
  const res = await listCohortTargetFolders({path: {system}, throwOnError: true})
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
    path: raw.path ?? [],
  }
}

export async function fetchInboundReconcilePreview(
  subjectId: number,
  cohortId: number,
): Promise<InboundReconcilePreview> {
  const res = await previewInboundReconcile({path: {id: subjectId, cohortId}, throwOnError: true})
  return res.data as InboundReconcilePreview
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
    throwOnError: true,
  })
  return res.data!
}

/*
 * The subject reads.
 *
 * A cohort subject arrives as a transport record with half its fields optional; what a page
 * draws is the shape below, with every absence already decided. Unlike the target reads above
 * these answer with nothing rather than throwing, because that is what their pages have always
 * shown for a subject the api would not give.
 */

/** Whether a ledger row agrees with the external system, which is what the Sync column says. */
export type CohortSyncState = "IN_SYNC" | "ONLY_HERE" | "ONLY_EXTERNAL" | "BROKEN"

/**
 * One row of a cohort's ledger: somebody we hold, somebody the target holds, or both. A row the
 * target alone knows carries no user, which is why almost everything here may be missing.
 */
export type CohortMember = {
  cohortMemberId: number
  userId: number | null
  userFullName: string | null
  userEmail: string | null
  isUserDeleted: boolean
  joinedAt: string
  /** What the external system calls this row, where it has a name for it. */
  externalLabel: string | null
  externalUserId: string | null
  /** Which system's ledger the row belongs to, or nothing for a row no target claims. */
  system: TargetSystem | null
  sync: CohortSyncState
}

/** A cohort as its page shows it: what it is, where it syncs, and who is in it. */
export type CohortSubject = {
  id: number
  label: string
  description: string | null
  category: CohortSubjectCategory
  type: CohortSubjectType
  /** The definition in code that produces this cohort, or nothing where none does any more. */
  definitionKey: string | null
  orphaned: boolean
  mappings: TargetMapping[]
  members: CohortMember[]
}

/** A cohort in a listing: enough to put it in a row, not enough to open it. */
export type CohortSubjectSummary = {
  id: number
  label: string
  category: CohortSubjectCategory
  type: CohortSubjectType
  memberCount: number
  mappingCount: number
}

/** One cohort a picker offers, named by where it lives as much as by what it is called. */
export type CohortOption = {
  id: number
  label: string
  system: string
  kind: CohortKind
  memberCount: number
}

/**
 * A row's agreement with its system, decided once here rather than at each column that reads it.
 *
 * Anything the api does not vouch for reads as broken rather than as healthy: a state it did not
 * send, and any state a later api adds, are both rows nobody can stand behind.
 */
function toSyncState(state: ApiCohortSubjectMember["state"]): CohortSyncState {
  switch (state) {
    case "SYNCED":
    case "VERIFIED":
      return "IN_SYNC"
    case "DESIRED":
      return "ONLY_HERE"
    case "STRANGER":
      return "ONLY_EXTERNAL"
    default:
      return "BROKEN"
  }
}

function toCohortMember(raw: ApiCohortSubjectMember): CohortMember {
  return {
    cohortMemberId: raw.cohortMemberId,
    userId: raw.userId ?? null,
    userFullName: raw.userFullName ?? null,
    userEmail: raw.userEmail ?? null,
    isUserDeleted: raw.isUserDeleted,
    joinedAt: raw.joinedAt,
    externalLabel: raw.externalLabel ?? null,
    externalUserId: raw.externalUserId ?? null,
    system: raw.system ?? null,
    sync: toSyncState(raw.state),
  }
}

function toCohortSubject(raw: ApiCohortSubjectDetail): CohortSubject {
  return {
    id: raw.id,
    label: raw.label,
    description: raw.description ?? null,
    category: raw.category,
    type: raw.type,
    definitionKey: raw.definitionKey ?? null,
    orphaned: raw.orphaned,
    mappings: raw.mappings.map(toTargetMapping),
    members: raw.members.map(toCohortMember),
  }
}

function toCohortSubjectSummary(raw: ApiCohortSubjectSummary): CohortSubjectSummary {
  return {
    id: raw.id,
    label: raw.label,
    category: raw.category,
    type: raw.type,
    memberCount: raw.memberCount,
    mappingCount: raw.mappingCount,
  }
}

/** Every cohort the engine holds. An unanswered listing reads as none, as its pages always have. */
export async function fetchCohortSubjects(): Promise<CohortSubjectSummary[]> {
  const res = await findCohortSubjects()
  return (res.data ?? []).map(toCohortSubjectSummary)
}

/** One cohort, or nothing where the api named none. */
export async function fetchCohortSubject(id: number): Promise<CohortSubject | null> {
  const res = await findCohortSubjectById({path: {id}})
  return res.data ? toCohortSubject(res.data) : null
}

/**
 * The cohorts a picker offers, by system and then by name.
 *
 * Sorted here because the order is the same wherever one is picked, and a picker that sorts for
 * itself is a picker that can sort differently from the next one.
 */
export async function fetchCohortOptions(): Promise<CohortOption[]> {
  const res = await findCohorts()
  return (res.data ?? [])
    .map((raw: ApiCohortSummary) => ({
      id: raw.id,
      label: raw.label,
      system: raw.system,
      kind: raw.kind,
      memberCount: raw.memberCount,
    }))
    .sort((left, right) => {
      const bySystem = left.system.localeCompare(right.system)
      return bySystem !== 0 ? bySystem : left.label.localeCompare(right.label)
    })
}

/** A job one of the cohort pages asks for by hand, and the id it was queued under. */
export type CohortJobQueued = {ok: true; jobId: number | null} | {ok: false}

/**
 * Queues one of the cohort engine's jobs.
 *
 * Answers rather than throwing: the pages that press these buttons report a refusal in their own
 * words beside the button, which a thrown error would replace with a network notice.
 */
export async function queueCohortJob(
  jobType: string,
  payload: Record<string, unknown> = {},
): Promise<CohortJobQueued> {
  const res = await enqueue({body: {jobType, payload}})
  if (res.status !== 200 || !res.data) return {ok: false}
  return {ok: true, jobId: res.data.id ?? null}
}
