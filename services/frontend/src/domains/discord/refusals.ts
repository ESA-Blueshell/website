// TWIN: `discord/domain/GameChannelPolicies.kt` declares the codes. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

// TWIN: `cohort/domain/TargetRefusal.kt` declares the two a role or channel change can meet.
interface RefusalBody extends RefusalCode {
  reason?: string
  cohort?: string
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  DiscordUnreachable: () => "Discord cannot be reached now; try again in a moment.",
  TargetSystemUnavailable: () => "Discord cannot be reached now; try again in a moment.",
  TargetSystemRefused: (r) => `Discord refused it. ${r.reason}`,
  TargetLinkedElsewhere: (r) => `That role already belongs to ${r.cohort}. Unlink it there first, or move it here.`,
  BoardCommitteeHasNoRole: () => "The board's committee has no Discord role: the board in office holds @Board.",
}

export const {accepted, reasonFor, refusable} = refusalReader(sentences)
