// TWIN: `cohort/domain/TargetRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  system?: string
  reason?: string
  externalId?: string
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  TargetSystemRefused: r => `${r.system} refused it: ${r.reason}`,
  TargetNotFound: r => `${r.system} has no list ${r.externalId}; reload the lists.`,
}

export const {sentenceFor, refusable} = refusalReader(sentences)
