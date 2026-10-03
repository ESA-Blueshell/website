// TWIN: `cohort/domain/TargetRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  system?: string
  reason?: string
  externalId?: string
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  TargetSystemRefused: r => `${r.system} refused it: ${r.reason}`,
  TargetSystemUnavailable: r => `${r.system} cannot be reached now; try again later.`,
  TargetNotFound: r => `${r.system} has no list ${r.externalId}; reload the lists.`,
  TargetStillLinked: () => "A list linked to a cohort is archived, not deleted.",
  TargetNameMismatch: () => "That is not the list's name; type it exactly to delete it.",
  TargetNotOfCohort: () => "That target is not this cohort's; reload the page.",
  TargetNotCreated: () => "The target has not been created yet.",
}

export const {sentenceFor, refusable, accepted} = refusalReader(sentences)
