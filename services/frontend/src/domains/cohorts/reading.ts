/**
 * How a cohort reads on screen: what a row is called, what its sync state says, and what each
 * category is titled.
 *
 * Pure functions over domain records rather than computeds over a page's state, so the rules
 * are worth a test and the two cohort pages cannot title the same category differently.
 */
import {CohortSubjectCategory, type CohortMember} from "./adapters/cohorts"

/**
 * What each system is called. Keyed by the string rather than by the enum: the ledger carries
 * systems the current enum does not name, and an unknown one reads as its own id.
 */
const SYSTEM_LABELS: Record<string, string> = {
  BREVO: "Brevo",
  DISCORD: "Discord",
  GOOGLE_WORKSPACE: "Google Workspace",
}

export const systemLabel = (system: string): string => SYSTEM_LABELS[system] ?? system

const CATEGORY_LABELS: Record<CohortSubjectCategory, string> = {
  [CohortSubjectCategory.COMMITTEES]: "Committees",
  [CohortSubjectCategory.PERIODS]: "Periods",
  [CohortSubjectCategory.MEMBERS]: "Members",
}

export const categoryLabel = (category: CohortSubjectCategory): string => CATEGORY_LABELS[category]

/** The system a row answers to, or the target in the abstract where it belongs to none. */
export const memberSystemLabel = (member: CohortMember): string =>
  member.system == null ? "the target" : systemLabel(member.system)

/**
 * What each state is called on screen. Only the exceptions are chipped: a cohort of eighty-
 * seven healthy rows would otherwise spend all its colour saying nothing.
 */
export const syncLabel = (member: CohortMember): string => {
  switch (member.sync) {
    case "IN_SYNC":
      return "In sync"
    case "ONLY_HERE":
      // "yet", because the sync queue resolves this one on its own.
      return `Not in ${memberSystemLabel(member)} yet`
    case "ONLY_EXTERNAL":
      return `Only in ${memberSystemLabel(member)}`
    default:
      return "Broken"
  }
}

export const syncChipColour = (member: CohortMember): string | undefined => {
  switch (member.sync) {
    case "ONLY_HERE":
      return "info"
    case "ONLY_EXTERNAL":
      return "warning"
    case "BROKEN":
      return "error"
    default:
      return undefined
  }
}

/** A row belonging to somebody here — as opposed to one that only the target knows about. */
export const isMember = (member: CohortMember): boolean => member.sync !== "ONLY_EXTERNAL"

export const memberName = (member: CohortMember): string => {
  if (member.userFullName) return member.userFullName
  if (member.isUserDeleted && member.userId != null) return `Deleted user #${member.userId}`
  if (member.userId != null) return `User #${member.userId}`
  // A stranger nothing local claims: the external system's own label is all there is.
  return member.externalLabel ?? member.externalUserId ?? "Unknown"
}
