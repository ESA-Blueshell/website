import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import * as api from "@/services/api"
import SearchPicker from "@/components/island/SearchPicker.vue"
import PickFinder from "@/domains/vote/island/PickFinder.vue"
import {answer, refusal} from "../../../helpers/sdkAnswers"
import {aGame, aGamesVote, aResult, aTrack, aVote} from "../fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  addVoteTrack: vi.fn(),
  searchVoteCandidates: vi.fn(),
  storeVoteCandidate: vi.fn(),
  voteCandidateTracks: vi.fn(),
}))

describe("the vote pick finder", () => {
  const hollow = aResult(14593, "Hollow Knight")
  const portal = {...aResult(72, "Portal 2", null), firstReleaseYear: null}
  const known = aTrack(1, aGame(1, "Hollow Knight"))

  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    vi.mocked(api.searchVoteCandidates).mockResolvedValue(answer(api.searchVoteCandidates, [hollow, portal]))
    vi.mocked(api.voteCandidateTracks).mockResolvedValue(answer(api.voteCandidateTracks, [known]))
  })

  afterEach(() => vi.useRealTimers())

  const searched = async (vote = aVote("VOTING"), term = "o") => {
    const wrapper = mount(PickFinder, {props: {vote}})
    const picker = wrapper.findComponent(SearchPicker)
    picker.vm.$emit("search", term)
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    return {wrapper, picker}
  }

  const chosen = async (vote = aVote("VOTING"), key = "14593") => {
    const found = await searched(vote)
    found.picker.vm.$emit("pick", key)
    await flushPromises()
    return found
  }

  it("asks the catalogue for the vote's kind and year once typing settles, covers drawn through the api", async () => {
    const {wrapper, picker} = await searched(aVote("VOTING"), "hol")

    expect(api.searchVoteCandidates).toHaveBeenCalledWith({query: {query: "hol", kind: "OST_TOP_50", year: 2026}})
    expect(picker.props("options")).toEqual([
      {key: "14593", label: "Hollow Knight", note: "2017", avatar: expect.stringMatching(/\/api\/vote-candidates\/covers\/co14593$/)},
      {key: "72", label: "Portal 2", note: undefined, avatar: undefined},
    ])
    expect(picker.props("placeholder")).toBe("Search for a game")
    expect(wrapper.find("[data-testid=vote-tracks]").exists()).toBe(false)
  })

  it("keeps the newest search's answer when an older one comes back last", async () => {
    type Search = ReturnType<typeof api.searchVoteCandidates>
    let answerOld: (value: Awaited<Search>) => void = () => {}
    vi.mocked(api.searchVoteCandidates)
      .mockImplementationOnce(() => new Promise<Awaited<Search>>(resolve => { answerOld = resolve }) as Search)
      .mockResolvedValueOnce(answer(api.searchVoteCandidates, [portal]))
    const wrapper = mount(PickFinder, {props: {vote: aVote("VOTING")}})
    const picker = wrapper.findComponent(SearchPicker)
    picker.vm.$emit("search", "h")
    await vi.advanceTimersByTimeAsync(250)
    picker.vm.$emit("search", "po")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    answerOld(answer(api.searchVoteCandidates, [hollow]))
    await flushPromises()

    expect(picker.props("options").map((option: {label: string}) => option.label)).toEqual(["Portal 2"])
  })

  it("lists a picked game's tracks, and picking one hands the game and track up and starts over", async () => {
    const {wrapper} = await chosen()

    expect(api.voteCandidateTracks).toHaveBeenCalledWith({path: {igdbId: 14593}})
    expect(wrapper.get("[data-testid=vote-tracks]").text()).toContain("Tracks members picked for Hollow Knight")
    expect(wrapper.get(".finder__thumb").attributes("src")).toMatch(/\/api\/files\/public\/vote-thumbnails\/1\.webp$/)
    await wrapper.get("[data-testid=vote-track-pick-1]").trigger("click")

    expect(wrapper.emitted("pick")).toEqual([[{candidate: known.candidate, track: known}]])
    expect(wrapper.find("[data-testid=vote-tracks]").exists()).toBe(false)
  })

  it("says nobody picked a track yet, and draws a track without a stored thumbnail", async () => {
    vi.mocked(api.voteCandidateTracks).mockResolvedValueOnce(answer(api.voteCandidateTracks, []))
    const {wrapper, picker} = await chosen(aVote("VOTING"), "72")
    expect(wrapper.text()).toContain("Nobody picked a track for Portal 2 yet.")

    vi.mocked(api.voteCandidateTracks).mockResolvedValueOnce(answer(api.voteCandidateTracks, [{...known, thumbnailPath: null}]))
    picker.vm.$emit("pick", "14593")
    await flushPromises()
    expect(wrapper.find(".finder__thumb--none").exists()).toBe(true)
  })

  it("forgets a game the picker no longer offers, and a cleared choice", async () => {
    const {wrapper, picker} = await chosen(aVote("VOTING"), "999")
    expect(wrapper.find("[data-testid=vote-tracks]").exists()).toBe(false)

    picker.vm.$emit("pick", "14593")
    await flushPromises()
    picker.vm.$emit("clear")
    await flushPromises()
    expect(wrapper.find("[data-testid=vote-tracks]").exists()).toBe(false)
  })

  it("adds a pasted link as the game's track, or says why it could not", async () => {
    const {wrapper} = await chosen()

    await wrapper.get("form").trigger("submit")
    expect(api.addVoteTrack).not.toHaveBeenCalled()

    vi.mocked(api.addVoteTrack).mockResolvedValueOnce(refusal(api.addVoteTrack, {code: "UnsupportedTrackLink"}))
    await wrapper.get("input[data-testid=vote-link]").setValue(" https://example.com ")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(api.addVoteTrack).toHaveBeenCalledWith({body: {igdbId: 14593, url: "https://example.com", kind: "OST_TOP_50", year: 2026}})
    expect(wrapper.get("[data-testid=vote-finder-problem]").text()).toBe("Paste a link to a YouTube video or a Spotify track.")

    const added = aTrack(5, aGame(1, "Hollow Knight"))
    vi.mocked(api.addVoteTrack).mockResolvedValueOnce(answer(api.addVoteTrack, added))
    await wrapper.get("input[data-testid=vote-link]").setValue("https://youtu.be/dQw4w9WgXcQ")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.emitted("pick")).toEqual([[{candidate: added.candidate, track: added}]])
  })

  it("in a games-only vote, offers the year's games and puts a game on the ballot without a track", async () => {
    const goty = aGamesVote("VOTING")
    const {wrapper, picker} = await chosen(goty)

    expect(picker.props("placeholder")).toBe("Search for a game released in 2026")
    expect(api.voteCandidateTracks).not.toHaveBeenCalled()
    expect(wrapper.find("[data-testid=vote-tracks]").exists()).toBe(false)

    vi.mocked(api.storeVoteCandidate).mockResolvedValueOnce(refusal(api.storeVoteCandidate, {code: "GameSearchUnavailable"}, 503))
    await wrapper.get("[data-testid=vote-game-pick]").trigger("click")
    await flushPromises()
    expect(wrapper.get("[data-testid=vote-finder-problem]").text()).toContain("unavailable")

    const stored = aGame(7, "Hollow Knight")
    vi.mocked(api.storeVoteCandidate).mockResolvedValueOnce(answer(api.storeVoteCandidate, stored))
    await wrapper.get("[data-testid=vote-game-pick]").trigger("click")
    await flushPromises()
    expect(api.storeVoteCandidate).toHaveBeenLastCalledWith({body: {igdbId: 14593, kind: "GAME_OF_THE_YEAR", year: 2026}})
    expect(wrapper.emitted("pick")).toEqual([[{candidate: stored, track: null}]])
  })

  it("stops a pending search when it goes away", async () => {
    const wrapper = mount(PickFinder, {props: {vote: aVote("VOTING")}})
    wrapper.findComponent(SearchPicker).vm.$emit("search", "hol")
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(250)

    expect(api.searchVoteCandidates).not.toHaveBeenCalled()
  })
})
