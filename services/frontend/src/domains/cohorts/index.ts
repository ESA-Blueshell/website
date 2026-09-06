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
  isMember,
  memberName,
  memberSystemLabel,
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
  ExternalTarget,
  TargetMapping,
} from "./adapters/cohorts"
export {CohortKind, CohortSubjectCategory, CohortSubjectType, TargetSystem} from "./adapters/cohorts"
