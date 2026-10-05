// TWIN: `discord/domain/GameChannelPolicies.kt` declares the codes. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

// TWIN: `cohort/domain/TargetRefusal.kt` declares the two a role or channel change can meet.
interface RefusalBody extends RefusalCode {
  reason?: string
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  DiscordUnreachable: () => "Discord cannot be reached now; try again in a moment.",
  TargetSystemUnavailable: () => "Discord cannot be reached now; try again in a moment.",
  TargetSystemRefused: (r) => `Discord refused it. ${r.reason}`,
}

export const {accepted, refusable} = refusalReader(sentences)
