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
export {countLabel, nounFor} from "./cohortSummaries"
export type {
  CohortMember,
  TargetOption,
  Cohort,
  CohortSummary,
  CohortSyncState,
  ExternalTarget,
  TargetMapping,
} from "./adapters/cohorts"
export {TargetKind, CohortCategory, CohortType, TargetSystem} from "./adapters/cohorts"
export {type ExternalUserConflict} from "./adapters/cohorts"
export {fetchCohortTargets} from "./adapters/cohorts"
export {fetchCohort} from "./adapters/cohorts"
export {fetchCohorts, readCohorts, type SummaryTarget} from "./adapters/cohorts"
export {linkUserToExternal} from "./adapters/cohorts"
export {queueCohortJob} from "./adapters/cohorts"
export {removeExternalMember} from "./adapters/cohorts"
export {triggerReconcile} from "./adapters/cohorts"
export {useTargetOverview} from "./composables/useTargetOverview"
