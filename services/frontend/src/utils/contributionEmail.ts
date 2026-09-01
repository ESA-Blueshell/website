import {DateTime} from "luxon"
import {
  ContributionEmailKind,
  type BulkContributionEmailRowResponse,
  type ContributionPeriodResponse,
} from "@/services/api"
import {formatBulkDate} from "@/utils/bulkDisposition"
import {BulkFeeType, type BulkRow} from "@/utils/bulkRow"

/**
 * The payment-email rows, as the wizard reads them.
 *
 * Decided by the api rather than the browser: whether a member is warned about turns on
 * facts this page does not hold, and the send re-reads the same answer.
 */

export function toBulkRow(row: BulkContributionEmailRowResponse): BulkRow {
  return {
    userId: row.userId,
    name: row.name,
    disposition: row.disposition,
    reason: row.reason ?? undefined,
    memberType: row.memberType,
    memberSince: row.memberSince ?? undefined,
    amount: row.amount ?? null,
    recommendedFeeType: row.feeType ?? undefined,
    defaultKind: row.defaultKind,
    lastRemindedOn: row.lastRemindedOn ?? undefined,
    lastNotifiedOn: row.lastNotifiedOn ?? undefined,
  }
}

export function toBulkRows(rows: BulkContributionEmailRowResponse[]): BulkRow[] {
  return rows.map(toBulkRow)
}

export const contributionEmailLabels: Record<ContributionEmailKind, string> = {
  [ContributionEmailKind.REMINDER]: "Contribution reminder",
  [ContributionEmailKind.INCASSO_NOTIFICATION]: "Incasso notification",
}

export const contributionEmailItems: Array<{title: string; value: ContributionEmailKind}> =
  Object.values(ContributionEmailKind).map((value) => ({title: contributionEmailLabels[value], value}))

/** Which email a row is set to: the flag's choice unless the treasurer switched it. */
export function kindFor(row: BulkRow, chosen: Record<number, ContributionEmailKind>): ContributionEmailKind {
  return chosen[row.userId] ?? row.defaultKind ?? ContributionEmailKind.REMINDER
}

export function isSwitched(row: BulkRow, chosen: Record<number, ContributionEmailKind>): boolean {
  return !!row.defaultKind && kindFor(row, chosen) !== row.defaultKind
}

/** Why switching this row is worth flagging: the flag says the other thing. */
export function switchedNote(row: BulkRow): string {
  return row.defaultKind === ContributionEmailKind.INCASSO_NOTIFICATION
    ? "Pays by direct debit"
    : "No direct-debit mandate"
}

/** When this member last got the email they are getting now. */
export function lastSentOn(
  row: BulkRow,
  chosen: Record<number, ContributionEmailKind>,
): string | undefined {
  return kindFor(row, chosen) === ContributionEmailKind.INCASSO_NOTIFICATION
    ? row.lastNotifiedOn
    : row.lastRemindedOn
}

export function lastSentLabel(iso: string | undefined): string {
  const formatted = formatBulkDate(iso)
  // The absence is the answer here, so it reads as a word rather than an em dash.
  return formatted === "—" ? "Never" : formatted
}

/** A member this send can reach at all, so the wizard offers them a Send-to box. */
export function isSelectable(row: BulkRow): boolean {
  return row.disposition !== "EXCLUDED"
}

/**
 * Which rows the wizard starts ticked. A warned row starts unticked, so the safe choice is
 * the one the treasurer gets by doing nothing; a member it cannot reach has no box at all.
 */
export function seedSendTo(rows: BulkRow[]): Record<number, boolean> {
  const sendTo: Record<number, boolean> = {}
  for (const row of rows) {
    if (isSelectable(row)) sendTo[row.userId] = row.disposition === "INCLUDED"
  }
  return sendTo
}

/** The Send-to box is the selection: a row sends when it is ticked, and only then. */
export function willSend(row: BulkRow, sendTo: Record<number, boolean>): boolean {
  return isSelectable(row) && !!sendTo[row.userId]
}

/** Ticked rows the api warned about. It re-decides on send, so it is told to overrule. */
export function forcedUserIds(rows: BulkRow[], sendTo: Record<number, boolean>): number[] {
  return rows
    .filter((row) => row.disposition === "WARNING" && willSend(row, sendTo))
    .map((row) => row.userId)
}

