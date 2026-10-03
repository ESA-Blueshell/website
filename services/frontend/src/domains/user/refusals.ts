// TWIN: `user/domain/RoleRefusal.kt`, `MandateRefusal.kt` and `sealing/Sealer.kt` declare the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  role?: string
  authorisedAt?: string
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
  MandateAddressMissing: () => "Fill in your whole address. It is recorded with the mandate.",
  MandateWordingOutdated: () => "The authorisation wording has changed since this page was opened. Reload the page and authorise again.",
  ReplacesOnlineMandate: r =>
    `This replaces the online mandate the member authorised on ${day(r.authorisedAt)}. Its PDF will no longer be available. Confirm to record it.`,
  NoMandateRecorded: () => "No mandate is recorded on this membership.",
  BankDetailsUnopenable: () => "These bank details were changed outside the site and no longer open. Record the mandate again.",
  SealingUnavailable: () => "Private details cannot be saved or shown right now, and nothing was changed. Try again in a moment.",
}

export const {refusable, accepted, reasonFor} = refusalReader(sentences)
