import type {Image} from "@/services/api"
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

export const aVote = (state: AnnualVote["state"], overrides: Partial<AnnualVote> = {}): AnnualVote => ({
  kind: "OST_TOP_50",
  year: 2026,
  version: 2,
  state,
  opensAt: "2026-11-30T23:00:00Z",
  closesAt: "2026-12-21T22:59:00Z",
  revealAt: "2026-12-28T19:00:00Z",
  ballots: 3,
  results: [],
  points: [12, 10, 8, 6, 4],
  picksTrack: true,
  releasedInYear: false,
  ...overrides,
})

/** A Game of the Year vote: games only, of its year. */
export const aGamesVote = (state: AnnualVote["state"], overrides: Partial<AnnualVote> = {}): AnnualVote =>
  aVote(state, {kind: "GAME_OF_THE_YEAR", picksTrack: false, releasedInYear: true, ...overrides})