/** A payment date may run this far past the period's end, so August chasing stays possible. */
export const PERIOD_OVERHANG_MONTHS = 3

type PeriodDates = Pick<ContributionPeriodResponse, "startDate" | "endDate">

/** The window a payment due date or debit date may fall in. */
export function periodDateWindow(
  period: PeriodDates | null | undefined,
): {from: string; until: string} | null {
  if (!period) return null
  return {
    from: period.startDate,
    until: DateTime.fromISO(period.endDate)
      .plus({months: PERIOD_OVERHANG_MONTHS})
      .toFormat("yyyy-MM-dd"),
  }
}

/**
 * Why this date cannot be sent, or null when it can.
 *
 * The api enforces the same rule on send — its copy is the period-bounds check in
 * `BulkContributionEmailUseCases`, mirrored here the way `effectiveAmount` and
 * `resolveFeeAmount` name each other. Changing one means changing the other.
 */
export function paymentDateProblem(
  iso: string,
  period: PeriodDates | null | undefined,
  today: string,
): string | null {
  if (!iso) return null
  if (iso <= today) return "The date must be after today."
  const window = periodDateWindow(period)
  if (!window) return null
  if (iso < window.from || iso > window.until) {
    return `The date must fall between ${formatBulkDate(window.from)} and ${formatBulkDate(window.until)}.`
  }
  return null
}

/** How many of each email this send would put out. */
export function countByKind(
  rows: BulkRow[],
  chosen: Record<number, ContributionEmailKind>,
  sendTo: Record<number, boolean>,
): Record<ContributionEmailKind, number> {
  const counts = {
    [ContributionEmailKind.REMINDER]: 0,
    [ContributionEmailKind.INCASSO_NOTIFICATION]: 0,
  }
  for (const row of rows) {
    if (willSend(row, sendTo)) counts[kindFor(row, chosen)]++
  }
  return counts
}

/** What the send is about to do, for the confirmation that stands in front of it. */
export interface PaymentEmailSummary {
  reminders: number
  incassoNotifications: number
  total: number
  /** Selected members the batch leaves alone, whether unticked or never reachable. */
  notEmailed: number
  /** Warned rows the operator ticked back in. */
  forced: number
  /** Rows moved off the email their direct-debit flag chose. */
  switched: number
  /** Rows charged a fee type other than the one that applies. */
  reCharged: number
  /** Recipients who have had this same email for this period before. */
  alreadySent: number
}

export function summarise(
  rows: BulkRow[],
  kinds: Record<number, ContributionEmailKind>,
  fees: Record<number, BulkFeeType>,
  sendTo: Record<number, boolean>,
): PaymentEmailSummary {
  const recipients = rows.filter((row) => willSend(row, sendTo))
  const counts = countByKind(rows, kinds, sendTo)
  return {
    reminders: counts[ContributionEmailKind.REMINDER],
    incassoNotifications: counts[ContributionEmailKind.INCASSO_NOTIFICATION],
    total: recipients.length,
    notEmailed: rows.length - recipients.length,
    forced: recipients.filter((row) => row.disposition === "WARNING").length,
    switched: recipients.filter((row) => isSwitched(row, kinds)).length,
    reCharged: recipients.filter((row) => {
      const chosen = fees[row.userId]
      return !!chosen && chosen !== row.recommendedFeeType
    }).length,
    alreadySent: recipients.filter((row) => !!lastSentOn(row, kinds)).length,
  }
}

/** Only the rows the treasurer moved off the email their flag chose. */
export function changedKinds(
  rows: BulkRow[],
  chosen: Record<number, ContributionEmailKind>,
): Record<string, ContributionEmailKind> {
  const changed: Record<string, ContributionEmailKind> = {}
  for (const row of rows) {
    if (isSwitched(row, chosen)) changed[String(row.userId)] = kindFor(row, chosen)
  }
  return changed
}

/**
 * Only the fee types the treasurer changed. Stating every row would claim a choice where
 * none was made, and the api refuses a type naming somebody it does not write to.
 */
export function changedFeeTypes(
  rows: BulkRow[],
  selections: Record<number, BulkFeeType>,
): Record<string, BulkFeeType> {
  const changed: Record<string, BulkFeeType> = {}
  for (const row of rows) {
    const chosen = selections[row.userId]
    if (chosen && chosen !== row.recommendedFeeType) changed[String(row.userId)] = chosen
  }
  return changed
}
