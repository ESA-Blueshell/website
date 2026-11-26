/** Committees and teams matched by name to the server's roles, and linking the confirmed ones. */
import {adoptDiscordMatches, type AdoptionMatch, type AdoptionOutcome, listDiscordMatches, type RefusedMatch} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import {refusalReader, type RefusalCode, type Saved} from "@/utils/refusals"

export type {AdoptionMatch, AdoptionOutcome}

interface RefusalBody extends RefusalCode {
  cohort?: string
  channel?: string
  category?: string
  reason?: string
}

// TWIN: `cohort/domain/TargetRefusal.kt` declares the codes and their facts. See ADR-026.
const sentences: Record<string, (r: RefusalBody) => string> = {
  TargetSystemUnavailable: () => "Discord cannot be reached now.",
  TargetSystemRefused: r => `Discord refused it: ${r.reason}`,
  TargetLinkedElsewhere: r => `${r.cohort} already follows that role.`,
  ChannelOutsideCategory: r => `#${r.channel} is not under ${r.category}, so it cannot be opened to the role.`,
}

const {refusable, sentenceFor} = refusalReader(sentences)

/** The matches by name; nothing where they could not be read. */
export const listMatches = (): Promise<AdoptionMatch[] | null> => readOr(listDiscordMatches(), null)

/** Links the confirmed matches, each with the role and channels the board saw beside it. */
export const adoptMatches = (matches: AdoptionMatch[]): Promise<Saved<AdoptionOutcome> | Refused> =>
  refusable(
    adoptDiscordMatches({body: {matches: matches.map(match => ({key: match.key, roleId: match.roleId, channelIds: match.channels.map(channel => channel.id)}))}}),
    "The roles could not be linked.",
  )

/** Why one confirmed match was not linked, in words. */
export const refusedReason = (refused: RefusedMatch): string =>
  sentenceFor({code: refused.code ?? undefined, ...refused.facts})
  ?? (typeof refused.facts.reason === "string" ? refused.facts.reason : "It could not be linked.")
