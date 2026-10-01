import {formatDate} from "@/utils/timestamps"
import {type CohortMember, CohortType, DriftResolutionAction, type ReconcileRun, type TargetSystem} from "./adapters/cohorts"
import {memberName} from "./reading"

/** A person on one side only: missing from the list, or extra on it. */
export const isDrift = (member: CohortMember): boolean => member.sync === "ONLY_HERE" || member.sync === "ONLY_EXTERNAL"

/** The drift on one system's list, by name, narrowed by a search on name or address. */
export function driftRowsOf(members: CohortMember[], system: TargetSystem, search = ""): CohortMember[] {
  const needle = search.trim().toLowerCase()
  return members
    .filter((member) => member.system === system && isDrift(member))
    .filter((member) => needle === "" || [memberName(member), member.userEmail, member.externalLabel].some((value) => value?.toLowerCase().includes(needle)))
    .sort((a, b) => memberName(a).localeCompare(memberName(b)))
}

/** How many people on one system's list are where they should be. */
export const inStepOn = (members: CohortMember[], system: TargetSystem): number =>
  members.filter((member) => member.system === system && member.sync === "IN_SYNC").length

/** Why a person drifts, in the cohort's own name. */
export function whyOf(member: CohortMember, cohortLabel: string): string {
  if (member.sync === "ONLY_HERE") return `In ${cohortLabel} since ${formatDate(member.joinedAt)}, and not on the list`
  if (member.userId != null) return `On the list, and not in ${cohortLabel}`
  return "On the list; no account here has this address"
}

/** What taking an extra person in means for the cohort, or nothing where its data cannot take anybody in. */
export const adoptWord = (type: CohortType | null | undefined): string | null => (type === CohortType.PERIOD_PAYERS ? "Record as paid" : null)

export const RESOLUTION_WORDS: Record<DriftResolutionAction, string> = {
  [DriftResolutionAction.PUSH]: "Pushed to the list",
  [DriftResolutionAction.REMOVE]: "Removed from the list",
  [DriftResolutionAction.LINK]: "Linked to an account",
  [DriftResolutionAction.ADOPT]: "Taken in from the list",
  [DriftResolutionAction.ENFORCED_REMOVE]: "Removed by enforcing",
}

/** The drift of the last runs as bars, oldest first, each as tall as its drift against the largest. */
export function runBars(runs: ReconcileRun[], count = 8): {height: number; drift: boolean}[] {
  const recent = runs.slice(0, count).reverse()
  const largest = Math.max(1, ...recent.map((run) => run.missing + run.extra))
  return recent.map((run) => {
    const drift = run.missing + run.extra
    return {height: drift === 0 ? 3 : Math.max(6, Math.round((drift / largest) * 21)), drift: drift > 0}
  })
}
