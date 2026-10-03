import {formatDate} from "@/utils/timestamps"
import {type CohortMember, CohortType, DriftResolutionAction, type ReconcileRun, TargetSystem} from "./adapters/cohorts"
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

/** How a target's page words its drift: a Brevo list's people are pushed, a Discord role is added. */
export type DriftWords = {
  push: string
  remove: string
  link: string
  missing: (cohortLabel: string, since: string) => string
  extra: (cohortLabel: string) => string
  stranger: string
  /** What a row is called whose external id no account here claims. */
  nameless: string
  unreachable: string
  unreachableMark: string
  /** What a link plan says of a stranger no account claims. */
  unclaimed: string
  resolved: Record<DriftResolutionAction, string>
}

const BREVO_WORDS: DriftWords = {
  push: "Push",
  remove: "Remove",
  link: "Link to an account",
  missing: (cohortLabel, since) => `In ${cohortLabel} since ${since}, and not on the list`,
  extra: (cohortLabel) => `On the list, and not in ${cohortLabel}`,
  stranger: "On the list; no account here has this address",
  nameless: "Unknown contact",
  unreachable: "Has no address Brevo can reach",
  unreachableMark: "Unreachable",
  unclaimed: "no account has this address, so it stays as it is",
  resolved: {
    [DriftResolutionAction.PUSH]: "Pushed to the list",
    [DriftResolutionAction.REMOVE]: "Removed from the list",
    [DriftResolutionAction.LINK]: "Linked to an account",
    [DriftResolutionAction.ADOPT]: "Taken in from the list",
    [DriftResolutionAction.ENFORCED_REMOVE]: "Removed by enforcing",
  },
}

const DISCORD_WORDS: DriftWords = {
  push: "Add the role",
  remove: "Remove the role",
  link: "Link to a user",
  missing: (cohortLabel, since) => `In ${cohortLabel} since ${since}, and without the role`,
  extra: (cohortLabel) => `Holds the role, and not in ${cohortLabel}`,
  stranger: "No account here has this Discord linked",
  nameless: "Unknown member",
  unreachable: "No Discord linked, so the role cannot be added",
  unreachableMark: "No Discord linked",
  unclaimed: "no account has this Discord linked, so it stays as it is",
  resolved: {
    [DriftResolutionAction.PUSH]: "Added the role",
    [DriftResolutionAction.REMOVE]: "Removed the role",
    [DriftResolutionAction.LINK]: "Linked to a user",
    [DriftResolutionAction.ADOPT]: "Taken in from the role",
    [DriftResolutionAction.ENFORCED_REMOVE]: "Removed by enforcing",
  },
}

export const driftWords = (system: TargetSystem): DriftWords => (system === TargetSystem.DISCORD ? DISCORD_WORDS : BREVO_WORDS)

/** Why a person drifts, in the cohort's own name and the target's words. */
export function whyOf(member: CohortMember, cohortLabel: string, words: DriftWords = BREVO_WORDS): string {
  if (member.sync === "ONLY_HERE") return member.unreachable ? words.unreachable : words.missing(cohortLabel, formatDate(member.joinedAt))
  if (member.userId != null) return words.extra(cohortLabel)
  return words.stranger
}

/** What taking an extra person in means for the cohort, or nothing where its data cannot take anybody in. */
export const adoptWord = (type: CohortType | null | undefined): string | null => (type === CohortType.PERIOD_PAYERS ? "Record as paid" : null)

export const RESOLUTION_WORDS = BREVO_WORDS.resolved

/** The drift of the last runs as bars, oldest first, each as tall as its drift against the largest. */
export function runBars(runs: ReconcileRun[], count = 8): {height: number; drift: boolean}[] {
  const recent = runs.slice(0, count).reverse()
  const largest = Math.max(1, ...recent.map((run) => run.missing + run.extra))
  return recent.map((run) => {
    const drift = run.missing + run.extra
    return {height: drift === 0 ? 3 : Math.max(6, Math.round((drift / largest) * 21)), drift: drift > 0}
  })
}
