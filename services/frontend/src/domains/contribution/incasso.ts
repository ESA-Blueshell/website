import {DateTime} from "luxon"
import {type IncassoCandidate, IncassoLeftOut} from "@/services/api"

/** Why a member on incasso is left out of a run, as the first step says it. */
export const leftOutLabels: Record<IncassoLeftOut, string> = {
  [IncassoLeftOut.NO_BANK_DETAILS]: "No bank details recorded",
  [IncassoLeftOut.ALREADY_PAID]: "Already paid",
  [IncassoLeftOut.OWES_NOTHING]: "Owes nothing",
  [IncassoLeftOut.NO_EMAIL]: "No email address",
  [IncassoLeftOut.DELETED]: "Account deleted",
}

/** What a board member does about a member left out, where there is something to do. */
export const leftOutHelp: Partial<Record<IncassoLeftOut, string>> = {
  [IncassoLeftOut.NO_BANK_DETAILS]: "Ask them to add their bank details on their account page, or record their paper mandate",
}

/** An account shown only by its last four, the way ING's own pages mask one. */
export const maskedIban = (lastFour: string | null | undefined): string =>
  (lastFour ? `NL•• •••• •••• ••${lastFour.slice(0, 2)} ${lastFour.slice(2)}` : "None recorded")

/** The members whose name goes to ING spelled differently, such as Zoë as Zoe. */
export const renamedForIng = (chosen: Pick<IncassoCandidate, "name" | "ingName">[]): Pick<IncassoCandidate, "name" | "ingName">[] =>
  chosen.filter((one) => one.name !== one.ingName)

/** What the bank statement says unless the board writes something else, such as Contributie 2026-2027 ESA Blueshell. */
export const defaultStatementText = (startDate: string, endDate: string): string =>
  `Contributie ${startDate.slice(0, 4)}-${endDate.slice(0, 4)} ESA Blueshell`

/** Left-out members grouped under their reason, in the order the reasons are decided. */
export function leftOutGroups(candidates: IncassoCandidate[]): {reason: IncassoLeftOut; names: string[]}[] {
  return (Object.values(IncassoLeftOut) as IncassoLeftOut[])
    .map((reason) => ({reason, names: candidates.filter((one) => one.leftOut === reason).map((one) => one.name)}))
    .filter((group) => group.names.length > 0)
}

/** A day as a person reads it, such as 1 Nov 2026. */
export const dayName = (iso: string | null | undefined): string => {
  const day = iso ? DateTime.fromISO(iso) : null
  return day?.isValid ? day.toFormat("d LLL yyyy") : "—"
}

/** What a run's file for ING is called, such as incassobatch-2026-11-01.xlsx, or with its part when there are several. */
export const incassoFileName = (collectionDate: string, part: number, parts: number): string =>
  `incassobatch-${collectionDate}${parts > 1 ? `-${part}-of-${parts}` : ""}.xlsx`
