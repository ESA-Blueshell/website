// TWIN: `committee/api/CommitteeRefusal.kt` declares the codes and their facts, and
// `game/api/GameRefusal.kt` the game codes a committee's games can be refused with, and
// `cohort/domain/TargetRefusal.kt` the Discord ones. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  committeeName?: string
  events?: number
  address?: string
  gameName?: string
  cohort?: string
  reason?: string
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  CommitteeAddressBlank: () => "A committee's page needs an address.",
  CommitteeAddressTaken: r => `The address '${r.address}' is already used by ${r.committeeName}.`,
  UnknownCommitteeAddress: r => `No committee answers to '${r.address}'.`,
  CommitteeEventsNeedTaker: r => `It still organises ${r.events} ${r.events === 1 ? "event" : "events"}. Pick the committee that takes them over.`,
  CommitteeCannotTakeOwnEvents: () => "Pick another committee to take over its events.",
  ArchivedCommitteeCannotTakeEvents: r => `${r.committeeName} is archived, so it cannot take over events.`,
  GameArchived: r => `${r.gameName} is archived, so it cannot be newly picked.`,
  PictureNotStored: () => "That picture is not in storage.",
  TargetSystemUnavailable: () => "Discord cannot be reached now, so its role and channels are left as they were.",
  TargetSystemRefused: r => `Discord refused it: ${r.reason}`,
  TargetLinkedElsewhere: r => `${r.cohort} already follows that role. Pick another role.`,
  BoardCommitteeHasNoRole: () => "The board's committee gets no Discord role.",
}

export const {sentenceFor, refusable, accepted} = refusalReader(sentences)
