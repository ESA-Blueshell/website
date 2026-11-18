// TWIN: `vote/domain/VoteRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"
import type {VoteState} from "./adapters/vote"

interface RefusalBody extends RefusalCode {
  state?: VoteState
  max?: number
  year?: number
}

// Where a vote stands, as the end of "The vote …".
const STATE_SAID: Record<VoteState, string> = {
  UPCOMING: "has not opened yet",
  VOTING: "is open",
  CLOSED: "has closed",
  REVEALED: "has been revealed",
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  WrongVoteState: r => `The vote ${r.state ? STATE_SAID[r.state] : "is not open"}, so that cannot be done now.`,
  BallotSize: r => (r.max ? `A ballot holds one to ${r.max} picks.` : "A ballot holds too few or too many picks."),
  GamePickedTwice: () => "Each game can be on your ballot once. Pick a different game, or swap the track.",
  PickNeedsTrack: () => "Pick a track for each game on your ballot.",
  PickTakesNoTrack: () => "This vote picks games, not tracks.",
  GameNotEligible: r => `Only games first released in ${r.year ?? "the vote's year"} can be picked.`,
  UnsupportedTrackLink: () => "Paste a link to a YouTube video or a Spotify track.",
  TrackLinkUnreadable: () => "That link could not be read. It may be private or removed.",
  GameTrackLimit: r => `${r.max ? `That game already has ${r.max} tracks.` : "That game has all the tracks it can take."} Pick one of them.`,
  NoVoteOpen: () => "Covers are only shown while a vote is open.",
  CoverUnfetchable: () => "That cover could not be fetched.",
  TrackMergeInvalid: () => "A track can only be merged into another track of the same game.",
  GameSearchUnavailable: () => "Game search is unavailable right now. Try again in a moment.",
  AnnualVoteExists: () => "That year already has this vote.",
  VoteKindNotOffered: () => "That vote is not running yet.",
  VoteDatesOutOfOrder: () => "A vote opens before it closes, and its results are revealed after it closes.",
}

export const {reasonFor, refusable} = refusalReader(sentences)
