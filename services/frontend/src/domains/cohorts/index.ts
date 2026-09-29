/**
 * The cohorts domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001).
 *
 * The wire is not listed — the cohort pages reach the adapter at its own path, which is the one
 * place a call to the api may be written. Re-exported by name rather than with `export *`,
 * because the list of names is the promise.
 */
export {
  categoryLabel,
  driftLabel,
  earlierDrift,
  isMember,
  memberName,
  memberSystemLabel,
  runStartedBy,
  syncChipColour,
  syncLabel,
  systemLabel,
} from "./reading"
export {COHORT_TYPE_LABELS, COHORT_TYPE_ORDER, cohortTypeLabel} from "./cohortTypeLabels"
export {countLabel, nounFor} from "./cohortSummaries"
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
export {fetchCohort} from "./adapters/cohorts"
export {fetchCohorts} from "./adapters/cohorts"
export {linkDriftPeople, proposeDriftLinks, pushDriftPeople, removeDriftPeople, setTargetEnforced} from "./adapters/cohorts"
export {queueCohortJob} from "./adapters/cohorts"
export {triggerReconcile} from "./adapters/cohorts"
export {useDriftResolution} from "./composables/useDriftResolution"
export {useTargetOverview} from "./composables/useTargetOverview"
