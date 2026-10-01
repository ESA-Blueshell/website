/**
 * The cohorts domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001).
 *
 * The wire is not listed — the cohort pages reach the adapter at its own path, which is the one
 * place a call to the api may be written. Re-exported by name rather than with `export *`,
 * because the list of names is the promise.
 */
export {memberName, systemLabel} from "./reading"
export {COHORT_TYPE_LABELS, cohortTypeLabel} from "./cohortTypeLabels"
export type {
  CohortMember,
  TargetOption,
  Cohort,
  CohortSummary,
  CohortSyncState,
  DriftResolutionEntry,
  ExternalTarget,
  LinkOutcome,
  LinkProposal,
  ReconcileRun,
  TargetMapping,
} from "./adapters/cohorts"
export {TargetKind, CohortCategory, CohortType, DriftResolutionAction, TargetSystem} from "./adapters/cohorts"
export {fetchCohortTargets} from "./adapters/cohorts"
export {
  applyTidy,
  archiveTarget,
  createFolderInSystem,
  createListInSystem,
  createMissingLists,
  fetchTargetFolders,
  fetchTidyPlan,
  readListedTarget,
  readTargetOverview,
  renameTarget,
  moveTargetToFolder,
  deleteTarget,
  linkExistingTargetForCohort,
  type LastTidy,
  type ListedTarget,
  type MissingTarget,
  type TidyMove,
  type TidyPlan,
  type TargetOverview,
} from "./adapters/cohorts"
export {RESOLUTION_WORDS, adoptWord, driftRowsOf, inStepOn, isDrift, runBars, whyOf} from "./listPage"
export {ARCHIVE_FOLDER, driftOf, followsOf, groupsOf, lastTidyLine, missingNotice, overviewFacts, type OverviewGroup, type OverviewRow} from "./listOverview"
export {fetchCohort} from "./adapters/cohorts"
export {fetchCohorts} from "./adapters/cohorts"
export {linkDriftPeople, proposeDriftLinks, pushDriftPeople, removeDriftPeople, setTargetEnforced} from "./adapters/cohorts"
export {triggerReconcile} from "./adapters/cohorts"
export {useDriftResolution, type DriftAction} from "./composables/useDriftResolution"
