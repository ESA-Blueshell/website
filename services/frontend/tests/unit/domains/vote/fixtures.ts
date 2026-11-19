import type {Image} from "@/services/api"
import {decemberDates, isoOf} from "@/domains/vote"
import type {AnnualVote, BallotPick, CatalogueGame, VoteCandidate, Placing, Track} from "@/domains/vote"

export const aVotePicture = (path: string, renditions: {url: string; width: number}[] = []): Image =>
  ({url: `/files/public/${path}`, path, width: 264, height: 352, renditions})

export const aGame = (id: number, name = `Game ${id}`, cover: Image | null = aVotePicture(`vote-covers/${id}.webp`)): VoteCandidate =>
  ({id, igdbId: id * 100, name, firstReleaseYear: 2017, cover})

export const aSearchHit = (igdbId: number, name = `Game ${igdbId}`, coverImageId: string | null = `co${igdbId}`): CatalogueGame =>
  ({igdbId, name, firstReleaseYear: 2017, coverImageId})

export const aTrack = (id: number, game: VoteCandidate = aGame(id * 10), source: Track["source"] = "YOUTUBE"): Track => ({
  id,
  candidate: game,
  source,
  externalId: `video${id}`,
  url: `https://www.youtube.com/watch?v=video${id}`,
  embedUrl: source === "SPOTIFY" ? `https://open.spotify.com/embed/track/video${id}` : `https://www.youtube-nocookie.com/embed/video${id}`,
  title: `Track ${id}`,
  thumbnail: aVotePicture(`vote-thumbnails/${id}.webp`),
})

export const aPick = (track: Track): BallotPick => ({candidate: track.candidate, track})

export const aPlacing = (place: number, game: VoteCandidate, track: Track | null, points = 12, voters = 1): Placing =>
  ({place, candidate: game, track, points, voters})

/** The api's five ranked picks, as the OST Top 50 counts them. */
export const FIVE_RANKED = [12, 10, 8, 6, 4]

// The board's default December, as the api hands it back.
const december = decemberDates(2026)

export const aVote = (state: AnnualVote["state"], overrides: Partial<AnnualVote> = {}): AnnualVote => ({
  kind: "OST_TOP_50",
  year: 2026,
  version: 2,
  state,
  opensAt: isoOf(december.opensAt)!,
  closesAt: isoOf(december.closesAt)!,
  revealAt: isoOf(december.revealAt)!,
  ballots: 3,
  results: [],
  points: FIVE_RANKED,
  picksTrack: true,
  releasedInYear: false,
  ...overrides,
})

/** A Game of the Year vote: games only, of its year. */
export const aGamesVote = (state: AnnualVote["state"], overrides: Partial<AnnualVote> = {}): AnnualVote =>
  aVote(state, {kind: "GAME_OF_THE_YEAR", picksTrack: false, releasedInYear: true, ...overrides})
