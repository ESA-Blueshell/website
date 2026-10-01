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
}

export const {refusable} = refusalReader(sentences)
