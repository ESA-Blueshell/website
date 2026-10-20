/**
 * A person's one bank account (api ADR-041). Every answer is its preview: the full account is
 * sealed on the api and opened only for the board's work.
 */
import {needsStepUp} from "@/domains/auth"
import {type BankAccountPreviewResponse, deleteOwnBankAccount, findOwnBankAccount, replaceOwnBankAccount} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {type BankAccount, type BankAccountPreview, maskedIban} from "../bankAccount"
import {reasonFor} from "../refusals"

/** A write that waits on a step-up the page asks for, as a change of bank details does. */
type Guarded<T> = T | (Refused & {needsStepUp?: boolean})

const shown = (account: BankAccountPreviewResponse): BankAccountPreview => ({
  preview: maskedIban(account.ibanCountry, account.ibanLastTwo),
  addedAt: account.recordedAt,
})

const refused = (error: unknown, fallback: string) => ({ok: false as const, reason: reasonFor(error, fallback), needsStepUp: needsStepUp(error)})

/** The reader's own account as the site shows it, or nothing where none is on file or it could not be read. */
export async function readOwnBankAccount(): Promise<BankAccountPreview | null> {
  const held = await readOr(findOwnBankAccount(), null)
  return held ? shown(held) : null
}

export async function saveOwnBankAccount(account: BankAccount): Promise<Guarded<Saved<BankAccountPreview>>> {
  const res = await replaceOwnBankAccount({body: {iban: account.iban, accountHolder: account.holder, bic: account.bic || undefined}})
  return res.data ? {ok: true, saved: shown(res.data)} : refused(res.error, "We cannot save your bank account right now. Try again in a moment.")
}

export async function removeOwnBankAccount(): Promise<Guarded<{ok: true}>> {
  const res = await deleteOwnBankAccount()
  return res.error ? refused(res.error, "We cannot delete your bank account right now. Try again in a moment.") : {ok: true}
}
