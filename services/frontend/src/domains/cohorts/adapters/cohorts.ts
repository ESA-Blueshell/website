/**
 * Cohort domain adapter — the only file in this domain that imports from @/services/api (per
 * frontend ADR-002). Everything else imports from here, and anything outside the domain reads
 * it through `index.ts`.
 */
import {
  applyFolderTidy,
  applyInboundReconcile,
  archiveExternalTarget,
  createExternalTarget,
  createMissingTargets,
  createTargetFolder,
  type FolderMerge,
  type FolderState,
  listTargetFolderStates,
  mergeTargetFolders,
  removeTargetFolder,
  deleteExternalTarget,
  enforceTarget,
  findCohortById,
  findCohorts,
  findListedTarget,
  findTargetOverview,
  listTargetOptions,
  linkExistingTarget,
  linkDrift,
  listCohortTargetFolders,
  moveCohortTarget,
  previewFolderTidy,
  previewInboundReconcile,
  reconcileTarget,
  proposeLinks,
  pushDrift,
  removeDrift,
  renameExternalTarget,
} from "@/services/api"
import type {
  CohortTarget as ApiCohortTarget,
  CohortDetail as ApiCohortDetail,
  CohortMember as ApiCohortMember,
  CohortSummary as ApiCohortSummary,
  TargetOption as ApiTargetOption,
  ExternalTarget as ApiExternalTarget,
  InboundReconcileApplyResponse as ApiInboundReconcileApplyResponse,
  InboundReconcilePreview as ApiInboundReconcilePreview,
  ListedTarget,
  MissingTarget,
  SummaryTarget,
  TargetOverviewResult,
} from "@/services/api"
import {TargetKind, CohortCategory, CohortType, DriftResolutionAction, JobTrigger, TargetSystem} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {accepted, refusable} from "@/domains/cohorts/refusals"

/*
 * The enums are re-exported rather than re-declared: what a picker offers and what a category
 * route matches are the values the api declares, and a copy in a page drifts from them.
 */
export {TargetKind, CohortCategory, CohortType, DriftResolutionAction, JobTrigger, TargetSystem}
export type {SummaryTarget}

/** Queue a reconcile of one of the cohort's targets, started by hand. */
export async function triggerReconcile(cohortId: number, targetId: number): Promise<{ok: true} | Refused> {
  return accepted(reconcileTarget({path: {id: cohortId, targetId}}), "The reconcile could not be queued.")
}

/** A theirs-only contact, and the account holding the address the target calls them by. */
export type LinkProposal = {externalUserId: string; label: string | null; userId: number | null; userFullName: string | null}

/** How many contacts were linked, and those another account already holds. */
export type LinkOutcome = {linked: number; conflicts: {externalUserId: string; existingUserId: number}[]}

/** Switch whether each reconcile removes the target's theirs-only people. Only an admin may. */
export async function setTargetEnforced(cohortId: number, targetId: number, enforced: boolean): Promise<{ok: true} | Refused> {
  return accepted(enforceTarget({path: {id: cohortId, targetId}, body: {enforced}}), "The target could not be switched.")
}

/** Push each ours-only person to the target; answers how many were still ours only. */
export async function pushDriftPeople(cohortId: number, targetId: number, userIds: number[]): Promise<Saved<number> | Refused> {
  const answer = await refusable(pushDrift({path: {id: cohortId, targetId}, body: {userIds}}), "They could not be pushed.")
  return answer.ok ? {ok: true, saved: answer.saved.resolved} : answer
}

/** Remove each theirs-only person from the target; answers how many were still theirs only. */
export async function removeDriftPeople(
  cohortId: number,
  targetId: number,
  externalUserIds: string[],
): Promise<Saved<number> | Refused> {
  const answer = await refusable(removeDrift({path: {id: cohortId, targetId}, body: {externalUserIds}}), "They could not be removed.")
  return answer.ok ? {ok: true, saved: answer.saved.resolved} : answer
}

