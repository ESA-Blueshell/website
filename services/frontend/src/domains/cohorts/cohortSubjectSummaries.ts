import type {CohortSubjectDetail} from "@/services/api"

/**
 * What each section of a cohort's page says it holds.
 *
 * These used to be spelled into the template, which is how `Members (12)` ended up with the
 * number inside its own heading while the section beside it had none. Pure functions over the
 * arrays rather than computeds over the subject, so they are worth a test and read the same
 * whether the subject has loaded or not.
 */

/** `1 member`, `2 members` — every noun counted here is regular. */
export function countLabel(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`
}

/** The line under a subject's name: how many people, in how many places. */
export function subjectCounts(subject: CohortSubjectDetail): string {
  return `${countLabel(subject.members.length, "member")} · ${countLabel(subject.mappings.length, "sync target")}`
}

/** Rules are worth counting by how many actually apply — a disabled rule explains nothing. */
export function rulesSummary(rules: CohortSubjectDetail["rules"]): string {
  return `${countLabel(rules.length, "rule")} · ${rules.filter((rule) => rule.enabled).length} enabled`
}

export function targetsSummary(mappings: CohortSubjectDetail["mappings"]): string {
  return countLabel(mappings.length, "sync target")
}

/**
 * Deleted members are named only when there are any: they are kept for historical stats, so a
 * trailing `· 0 deleted` would be on nearly every cohort and mean nothing on any of them.
 */
export function membersSummary(members: CohortSubjectDetail["members"]): string {
  const deleted = members.filter((member) => member.isUserDeleted).length
  const counted = countLabel(members.length, "member")
  return deleted === 0 ? counted : `${counted} · ${deleted} deleted`
}
