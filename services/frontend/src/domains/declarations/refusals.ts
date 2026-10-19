// TWIN: `declaration/domain/DeclarationRefusal.kt` declares the codes. See api ADR-026.

import {DeclarationState} from "@/services/api"
import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface DeclarationRefusal extends RefusalCode {
  state?: DeclarationState
  expense?: number
  field?: "paidOn" | "amount" | "description"
}

const MISSING: Record<NonNullable<DeclarationRefusal["field"]>, string> = {
  paidOn: "the date you paid",
  amount: "an amount above zero",
  description: "a description",
}

const STATE_WORDS: Record<DeclarationState, string> = {
  [DeclarationState.DRAFT]: "a draft again",
  [DeclarationState.SUBMITTED]: "waiting to be checked",
  [DeclarationState.APPROVED]: "already approved",
  [DeclarationState.PAID]: "already paid",
  [DeclarationState.REJECTED]: "already rejected",
}

const sentences: Record<string, (r: DeclarationRefusal) => string> = {
  DeclarationNotFound: () => "This declaration does not exist, or it is not yours.",
  DeclarationNotDraft: () => "This declaration is already submitted, so you cannot change it. Withdraw it to make changes.",
  DeclarationNotSubmitted: () => "The board has already approved this declaration, so you cannot withdraw it.",
  DeclarationStateChanged: r => {
    const now = r.state ? STATE_WORDS[r.state] : undefined
    return now ? `This declaration is ${now}. Reload the page.` : "This declaration has changed. Reload the page."
  },
  ReasonMissing: () => "Write the reason. The person who submitted it gets it by email.",
  NoBankAccount: () => "Add your bank account before you submit.",
  NoReceipt: () => "Add at least one receipt.",
  NotConfirmed: () => "Tick the box to confirm.",
  NoExpense: () => "Add at least one expense.",
  ExpenseIncomplete: r => `Expense ${r.expense} needs ${r.field ? MISSING[r.field] : "more details"}.`,
  PaidInFuture: r => `The date you paid for expense ${r.expense} is in the future.`,
  UnknownBooking: () => "That committee or event no longer exists. Choose another one.",
  UnknownReceipt: () => "One of the receipts could not be found. Add it again.",
}

export const {reasonFor, refusable, accepted} = refusalReader(sentences)
