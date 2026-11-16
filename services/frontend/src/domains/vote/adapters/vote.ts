/*
 * The vote domain's adapter. The annual vote pages read and write a vote from here, never from the
 * generated client (frontend ADR-001/002).
 */
import {
  addVoteTrack,
  annualVoteStandings,
  apiUrl,
  castVoteBallot,
  createAnnualVote,
  currentAnnualVote,
  mergeVoteTrack,
  myVoteBallot,
  scheduleAnnualVote,
  searchVoteCandidates,
  storeVoteCandidate,
  voteCandidateTracks,
  type AnnualVoteResponse,
  type CatalogueGameResponse,
  type VoteBallotResponse,
  type VoteCandidateResponse,
  type VotePlacingResponse,
  type CandidateTrackResponse,
  type Image,
} from "@/services/api"
import {srcsetOf, type Picture} from "@/components/island/pictures"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import type {BallotPick} from "../ballot"
import {refusable} from "../refusals"

export type AnnualVote = AnnualVoteResponse
export type VoteKind = AnnualVoteResponse["kind"]
export type VoteState = AnnualVoteResponse["state"]
export type Ballot = VoteBallotResponse
export type CatalogueGame = CatalogueGameResponse
export type Candidate = VoteCandidateResponse
export type Track = CandidateTrackResponse
export type Placing = VotePlacingResponse

/** The dates a vote runs on, as the api takes them. */
export interface VoteSchedule {
  opensAt: string
  closesAt: string
  revealAt: string
}

/** A member's ballot before they have voted. */
export const EMPTY_BALLOT: Ballot = {version: null, submittedAt: null, picks: []}

/** The latest year's vote of [kind], or null when there is none yet. */
export const loadVote = async (kind: VoteKind): Promise<AnnualVote | null> => readOr(currentAnnualVote({path: {kind}}), null)

/** The signed-in member's own ballot, empty until they vote. */
export const loadBallot = async (kind: VoteKind, year: number): Promise<Ballot> =>
  readOr(myVoteBallot({path: {kind, year}}), EMPTY_BALLOT)

/** Saves the member's picks, best first, at the [version] they last read. */
export const castBallot = (kind: VoteKind, year: number, picks: BallotPick[], version: number | null): Promise<Saved<Ballot> | Refused> =>
  refusable(
    castVoteBallot({path: {kind, year}, body: {picks: picks.map(pick => ({candidateId: pick.candidate.id, trackId: pick.track?.id ?? null})), version}}),
    "Your ballot could not be saved.",
  )

/** Games whose name matches [query] that a [kind] vote in [year] may take, or why the search was refused. */
export const findGames = (query: string, kind: VoteKind, year: number): Promise<Saved<CatalogueGame[]> | Refused> =>
  refusable(searchVoteCandidates({query: {query, kind, year}}), "Game search did not answer. Try again in a moment.")

/** The game with that IGDB id as a pick names it, stored the first time anybody picks it. */
export const storeGame = (igdbId: number, vote: AnnualVote): Promise<Saved<Candidate> | Refused> =>
  refusable(storeVoteCandidate({body: {igdbId, kind: vote.kind, year: vote.year}}), "That game could not be picked.")

/** The tracks members already picked for a game, first added first. */
export const tracksOf = async (igdbId: number): Promise<Track[]> => readOr(voteCandidateTracks({path: {igdbId}}), [])

/** The track a pasted YouTube or Spotify link is, as a track of the game [igdbId]. */
export const addTrack = (igdbId: number, url: string, vote: AnnualVote): Promise<Saved<Track> | Refused> =>
  refusable(addVoteTrack({body: {igdbId, url, kind: vote.kind, year: vote.year}}), "That link could not be added.")

/** Every game's standing so far, which only the board may read before the results are out. */
export const loadStandings = async (kind: VoteKind, year: number): Promise<Placing[]> =>
  readOr(annualVoteStandings({path: {kind, year}}), [])

/** Starts a [kind] vote for [year] on [dates]. */
export const createVote = (kind: VoteKind, year: number, dates: VoteSchedule): Promise<Saved<AnnualVote> | Refused> =>
  refusable(createAnnualVote({path: {kind}, body: {year, ...dates}}), "The vote could not be started.")

/** Moves a vote's dates. */
/** Moves the vote's dates; [version] is the vote as the form read it, so a second board member's save is not lost. */
export const scheduleVote = (kind: VoteKind, year: number, dates: VoteSchedule, version: number): Promise<Saved<AnnualVote> | Refused> =>
  refusable(scheduleAnnualVote({path: {kind, year}, body: {...dates, version}}), "The dates could not be saved.")

/** Counts the track [id] as the track [intoId] from now on. */
export const mergeTrack = (id: number, intoId: number): Promise<Saved<Track> | Refused> =>
  refusable(mergeVoteTrack({path: {id}, body: {intoId}}), "Those tracks could not be merged.")

/** Where a search result's cover is drawn from: the api, which fetches it from IGDB. */
export const coverPreviewUrl = (imageId: string | null | undefined): string | null =>
  imageId ? apiUrl(`/vote-candidates/covers/${encodeURIComponent(imageId)}`) : null

/**
 * What an `<img>` needs to draw our stored copy of a cover or a thumbnail: the full-size address and
 * the stored widths, through the api, and its size, so the page keeps its space while it loads.
 */
export const imageAttrs = (image: Image) => {
  const picture: Picture = {...image, url: apiUrl(image.url), renditions: image.renditions.map(one => ({...one, url: apiUrl(one.url)}))}
  return {src: picture.url, srcset: srcsetOf(picture), width: image.width ?? undefined, height: image.height ?? undefined}
}
