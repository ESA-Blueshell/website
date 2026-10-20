import {DateTime} from "luxon"
import type {StateKind} from "@/components/island/StateMark.vue"
import {BookingKind, DeclarationState} from "@/services/api"

export {BookingKind, DeclarationState}

export interface Receipt {
  id: number
  /** Ends in the file's kind, which is how the picture input tells a PDF from a photo. */
  path: string
  name: string
  /** The file's media type: an image or `application/pdf`. */
  type: string
  url: string
}

export interface ExpenseFor {
  kind: BookingKind
  id: number
  name: string
}

export interface Expense {
  /** The api's id once saved; a new expense carries one made up here until then. */
  id: string
  /** The ISO day it was paid, or empty while a draft misses it. */
  paidOn: string
  description: string
  /** Euros as the decimal string the money field writes. */
  amount: string
  for: ExpenseFor | null
  receipts: Receipt[]
}

/** A committee or event an expense may be booked to, as the pickers offer it. */
export interface Choice {
  id: number
  name: string
  note?: string
}

export interface DeclarationStep {
  state: DeclarationState
  at: string
  by: string
  reason?: string
}

export interface Declaration {
  id: number
  submitterId: number
  submitter: string
  state: DeclarationState
  expenses: Expense[]
  /** The receipts for the declaration as a whole, rather than for one expense. */
  receipts: Receipt[]
  confirmed: boolean
  /** Every change of state, oldest first. A draft sent back carries the reason on its last step. */
  steps: DeclarationStep[]
  createdAt: string
}

export type DeclarationDraft = Pick<Declaration, "expenses" | "receipts" | "confirmed">

/** Cents from the money field's decimal string; nothing where it holds no number. */
export const centsOf = (amount: string): number | null => {
  const value = Number.parseFloat(amount)
  return Number.isFinite(value) ? Math.round(value * 100) : null
}

export const totalOf = (expenses: readonly Expense[]): number =>
  expenses.reduce((sum, expense) => sum + (centsOf(expense.amount) ?? 0), 0) / 100

export const euros = (amount: number): string => `€ ${amount.toFixed(2)}`

export const day = (iso: string): string => DateTime.fromISO(iso).toFormat("d LLLL yyyy")

/** The day as the paper form wrote it. */
export const isoDay = (iso: string): string => DateTime.fromISO(iso).toISODate() ?? iso

/** Every receipt a declaration holds, its expenses' and its own. */
export const receiptsOf = (declaration: DeclarationDraft): Receipt[] =>
  [...declaration.expenses.flatMap(expense => expense.receipts), ...declaration.receipts]

export type Tone = "accent" | "ok" | "warn" | "quiet"

const STATES: Record<DeclarationState, {label: string; tone: Tone}> = {
  [DeclarationState.DRAFT]: {label: "Draft", tone: "quiet"},
  [DeclarationState.SUBMITTED]: {label: "Submitted", tone: "accent"},
  [DeclarationState.APPROVED]: {label: "Approved", tone: "accent"},
  [DeclarationState.PAID]: {label: "Paid", tone: "ok"},
  [DeclarationState.REJECTED]: {label: "Rejected", tone: "warn"},
}

/* The marks the other Management lists use: an outline waits for someone, a filled one is under way. */
export const STATE_MARKS: Record<DeclarationState, StateKind> = {
  [DeclarationState.SUBMITTED]: "extra",
  [DeclarationState.APPROVED]: "missing",
  [DeclarationState.PAID]: "in-sync",
  [DeclarationState.REJECTED]: "not-created",
  [DeclarationState.DRAFT]: "not-compared",
}

/** What the board can still send back or reject. TWIN: `WITH_THE_BOARD` in the api's `DeclarationReviews.kt`. */
export const withTheBoard = (declaration: Pick<Declaration, "state">): boolean =>
  declaration.state === DeclarationState.SUBMITTED || declaration.state === DeclarationState.APPROVED

/** A draft the board returned reads differently from one never sent. */
export const sentBack = (declaration: Pick<Declaration, "state" | "steps">): DeclarationStep | null => {
  const last = declaration.steps.at(-1)
  return declaration.state === DeclarationState.DRAFT && last?.state === DeclarationState.DRAFT && last.reason ? last : null
}

export const stateOf = (declaration: Declaration): {label: string; tone: Tone} =>
  sentBack(declaration) ? {label: "Sent back", tone: "warn"} : STATES[declaration.state]

const STEP_LABELS: Record<DeclarationState, string> = {
  [DeclarationState.DRAFT]: "Started",
  [DeclarationState.SUBMITTED]: "Submitted",
  [DeclarationState.APPROVED]: "Approved",
  [DeclarationState.PAID]: "Marked as paid",
  [DeclarationState.REJECTED]: "Rejected",
}

/** One line of the history: a later draft step is the board sending it back, or its submitter withdrawing it. */
export const stepLabel = (step: DeclarationStep, index: number): string => {
  if (step.state !== DeclarationState.DRAFT || index === 0) return STEP_LABELS[step.state]
  return step.reason ? "Sent back" : "Withdrawn"
}

export const stepAt = (declaration: Pick<Declaration, "steps">, state: DeclarationState): DeclarationStep | null =>
  declaration.steps.findLast(step => step.state === state) ?? null

/** The committees and events the expenses were for, each once. */
export const forsOf = (declaration: Declaration): string[] =>
  [...new Set(declaration.expenses.flatMap(expense => (expense.for ? [expense.for.name] : [])))]

/** The declaration named by what it was for, since several are often open at once. TWIN: `titleOf` in the api's `DeclarationReviews.kt`. */
export function titleOf(declaration: Declaration): string {
  const first = declaration.expenses[0]?.description.trim()
  if (!first) return "New declaration"
  const more = declaration.expenses.length - 1
  return more === 0 ? first : `${first} and ${more} more`
}

export function metaOf(declaration: Declaration): string {
  const count = declaration.expenses.length
  const submitted = stepAt(declaration, DeclarationState.SUBMITTED)
  const paid = stepAt(declaration, DeclarationState.PAID)
  return [
    count === 1 ? "1 expense" : `${count} expenses`,
    euros(totalOf(declaration.expenses)),
    paid && declaration.state === DeclarationState.PAID
      ? `Paid ${day(paid.at)}`
      : submitted && declaration.state !== DeclarationState.DRAFT
        ? `Submitted ${day(submitted.at)}`
        : `Started ${day(declaration.createdAt)}`,
  ].join(" · ")
}