/** For each theirs-only contact, the account with its address, where there is one. Changes nothing. */
export async function proposeDriftLinks(
  cohortId: number,
  targetId: number,
  externalUserIds: string[],
): Promise<Saved<LinkProposal[]> | Refused> {
  const answer = await refusable(proposeLinks({path: {id: cohortId, targetId}, body: {externalUserIds}}), "The links could not be found.")
  if (!answer.ok) return answer
  return {
    ok: true,
    saved: answer.saved.map((p) => ({
      externalUserId: p.externalUserId,
      label: p.label ?? null,
      userId: p.userId ?? null,
      userFullName: p.userFullName ?? null,
    })),
  }
}

/** Link each theirs-only contact to the account chosen for it. */
export async function linkDriftPeople(
  cohortId: number,
  targetId: number,
  links: {externalUserId: string; userId: number}[],
): Promise<Saved<LinkOutcome> | Refused> {
  return refusable(linkDrift({path: {id: cohortId, targetId}, body: {links}}), "They could not be linked.")
}

// Mirrors the API's CohortTarget. A field added there has to be added here too.
export type TargetMapping = {
  targetId: number
  system: TargetSystem
  kind: TargetKind
  externalId: string | null
  label: string
  /** When the target was last confirmed to agree with us, or nothing where it never has. */
  lastReconciledAt: string | null
  /** Where the target sits on its system, outside in. Empty when the system files nothing. */
  path: string[]
  /** False when the system could not say which folder the target is in. */
  folderKnown: boolean
  /** Recent reconciles, newest first; the first is the target's current drift. */
  runs: ReconcileRun[]
  /** Whether each reconcile removes the target's theirs-only people. */
  enforced: boolean
}

/** One reconcile of a target: how many were in step, missing from it and extra on it. */
export type ReconcileRun = {
  startedAt: string
  trigger: string | null
  inStep: number
  missing: number
  extra: number
}

export type AddTargetResult = { type: "ok"; mapping: TargetMapping } | { type: "conflict" }

// Mirrors the API's ExternalTarget. A field added there has to be added here too.
export type ExternalTarget = {
  system: TargetSystem
  externalId: string
  kind: ApiExternalTarget["kind"]
  label: string
  folderLabel: string | null
  memberCount: number | null
  linkedTargetId: number | null
  /** Where the target sits on its system, outside in. Empty when the system files nothing. */
  path: string[]
}

export type InboundReconcilePreview = ApiInboundReconcilePreview
export type InboundReconcileApplyResponse = ApiInboundReconcileApplyResponse

function toTargetMapping(raw: ApiCohortTarget): TargetMapping {
  return {
    targetId: raw.targetId,
    system: raw.system,
    kind: raw.kind,
    externalId: raw.externalId ?? null,
    label: raw.label,
    lastReconciledAt: raw.lastReconciledAt ?? null,
    path: raw.path ?? [],
    folderKnown: raw.folderKnown,
    runs: raw.runs.map((run) => ({
      startedAt: run.startedAt,
      trigger: run.trigger ?? null,
      inStep: run.inSync,
      missing: run.oursOnly,
      extra: run.theirsOnly,
    })),
    enforced: raw.enforced,
  }
}

function asConflict(err: unknown): AddTargetResult | null {
  const status = (err as { response?: { status?: number } })?.response?.status
  return status === 409 ? { type: "conflict" } : null
}

/** Maps the cohort's per-system cohort to an external target that already exists. */
export async function linkExistingTargetForCohort(
  cohortId: number,
  system: TargetSystem,
  externalId: string,
): Promise<AddTargetResult> {
  try {
    const res = await linkExistingTarget({
      path: { id: cohortId },
      body: { system, externalId },
      throwOnError: true,
    })
    return { type: "ok", mapping: toTargetMapping(res.data!) }
  } catch (err: unknown) {
    return asConflict(err) ?? Promise.reject(err)
  }
}

/*
 * The four reads below throw rather than answering with nothing.
 *
 * Their callers all hold an error path already, and "no targets" is a real answer a cohort can
 * give — so a read that failed has to be told apart from one that came back empty, or the page
 * states an emptiness nobody confirmed.
 */
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

