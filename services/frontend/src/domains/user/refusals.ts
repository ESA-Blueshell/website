// TWIN: `user/domain/RoleRefusal.kt`, `MandateRefusal.kt`, `MembershipTypeRefusal.kt` and `sealing/Sealer.kt` declare the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  role?: string
  authorisedAt?: string
  startDate?: string
  endDate?: string
}

const day = (iso?: string): string => (iso ? new Date(iso).toLocaleDateString("en-GB", {day: "numeric", month: "long", year: "numeric"}) : "an earlier day")

const sentences: Record<string, (r: RefusalBody) => string> = {
  RoleNotAssignable: r =>
    `${r.role ?? "That role"} is not a role an admin hands out. `
    + "Member follows a membership and committee follows a committee seat.",
  LastAdministrator: () =>
    "This is the last administrator. Give somebody else admin first, "
    + "so that nobody is locked out of the site.",
  InvalidIban: () => "That is not a valid IBAN. Check it against the bank card or statement.",
  MandateSignedInFuture: () => "A mandate is signed today or before, not after.",
  AccountHolderMissing: () => "Say whose account it is.",
  MandateAddressMissing: () => "Direct debit needs your full home address. Add the missing parts to your account, then try again.",
  MandateWordingOutdated: () => "The authorisation wording has changed since this page was opened. Reload the page and authorise again.",
  ReplacesOnlineMandate: r =>
    `This replaces the online mandate the member authorised on ${day(r.authorisedAt)}. Its PDF will no longer be available. Confirm to record it.`,
  NoOnlineMandate: () => "Only a mandate the member authorised on the site has a PDF.",
  NoBankAccount: () => "Add a bank account first. Direct debit collects your contribution from it.",
  NoMandateRecorded: () => "No mandate is recorded for this person.",
  BankDetailsUnopenable: () => "These bank details were changed outside the site and no longer open. Record the mandate again.",
  MembershipTypeOutsideMembership: r => (r.endDate
    ? `Pick a day from ${day(r.startDate)} to the day before ${day(r.endDate)}.`
    : `Pick a day on or after ${day(r.startDate)}.`),
  FirstMembershipTypeStartsTheMembership: () =>
    "The first membership type always starts with the membership, so it cannot be removed. Add a type on the start day to change it.",
  MembershipTypeNotFound: () => "That membership type is gone. Reload the page.",
  SealingUnavailable: () => "Private details cannot be saved or shown right now, and nothing was changed. Try again in a moment.",
}

export const {refusable, accepted, reasonFor} = refusalReader(sentences)
