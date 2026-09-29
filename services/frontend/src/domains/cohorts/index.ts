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
export {countLabel, nounFor} from "./cohortSubjectSummaries"
export type {
  CohortMember,
  CohortOption,
  CohortSubject,
  CohortSubjectSummary,
  CohortSyncState,
  DriftResolutionEntry,
  ExternalTarget,
  LinkOutcome,
  LinkProposal,
  ReconcileRun,
  TargetMapping,
} from "./adapters/cohorts"
export {CohortKind, CohortSubjectCategory, CohortSubjectType, DriftResolutionAction, TargetSystem} from "./adapters/cohorts"
export {fetchCohortOptions} from "./adapters/cohorts"
export {fetchCohortSubject} from "./adapters/cohorts"
export {fetchCohortSubjects} from "./adapters/cohorts"
export {linkDriftPeople, proposeDriftLinks, pushDriftPeople, removeDriftPeople} from "./adapters/cohorts"
export {queueCohortJob} from "./adapters/cohorts"
export {triggerReconcile} from "./adapters/cohorts"
export {useDriftResolution} from "./composables/useDriftResolution"
export {useTargetOverview} from "./composables/useTargetOverview"