/** Make a list linked to no cohort, in [folder] or at the top level; answers it as the system has it. */
export async function createListInSystem(
  system: TargetSystem,
  name: string,
  folder: string | null,
): Promise<Saved<ExternalTarget> | Refused> {
  const answer = await refusable(createExternalTarget({path: {system}, body: {name, folder}}), "The list could not be made.")
  return answer.ok ? {ok: true, saved: toExternalTarget(answer.saved)} : answer
}

/** Give a list, linked or not, another name on its system. */
export async function renameTarget(
  system: TargetSystem,
  externalId: string,
  name: string,
): Promise<Saved<ExternalTarget> | Refused> {
  const answer = await refusable(renameExternalTarget({path: {system, externalId}, body: {name}}), "The list could not be renamed.")
  return answer.ok ? {ok: true, saved: toExternalTarget(answer.saved)} : answer
}

/** Make a folder, or find the one already called that; answers every folder. */
export async function createFolderInSystem(system: TargetSystem, name: string): Promise<Saved<string[]> | Refused> {
  return refusable(createTargetFolder({path: {system}, body: {name}}), "The folder could not be made.")
}

/** File a list in the archive folder; it keeps its contacts and its link. */
export async function archiveTarget(system: TargetSystem, externalId: string): Promise<Saved<ExternalTarget> | Refused> {
  const answer = await refusable(archiveExternalTarget({path: {system, externalId}}), "The list could not be archived.")
  return answer.ok ? {ok: true, saved: toExternalTarget(answer.saved)} : answer
}

/** Delete a list linked to no cohort for good, confirmed by its name typed exactly. */
export async function deleteTarget(system: TargetSystem, externalId: string, name: string): Promise<{ok: true} | Refused> {
  return accepted(deleteExternalTarget({path: {system, externalId}, body: {name}}), "The list could not be deleted.")
}

/** One linked list the tidy would move into its cohort type's folder. */
export type TidyMove = {externalId: string; label: string; from: string | null; to: string}

/** The newest applied tidy: when, by whom, and what it moved. */
export type LastTidy = {appliedAt: string; appliedByName: string | null; moved: number; failed: number}

export type TidyPlan = {moves: TidyMove[]; foldersToCreate: string[]; lastApplied: LastTidy | null}

/** What the folder tidy would do: its moves, the folders it would make and the last one applied. Changes nothing. */
export async function fetchTidyPlan(system: TargetSystem): Promise<TidyPlan> {
  const res = await previewFolderTidy({path: {system}, throwOnError: true})
  const last = res.data.lastApplied
  return {
    moves: (res.data.moves ?? []).map((m) => ({externalId: m.externalId, label: m.label, from: m.from ?? null, to: m.to})),
    foldersToCreate: res.data.foldersToCreate ?? [],
    lastApplied: last ? {appliedAt: last.appliedAt, appliedByName: last.appliedByName ?? null, moved: last.moved, failed: last.failed} : null,
  }
}

