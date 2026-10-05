/** How a cohort reads on screen: what a system and a ledger row are called. */
import type {CohortMember, SummaryTarget} from "./adapters/cohorts"

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

export const memberName = (member: CohortMember): string => {
  if (member.userFullName) return member.userFullName
  if (member.isUserDeleted && member.userId != null) return `Deleted user #${member.userId}`
  if (member.userId != null) return `User #${member.userId}`
  // A stranger nothing local claims: the external system's own label is all there is.
  return member.externalLabel ?? member.externalUserId ?? "Unknown"
}

/** The name of the role or list a cohort has on [system], or nothing where it has none: what a list orders by. */
export const targetLabel = (targets: SummaryTarget[], system: string): string | null =>
  targets.find((one) => one.system === system && one.made)?.label ?? null
