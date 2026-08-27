import {CohortSubjectType} from "@/services/api"

/**
 * What each kind of cohort is called on screen, and the order the kinds read in.
 *
 * The values come from the generated enum rather than restated strings, so a kind added to
 * the api fails the typecheck here instead of quietly falling through to its raw name.
 * Ordered deliberately: the committee cohorts first because they are the ones anybody looks
 * for, then the period-scoped set in the order a period progresses through them.
 */
export const COHORT_TYPE_LABELS: Record<CohortSubjectType, string> = {
  [CohortSubjectType.COMMITTEE_MEMBERS]: "Committee members",
  [CohortSubjectType.PERIOD_MEMBERS]: "Members in period",
  [CohortSubjectType.PERIOD_ACTIVE_MEMBERS]: "Active members in period",
  [CohortSubjectType.PERIOD_PAYERS]: "Contribution paid",
  [CohortSubjectType.NEWSLETTER_SUBSCRIBERS]: "Newsletter subscribers",
}

/** The order the groups appear in, which is the order above rather than alphabetical. */
export const COHORT_TYPE_ORDER: CohortSubjectType[] = Object.keys(
  COHORT_TYPE_LABELS,
) as CohortSubjectType[]

export function cohortTypeLabel(type: CohortSubjectType): string {
  return COHORT_TYPE_LABELS[type] ?? String(type)
}
