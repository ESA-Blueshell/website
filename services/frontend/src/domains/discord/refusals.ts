// TWIN: `discord/domain/GameChannelPolicies.kt` declares the codes. See ADR-026.

import {refusalReader} from "@/utils/refusals"

const sentences: Record<string, () => string> = {
  DiscordUnreachable: () => "Discord cannot be reached now; try again in a moment.",
}

export const {accepted, refusable} = refusalReader(sentences)
