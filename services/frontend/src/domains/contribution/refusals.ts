// TWIN: `contribution/domain/IncassoRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  userIds?: number[]
  max?: number
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  NothingToCollect: () => "Choose at least one member to collect from.",
  NotCollectable: (r) =>
    `${r.userIds?.length ?? "Some"} of the members chosen can no longer be collected from. Go back to the first step to see why.`,
  CollectionDateNotAhead: () => "The collection date has to be after today.",
  CollectionDateOutsidePeriod: () => "The collection date has to fall in the contribution period, or within three months after it.",
  StatementTextMissing: () => "Say what the collection is for on their bank statement.",
  StatementTextTooLong: (r) => `The text on their bank statement can be at most ${r.max ?? 140} characters.`,
  IncassoRunNotFound: () => "There is no such incasso.",
  IncassoRunSubmitted: () => "This incasso is already in ING, so its file is not made again.",
  CollectionDatePassed: () => "The collection date has passed. Run a new incasso with a date ahead.",
  IngDetailsMissing: () => "The association's IBAN or incassant ID is not set up, so ING's file cannot be filled in. Ask an admin.",
  MandateChanged: (r) =>
    `${r.userIds?.length ?? "Some"} of the members changed their bank details after they were told. Run a new incasso for them.`,
  IncassoFilePartNotFound: () => "This incasso has no such file.",
  // Declared in `user`, which opens the bank details the file is filled with.
  SealingUnavailable: () => "The bank details cannot be opened right now, so ING's file cannot be made. Try again in a moment.",
  BankDetailsUnopenable: () =>
    "A member's bank details were changed outside the site and no longer open, so ING's file cannot be made. Ask an admin.",
}

export const {refusable} = refusalReader(sentences)
