import {beforeEach, describe, expect, it, vi} from "vitest"
import * as api from "@/services/api"
import * as network from "@/plugins/handleNetworkError"
import {
  addTrack,
  castBallot,
  coverPreviewUrl,
  createVote,
  findGames,
  loadBallot,
  loadStandings,
  loadVote,
  mergeTrack,
  rescheduleVote,
  imageAttrs,
  storeGame,
  tracksOf,
} from "@/domains/vote"
import {answer, refusal} from "../../../helpers/sdkAnswers"
import {aGame, aVotePicture, aPick, aPlacing, aSearchHit, aTrack, aVote} from "../fixtures"

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: vi.fn()}))

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  addVoteTrack: vi.fn(),
  annualVoteStandings: vi.fn(),
  castVoteBallot: vi.fn(),
  createAnnualVote: vi.fn(),
  currentAnnualVote: vi.fn(),
  mergeVoteTrack: vi.fn(),
  myVoteBallot: vi.fn(),
  rescheduleAnnualVote: vi.fn(),
  searchVoteCandidates: vi.fn(),
  storeVoteCandidate: vi.fn(),
  voteCandidateTracks: vi.fn(),
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

  it("reads the member's ballot, and none where it is refused", async () => {
    vi.mocked(api.myVoteBallot).mockResolvedValueOnce(answer(api.myVoteBallot, {version: 2, submittedAt: "2026-12-02T18:00:00Z", picks: [aPick(track)]}))
    expect((await loadBallot("OST_TOP_50", 2026)).picks).toHaveLength(1)
    expect(api.myVoteBallot).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}})

    vi.mocked(api.myVoteBallot).mockResolvedValueOnce(refusal(api.myVoteBallot, null, 403))
    expect(await loadBallot("OST_TOP_50", 2026)).toBeNull()
    expect(network.$handleNetworkError).not.toHaveBeenCalled()
  })

  it("hands a lapsed sign-in to the network handler, which offers to sign in again", async () => {
    vi.mocked(api.myVoteBallot).mockResolvedValueOnce(refusal(api.myVoteBallot, null, 401))
    expect(await loadBallot("OST_TOP_50", 2026)).toBeNull()
    expect(network.$handleNetworkError).toHaveBeenCalledWith({response: expect.objectContaining({status: 401})})

    vi.mocked(api.annualVoteStandings).mockResolvedValueOnce(refusal(api.annualVoteStandings, null, 500))
    expect(await loadStandings("OST_TOP_50", 2026)).toBeNull()
  })

  it("casts the picks by game and track id at the version read, and says why a cast is refused", async () => {
    vi.mocked(api.castVoteBallot).mockResolvedValueOnce(answer(api.castVoteBallot, {version: 0, submittedAt: "2026-12-02T18:00:00Z", picks: []}))
    const game = aGame(5)
    expect(await castBallot("OST_TOP_50", 2026, [aPick(track), {candidate: game, track: null}], null)).toMatchObject({ok: true})
    expect(api.castVoteBallot).toHaveBeenCalledWith({
      path: {kind: "OST_TOP_50", year: 2026},
      body: {picks: [{candidateId: track.candidate.id, trackId: 1}, {candidateId: 5, trackId: null}], version: null},
    })

    vi.mocked(api.castVoteBallot).mockResolvedValueOnce(refusal(api.castVoteBallot, {code: "GamePickedTwice"}))
    expect(await castBallot("OST_TOP_50", 2026, [aPick(track)], 3)).toEqual({ok: false, reason: expect.stringContaining("once")})
  })

  it("finds games for a kind and year, stores one, and lists a game's tracks", async () => {
    vi.mocked(api.searchVoteCandidates).mockResolvedValueOnce(answer(api.searchVoteCandidates, [aSearchHit(1)]))
    expect(await findGames("hol", "GAME_OF_THE_YEAR", 2026)).toEqual({ok: true, saved: [aSearchHit(1)]})
    expect(api.searchVoteCandidates).toHaveBeenCalledWith({query: {query: "hol", kind: "GAME_OF_THE_YEAR", year: 2026}})
    vi.mocked(api.searchVoteCandidates).mockResolvedValueOnce(refusal(api.searchVoteCandidates, {code: "GameSearchUnavailable"}, 503))
    expect(await findGames("hol", "OST_TOP_50", 2026)).toEqual({ok: false, reason: "Game search is unavailable right now. Try again in a moment."})

    vi.mocked(api.storeVoteCandidate).mockResolvedValueOnce(answer(api.storeVoteCandidate, aGame(3)))
    expect(await storeGame(300, aVote("VOTING"))).toEqual({ok: true, saved: aGame(3)})
    expect(api.storeVoteCandidate).toHaveBeenCalledWith({body: {igdbId: 300, kind: "OST_TOP_50", year: 2026}})

    vi.mocked(api.voteCandidateTracks).mockResolvedValueOnce(answer(api.voteCandidateTracks, [track]))
    expect(await tracksOf(10)).toEqual([track])
    expect(api.voteCandidateTracks).toHaveBeenCalledWith({path: {igdbId: 10}})
  })

  it("adds a track by its link", async () => {
    vi.mocked(api.addVoteTrack).mockResolvedValueOnce(answer(api.addVoteTrack, track))
    expect(await addTrack(10, "https://youtu.be/x", aVote("VOTING"))).toEqual({ok: true, saved: track})
    expect(api.addVoteTrack).toHaveBeenCalledWith({body: {igdbId: 10, url: "https://youtu.be/x", kind: "OST_TOP_50", year: 2026}})
  })

  it("runs the board's writes and reads", async () => {
    vi.mocked(api.annualVoteStandings).mockResolvedValueOnce(answer(api.annualVoteStandings, [aPlacing(1, track.candidate, track)]))
    expect(await loadStandings("OST_TOP_50", 2026)).toHaveLength(1)

    vi.mocked(api.createAnnualVote).mockResolvedValueOnce(answer(api.createAnnualVote, aVote("UPCOMING")))
    expect(await createVote("OST_TOP_50", 2026, dates)).toMatchObject({ok: true})
    expect(api.createAnnualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50"}, body: {year: 2026, ...dates}})

    vi.mocked(api.rescheduleAnnualVote).mockResolvedValueOnce(answer(api.rescheduleAnnualVote, aVote("VOTING")))
    await rescheduleVote("OST_TOP_50", 2026, dates, 2)
    expect(api.rescheduleAnnualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}, body: {...dates, version: 2}})

    vi.mocked(api.mergeVoteTrack).mockResolvedValueOnce(answer(api.mergeVoteTrack, track))
    expect(await mergeTrack(2, 1)).toEqual({ok: true, saved: track})
    expect(api.mergeVoteTrack).toHaveBeenCalledWith({path: {id: 2}, body: {intoId: 1}})
  })

  it("draws a search result's cover through the api, and a stored picture from our storage", () => {
    expect(coverPreviewUrl("co1abc")).toMatch(/\/api\/vote-candidates\/covers\/co1abc$/)
    expect(coverPreviewUrl(null)).toBeNull()
    const drawn = imageAttrs(aVotePicture("vote-covers/c.webp", [{url: "/files/public/vote-covers/c-160.webp", width: 160}]))
    expect(drawn.src).toMatch(/\/api\/files\/public\/vote-covers\/c\.webp$/)
    expect(drawn.srcset).toMatch(/\/api\/files\/public\/vote-covers\/c-160\.webp 160w, .*\/api\/files\/public\/vote-covers\/c\.webp 264w$/)
    expect([drawn.width, drawn.height]).toEqual([264, 352])
    const unsized = imageAttrs({...aVotePicture("x.webp"), width: null, height: null})
    expect(unsized.srcset).toBeUndefined()
    expect(unsized).not.toHaveProperty("width")
    expect(unsized).not.toHaveProperty("height")
  })
})
