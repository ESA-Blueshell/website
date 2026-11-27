/*
 * The vote domain's adapter. The annual vote pages read and write a vote from here, never from the
 * generated client (frontend ADR-001/002).
 */
import {
  addVoteTrack,
  annualVote,
  annualVoteStandings,
  annualVoteYears,
  apiUrl,
  castVoteBallot,
  createAnnualVote,
  currentAnnualVote,
  mergeVoteTrack,
  myVoteBallot,
  renameVoteTrack,
  rescheduleAnnualVote,
  searchVoteCandidates,
  storeVoteCandidate,
  voteCandidateTracks,
  voteSongs,
  type AnnualVoteResponse,
  type CatalogueGameResponse,
  type VoteBallotResponse,
  type VoteCandidateResponse,
  type VotePlacingResponse,
  type CandidateTrackResponse,
  type Image,
} from "@/services/api"
import {sizeOf, srcsetOf, type Picture} from "@/components/island/pictures"
import type {Refused} from "@/types/api"
import {readOr, type Answer} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import type {BallotPick} from "../ballot"
import {refusable} from "../refusals"

export type AnnualVote = AnnualVoteResponse
export type VoteKind = AnnualVoteResponse["kind"]
export type VoteState = AnnualVoteResponse["state"]
export type Ballot = VoteBallotResponse
export type CatalogueGame = CatalogueGameResponse
export type VoteCandidate = VoteCandidateResponse
export type Track = CandidateTrackResponse
export type Placing = VotePlacingResponse

/** The dates a vote runs on, as the api takes them. */
export interface VoteSchedule {
  opensAt: string
  closesAt: string
  revealAt: string
  /** How many picks a ballot holds; left out, a new vote takes its kind's number and a saved one keeps its own. */
  picks?: number
}

/** The years [kind] has a vote for, newest first; none where they could not be read. */
export const loadYears = async (kind: VoteKind): Promise<number[]> => readOr(annualVoteYears({path: {kind}}), [])

/** The vote of [kind] for [year], or null where there is none. */
export const loadYearVote = async (kind: VoteKind, year: number): Promise<AnnualVote | null> => readOr(annualVote({path: {kind, year}}), null)

/** Every live song whose title or game holds [query], game by game, for the board; null where they could not be read. */
export const loadSongs = async (query: string): Promise<Track[] | null> => readOr(voteSongs({query: {query}}), null)

/** The latest year's vote of [kind], or null when there is none yet. */
export const loadVote = async (kind: VoteKind): Promise<AnnualVote | null> => readOr(currentAnnualVote({path: {kind}}), null)

/**
 * A read the page says it could not make, as null. A lapsed sign-in also reaches the network
 * handler, whose message offers to sign in again.
 */
async function readOrSay<T>(call: Promise<Answer<T> & {response?: {status?: number}}>): Promise<T | null> {
  const answer = await call
  if (answer.response?.status === 401) {
    // Loaded when needed: the handler imports the router, which imports this domain's pages.
    const {$handleNetworkError} = await import("@/plugins/handleNetworkError")
    $handleNetworkError({response: answer.response})
  }
  return readOr(Promise.resolve(answer), null)
}

/** The signed-in member's own ballot, without picks until they vote, or null where it could not be read. */
export const loadBallot = (kind: VoteKind, year: number): Promise<Ballot | null> => readOrSay(myVoteBallot({path: {kind, year}}))

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
export const storeGame = (igdbId: number, vote: AnnualVote): Promise<Saved<VoteCandidate> | Refused> =>
  refusable(storeVoteCandidate({body: {igdbId, kind: vote.kind, year: vote.year}}), "That game could not be picked.")

/** The tracks members already picked for a game, first added first. */
export const tracksOf = async (igdbId: number): Promise<Track[]> => readOr(voteCandidateTracks({path: {igdbId}}), [])

/** The track a pasted YouTube or Spotify link is, as a track of the game [igdbId]. */
export const addTrack = (igdbId: number, url: string, vote: AnnualVote): Promise<Saved<Track> | Refused> =>
  refusable(addVoteTrack({body: {igdbId, url, kind: vote.kind, year: vote.year}}), "That link could not be added.")

/** Every game's standing so far, which only the board may read before the reveal, or null where it could not be read. */
export const loadStandings = (kind: VoteKind, year: number): Promise<Placing[] | null> =>
  readOrSay(annualVoteStandings({path: {kind, year}}))

/** Starts a [kind] vote for [year] on [dates]. */
export const createVote = (kind: VoteKind, year: number, dates: VoteSchedule): Promise<Saved<AnnualVote> | Refused> =>
  refusable(createAnnualVote({path: {kind}, body: {year, ...dates}}), "The vote could not be started.")

/** Moves the vote's dates and sets its number of picks until the first vote; [version] is the vote as the form read it, so a second board member's save is not lost. */
export const rescheduleVote = (kind: VoteKind, year: number, dates: VoteSchedule, version: number): Promise<Saved<AnnualVote> | Refused> =>
  refusable(rescheduleAnnualVote({path: {kind, year}, body: {...dates, version}}), "The dates could not be saved.")

/** Gives the track [id] the [title] a member writes, for everybody who picked it: by whoever added it, or the board. */
export const renameTrack = (id: number, title: string): Promise<Saved<Track> | Refused> =>
  refusable(renameVoteTrack({path: {id}, body: {title}}), "That song could not be renamed.")

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
  return {src: picture.url, srcset: srcsetOf(picture), ...sizeOf(picture)}
}