/** Apply the tidy to the lists picked; answers what moved and what the system refused. */
export async function applyTidy(system: TargetSystem, externalIds: string[]): Promise<BulkTargetMoveResult> {
  const res = await applyFolderTidy({path: {system}, body: {externalIds}, throwOnError: true})
  return {
    moved: (res.data.moved ?? []).map(toExternalTarget),
    failed: (res.data.failed ?? []).map((row) => ({externalId: row.externalId, label: row.label, message: row.message})),
  }
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

function toExternalTarget(raw: ApiExternalTarget): ExternalTarget {
  return {
    system: raw.system,
    externalId: raw.externalId,
    kind: raw.kind,
    label: raw.label,
    folderLabel: raw.folderLabel ?? null,
    memberCount: raw.memberCount ?? null,
    linkedTargetId: raw.linkedTargetId ?? null,
    path: raw.path ?? [],
  }
}

async function fetchInboundReconcilePreview(
  cohortId: number,
  targetId: number,
): Promise<InboundReconcilePreview> {
  const res = await previewInboundReconcile({path: {id: cohortId, targetId}, throwOnError: true})
  return res.data as InboundReconcilePreview
}

async function applyInboundReconcileSelection(
  cohortId: number,
  targetId: number,
  previewToken: string,
  selectedExternalUserIds: string[],
): Promise<InboundReconcileApplyResponse> {
  const res = await applyInboundReconcile({
    path: { id: cohortId, targetId },
    body: { previewToken, selectedExternalUserIds },
    throwOnError: true,
  })
  return res.data!
}

/*
 * The cohort reads.
 *
 * A cohort cohort arrives as a transport record with half its fields optional; what a page
 * draws is the shape below, with every absence already decided. Unlike the target reads above
 * these answer with nothing rather than throwing, because that is what their pages have always
 * shown for a cohort the api would not give.
 */

/** Whether a ledger row agrees with the external system, which is what the Sync column says. */
export type CohortSyncState = "IN_SYNC" | "ONLY_HERE" | "ONLY_EXTERNAL" | "BROKEN"

/**
 * One row of a cohort's ledger: somebody we hold, somebody the target holds, or both. A row the
 * target alone knows carries no user, which is why almost everything here may be missing.
 */
export type CohortMember = {
  targetMemberId: number
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
  /** No account on a system that cannot make one, so no push reaches them. */
  unreachable: boolean
}

/** A cohort as its page shows it: what it is, where it syncs, and who is in it. */
export type Cohort = {
  id: number
  label: string
  description: string | null
  category: CohortCategory
  type: CohortType
  /** The definition in code that produces this cohort, or nothing where none does any more. */
  definitionKey: string | null
  orphaned: boolean
  mappings: TargetMapping[]
  members: CohortMember[]
  /** The latest drift resolutions across the cohort's targets, newest first. */
  resolutions: DriftResolutionEntry[]
}

/** One person's drift on a target, resolved, and by whom. */
export type DriftResolutionEntry = {
  system: TargetSystem
  action: DriftResolutionAction
  personName: string | null
  /** The person's account, where they have one. */
  userId: number | null
  /** Null when the api resolved it on its own behalf. */
  resolvedByName: string | null
  /** The account that resolved it, where it still exists. */
  resolvedById: number | null
  resolvedAt: string
}

/** A cohort in a listing: enough to put it in a row, not enough to open it. */
export type CohortSummary = {
  id: number
  label: string
  category: CohortCategory
  type: CohortType
  memberCount: number
  mappingCount: number
  /** The definition in code that fills it, such as `COMMITTEE_MEMBERS:7`. */
  definitionKey: string | null
  targets: SummaryTarget[]
}

/** One cohort a picker offers, named by where it lives as much as by what it is called. */
export type TargetOption = {
  id: number
  label: string
  system: string
  kind: TargetKind
  memberCount: number
}

/**
 * A row's agreement with its system, decided once here rather than at each column that reads it.
 *
 * Anything the api does not vouch for reads as broken rather than as healthy: a state it did not
 * send, and any state a later api adds, are both rows nobody can stand behind.
 */
function toSyncState(state: ApiCohortMember["state"]): CohortSyncState {
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

function toCohortMember(raw: ApiCohortMember): CohortMember {
  return {
    targetMemberId: raw.targetMemberId,
    userId: raw.userId ?? null,
    userFullName: raw.userFullName ?? null,
    userEmail: raw.userEmail ?? null,
    isUserDeleted: raw.isUserDeleted,
    joinedAt: raw.joinedAt,
    externalLabel: raw.externalLabel ?? null,
    externalUserId: raw.externalUserId ?? null,
    system: raw.system ?? null,
    sync: toSyncState(raw.state),
    unreachable: raw.unreachable ?? false,
  }
}

function toCohort(raw: ApiCohortDetail): Cohort {
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
    resolutions: raw.resolutions.map((r) => ({
      system: r.system,
      action: r.action,
      personName: r.personName ?? null,
      userId: r.userId ?? null,
      resolvedByName: r.resolvedByName ?? null,
      resolvedById: r.resolvedById ?? null,
      resolvedAt: r.resolvedAt,
    })),
  }
}

function toCohortSummary(raw: ApiCohortSummary): CohortSummary {
  return {
    id: raw.id,
    label: raw.label,
    category: raw.category,
    type: raw.type,
    memberCount: raw.memberCount,
    mappingCount: raw.mappingCount,
    definitionKey: raw.definitionKey ?? null,
    targets: raw.targets ?? [],
  }
}

/** Every cohort the engine holds. An unanswered listing reads as none, as its pages always have. */
export async function fetchCohorts(): Promise<CohortSummary[]> {
  const res = await findCohorts()
  return (res.data ?? []).map(toCohortSummary)
}

/** One cohort, or nothing where the api named none. */
export async function fetchCohort(id: number): Promise<Cohort | null> {
  const res = await findCohortById({path: {id}})
  return res.data ? toCohort(res.data) : null
}

/**
 * The cohorts a picker offers, by system and then by name.
 *
 * Sorted here because the order is the same wherever one is picked, and a picker that sorts for
 * itself is a picker that can sort differently from the next one.
 */
export async function fetchCohortTargets(): Promise<TargetOption[]> {
  const res = await listTargetOptions()
  return (res.data ?? [])
    .map((raw: ApiTargetOption) => ({
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

export type {ListedTarget, MissingTarget}
export type TargetOverview = TargetOverviewResult

/** Every list on the system with the cohort it follows and its drift, and the missing ones; nothing where it could not be read. */
export const readTargetOverview = (system: TargetSystem): Promise<TargetOverview | null> => readOr(findTargetOverview({path: {system}}), null)

/** Queues a create for each missing list named, or every one when none are; answers how many. */
export async function createMissingLists(system: TargetSystem, targetIds: number[]): Promise<Saved<number> | Refused> {
  const answer = await refusable(createMissingTargets({path: {system}, body: {targetIds}}), "The lists could not be created.")
  return answer.ok ? {ok: true, saved: answer.saved.queued} : answer
}

/** One list on the system with the cohort it follows; nothing where the system does not have it. */
export const readListedTarget = (system: TargetSystem, externalId: string): Promise<ListedTarget | null> =>
  readOr(findListedTarget({path: {system, externalId}}), null)

/**
 * Takes the contacts named into the cohort where its data can, which for a paid cohort is recording
 * their contribution; answers how many are queued. Those the preview cannot take in are left alone.
 */
export async function adoptPeople(cohortId: number, targetId: number, externalUserIds: string[]): Promise<Saved<number> | Refused> {
  try {
    const preview = await fetchInboundReconcilePreview(cohortId, targetId)
    const wanted = new Set(externalUserIds)
    const picked = preview.matched.filter((row) => row.writable && wanted.has(row.externalUserId)).map((row) => row.externalUserId)
    if (!preview.writerSupported || picked.length === 0) return {ok: false, reason: "None of them can be taken in from Brevo."}
    const applied = await applyInboundReconcileSelection(cohortId, targetId, preview.previewToken, picked)
    return {ok: true, saved: applied.acceptedCount}
  } catch {
    return {ok: false, reason: "They could not be taken in."}
  }
}

export type {FolderMerge, FolderState}

/** Every folder with how many lists it holds, two of one name apart; nothing where the system cannot be read. */
export const readFolderStates = (system: TargetSystem): Promise<FolderState[]> =>
  readOr(listTargetFolderStates({path: {system}}), [])

/** Files everything under the oldest of the folders sharing a name, and removes the emptied others. */
export const mergeFolders = (system: TargetSystem): Promise<Saved<FolderMerge> | Refused> =>
  refusable(mergeTargetFolders({path: {system}}), "The folders could not be merged.")

/** Removes a folder that holds nothing. */
export const removeFolder = (system: TargetSystem, id: string): Promise<{ok: true} | Refused> =>
  accepted(removeTargetFolder({path: {system, folderId: id}}), "The folder could not be removed.")
