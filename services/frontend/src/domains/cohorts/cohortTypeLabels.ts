import {CohortType} from "./adapters/cohorts"

/**
 * What each kind of cohort is called on screen.
 *
 * The values come from the generated enum rather than restated strings, so a kind added to
 * the api fails the typecheck here instead of quietly falling through to its raw name.
 */
export const COHORT_TYPE_LABELS: Record<CohortType, string> = {
  [CohortType.COMMITTEE_MEMBERS]: "Committee members",
  [CohortType.PERIOD_MEMBERS]: "Members in period",
  [CohortType.PERIOD_ACTIVE_MEMBERS]: "Active members in period",
  [CohortType.PERIOD_PAYERS]: "Contribution paid",
  [CohortType.NEWSLETTER_SUBSCRIBERS]: "Newsletter subscribers",
  [CohortType.ACTIVISTS]: "Activists",
  [CohortType.CURRENT_MEMBERS]: "Members",
}

export function cohortTypeLabel(type: CohortType): string {
  return COHORT_TYPE_LABELS[type] ?? String(type)
}
