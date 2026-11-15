import {beforeEach, describe, expect, it, vi} from "vitest"
import * as api from "@/services/api"
import {
  addTrack,
  castBallot,
  coverPreviewUrl,
  createVote,
  EMPTY_BALLOT,
  findGames,
  loadBallot,
  loadStandings,
  loadVote,
  mergeTrack,
  scheduleVote,
  storedUrl,
  storeGame,
  tracksOf,
} from "@/domains/vote"
import {answer, refusal} from "../../../helpers/sdkAnswers"
import {aGame, aPick, aPlacing, aResult, aTrack, aVote} from "../fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  addVoteTrack: vi.fn(),
  annualVoteStandings: vi.fn(),
  castVoteBallot: vi.fn(),
  createAnnualVote: vi.fn(),
  currentAnnualVote: vi.fn(),
  mergeVoteTrack: vi.fn(),
  myVoteBallot: vi.fn(),
  scheduleAnnualVote: vi.fn(),
  searchVoteGames: vi.fn(),
  storeVoteGame: vi.fn(),
  voteGameTracks: vi.fn(),
}))

describe("the vote adapter", () => {
  const track = aTrack(1)
  const dates = {opensAt: "2026-11-30T23:00:00Z", closesAt: "2026-12-21T22:59:00Z", revealAt: "2026-12-28T19:00:00Z"}

  beforeEach(() => vi.clearAllMocks())

  it("reads a kind's vote, or none", async () => {
    vi.mocked(api.currentAnnualVote).mockResolvedValueOnce(answer(api.currentAnnualVote, aVote("VOTING")))
    expect((await loadVote("OST_TOP_50"))?.state).toBe("VOTING")
    expect(api.currentAnnualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50"}})

    vi.mocked(api.currentAnnualVote).mockResolvedValueOnce(refusal(api.currentAnnualVote, {code: "NoAnnualVote"}, 404))
    expect(await loadVote("OST_TOP_50")).toBeNull()
  })

  it("reads the member's ballot, empty where it is refused", async () => {
    vi.mocked(api.myVoteBallot).mockResolvedValueOnce(answer(api.myVoteBallot, {version: 2, submittedAt: "2026-12-02T18:00:00Z", picks: [aPick(track)]}))
    expect((await loadBallot("OST_TOP_50", 2026)).picks).toHaveLength(1)
    expect(api.myVoteBallot).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}})

    vi.mocked(api.myVoteBallot).mockResolvedValueOnce(refusal(api.myVoteBallot, null, 403))
    expect(await loadBallot("OST_TOP_50", 2026)).toBe(EMPTY_BALLOT)
  })

  it("casts the picks by game and track id at the version read, and says why a cast is refused", async () => {
    vi.mocked(api.castVoteBallot).mockResolvedValueOnce(answer(api.castVoteBallot, {version: 0, submittedAt: "2026-12-02T18:00:00Z", picks: []}))
    const game = aGame(5)
    expect(await castBallot("OST_TOP_50", 2026, [aPick(track), {game, track: null}], null)).toMatchObject({ok: true})
    expect(api.castVoteBallot).toHaveBeenCalledWith({
      path: {kind: "OST_TOP_50", year: 2026},
      body: {picks: [{gameId: track.game.id, trackId: 1}, {gameId: 5, trackId: null}], version: null},
    })

    vi.mocked(api.castVoteBallot).mockResolvedValueOnce(refusal(api.castVoteBallot, {code: "GamePickedTwice"}))
    expect(await castBallot("OST_TOP_50", 2026, [aPick(track)], 3)).toEqual({ok: false, reason: expect.stringContaining("once")})
  })

  it("finds games for a kind and year, stores one, and lists a game's tracks", async () => {
    vi.mocked(api.searchVoteGames).mockResolvedValueOnce(answer(api.searchVoteGames, [aResult(1)]))
    expect(await findGames("hol", "GAME_OF_THE_YEAR", 2026)).toHaveLength(1)
    expect(api.searchVoteGames).toHaveBeenCalledWith({query: {query: "hol", kind: "GAME_OF_THE_YEAR", year: 2026}})
    vi.mocked(api.searchVoteGames).mockResolvedValueOnce(refusal(api.searchVoteGames, {code: "GameSearchUnavailable"}, 503))
    expect(await findGames("hol", "OST_TOP_50", 2026)).toEqual([])

    vi.mocked(api.storeVoteGame).mockResolvedValueOnce(answer(api.storeVoteGame, aGame(3)))
    expect(await storeGame(300)).toEqual({ok: true, saved: aGame(3)})
    expect(api.storeVoteGame).toHaveBeenCalledWith({body: {igdbId: 300}})

    vi.mocked(api.voteGameTracks).mockResolvedValueOnce(answer(api.voteGameTracks, [track]))
    expect(await tracksOf(10)).toEqual([track])
    expect(api.voteGameTracks).toHaveBeenCalledWith({path: {igdbId: 10}})
  })

  it("adds a track by its link", async () => {
    vi.mocked(api.addVoteTrack).mockResolvedValueOnce(answer(api.addVoteTrack, track))
    expect(await addTrack(10, "https://youtu.be/x")).toEqual({ok: true, saved: track})
    expect(api.addVoteTrack).toHaveBeenCalledWith({body: {igdbId: 10, url: "https://youtu.be/x"}})
  })

  it("runs the board's writes and reads", async () => {
    vi.mocked(api.annualVoteStandings).mockResolvedValueOnce(answer(api.annualVoteStandings, [aPlacing(1, track.game, track)]))
    expect(await loadStandings("OST_TOP_50", 2026)).toHaveLength(1)

    vi.mocked(api.createAnnualVote).mockResolvedValueOnce(answer(api.createAnnualVote, aVote("UPCOMING")))
    expect(await createVote("OST_TOP_50", 2026, dates)).toMatchObject({ok: true})
    expect(api.createAnnualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50"}, body: {year: 2026, ...dates}})

    vi.mocked(api.scheduleAnnualVote).mockResolvedValueOnce(answer(api.scheduleAnnualVote, aVote("VOTING")))
    await scheduleVote("OST_TOP_50", 2026, dates)
    expect(api.scheduleAnnualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}, body: dates})

    vi.mocked(api.mergeVoteTrack).mockResolvedValueOnce(answer(api.mergeVoteTrack, track))
    expect(await mergeTrack(2, 1)).toEqual({ok: true, saved: track})
    expect(api.mergeVoteTrack).toHaveBeenCalledWith({path: {id: 2}, body: {intoId: 1}})
  })

  it("draws a search result's cover through the api, and a stored picture from our storage", () => {
    expect(coverPreviewUrl("co1abc")).toMatch(/\/api\/vote-games\/covers\/co1abc$/)
    expect(coverPreviewUrl(null)).toBeNull()
    expect(storedUrl("/files/public/vote-covers/c.webp")).toMatch(/\/api\/files\/public\/vote-covers\/c\.webp$/)
    expect(storedUrl(null)).toBeNull()
  })
})
