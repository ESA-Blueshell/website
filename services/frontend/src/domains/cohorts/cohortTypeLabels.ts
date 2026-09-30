import {CohortType} from "./adapters/cohorts"

/**
 * What each kind of cohort is called on screen, and the order the kinds read in.
 *
 * The values come from the generated enum rather than restated strings, so a kind added to
 * the api fails the typecheck here instead of quietly falling through to its raw name.
 * Ordered deliberately: the committee cohorts first because they are the ones anybody looks
 * for, then the period-scoped set in the order a period progresses through them.
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

/** The order the groups appear in, which is the order above rather than alphabetical. */
export const COHORT_TYPE_ORDER: CohortType[] = Object.keys(
  COHORT_TYPE_LABELS,
) as CohortType[]

export function cohortTypeLabel(type: CohortType): string {
  return COHORT_TYPE_LABELS[type] ?? String(type)
}
