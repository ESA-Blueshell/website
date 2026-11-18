import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import * as api from "@/services/api"
import SearchPicker from "@/components/island/SearchPicker.vue"
import PlaceEditor from "@/domains/vote/island/PlaceEditor.vue"
import {answer, refusal} from "../../../helpers/sdkAnswers"
import {aGame, aGamesVote, aSearchHit, aTrack, aVote} from "../fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  addVoteTrack: vi.fn(),
  searchVoteCandidates: vi.fn(),
  storeVoteCandidate: vi.fn(),
  voteCandidateTracks: vi.fn(),
}))

describe("the place editor", () => {
  const hollow = aSearchHit(14593, "Hollow Knight")
  const portal = {...aSearchHit(72, "Portal 2", null), firstReleaseYear: null}
  const known = aTrack(1, aGame(1, "Hollow Knight"))

  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    vi.mocked(api.searchVoteCandidates).mockResolvedValue(answer(api.searchVoteCandidates, [hollow, portal]))
    vi.mocked(api.voteCandidateTracks).mockResolvedValue(answer(api.voteCandidateTracks, [known]))
  })

  afterEach(() => vi.useRealTimers())

  const searched = async (vote = aVote("VOTING"), term = "o") => {
    const wrapper = mount(PlaceEditor, {props: {vote}})
    const picker = wrapper.findAllComponents(SearchPicker)[0]!
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
    expect(picker.props("emptyNote")).toBe("No game matches that.")
    expect(wrapper.findAllComponents(SearchPicker)[1]!.props("disabled")).toBe(true)
  })

  it("says why a search was refused, and forgets it once a search answers", async () => {
    vi.mocked(api.searchVoteCandidates).mockResolvedValueOnce(refusal(api.searchVoteCandidates, {code: "GameSearchUnavailable"}, 503))
    const wrapper = mount(PlaceEditor, {props: {vote: aVote("VOTING")}})
    const picker = wrapper.findAllComponents(SearchPicker)[0]!
    picker.vm.$emit("search", "hol")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    expect(wrapper.emitted("problem")!.at(-1)![0]).toContain("unavailable")
    expect(picker.props("options")).toEqual([])
  })

  it("keeps the newest search's answer when an older one comes back last", async () => {
    type Search = ReturnType<typeof api.searchVoteCandidates>
    let answerOld: (value: Awaited<Search>) => void = () => {}
    vi.mocked(api.searchVoteCandidates)
      .mockImplementationOnce(() => new Promise<Awaited<Search>>(resolve => { answerOld = resolve }) as Search)
      .mockResolvedValueOnce(answer(api.searchVoteCandidates, [portal]))
    const wrapper = mount(PlaceEditor, {props: {vote: aVote("VOTING")}})
    const picker = wrapper.findAllComponents(SearchPicker)[0]!
    picker.vm.$emit("search", "h")
    await vi.advanceTimersByTimeAsync(250)
    picker.vm.$emit("search", "po")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    answerOld(answer(api.searchVoteCandidates, [hollow]))
    await flushPromises()

    expect(picker.props("options").map((option: {label: string}) => option.label)).toEqual(["Portal 2"])
  })

  const song = (wrapper: ReturnType<typeof mount>) => wrapper.findAllComponents(SearchPicker)[1]!

  it("offers a picked game's songs, narrowed by what is typed, and picking one hands the game and song up", async () => {
    const {wrapper} = await chosen()

    expect(api.voteCandidateTracks).toHaveBeenCalledWith({path: {igdbId: 14593}})
    expect(song(wrapper).props("disabled")).toBe(false)
    expect(song(wrapper).props("options")).toEqual([{key: "1", label: known.title, note: "YouTube"}])
    expect(song(wrapper).props("emptyNote")).toContain("No song matches")
    song(wrapper).vm.$emit("search", "nothing like it")
    await flushPromises()
    expect(song(wrapper).props("options")).toEqual([])

    song(wrapper).vm.$emit("pick", "1")
    await flushPromises()
    expect(wrapper.emitted("pick")).toEqual([[{candidate: known.candidate, track: known}]])
    expect(song(wrapper).props("disabled")).toBe(true)
  })

  it("names a Spotify song as such, and says nobody added a song yet", async () => {
    vi.mocked(api.voteCandidateTracks).mockResolvedValueOnce(answer(api.voteCandidateTracks, [{...known, source: "SPOTIFY"}]))
    const {wrapper, picker} = await chosen()
    expect(song(wrapper).props("options")[0].note).toBe("Spotify")

    vi.mocked(api.voteCandidateTracks).mockResolvedValueOnce(answer(api.voteCandidateTracks, []))
    picker.vm.$emit("pick", "72")
    await flushPromises()
    expect(song(wrapper).props("emptyNote")).toContain("Nobody added a song")
  })

  it("forgets a game the picker no longer offers, a cleared choice and a song it does not know", async () => {
    const {wrapper, picker} = await chosen(aVote("VOTING"), "999")
    expect(song(wrapper).props("disabled")).toBe(true)
    expect(song(wrapper).props("emptyNote")).toBe("")

    picker.vm.$emit("pick", "14593")
    await flushPromises()
    song(wrapper).vm.$emit("pick", "404")
    await flushPromises()
    expect(wrapper.emitted("pick")).toBeUndefined()

    picker.vm.$emit("pick", "14593")
    await flushPromises()
    picker.vm.$emit("clear")
    await flushPromises()
    expect(song(wrapper).props("disabled")).toBe(true)
  })

  it("offers a pasted link as a new song, and adds it or says why it could not", async () => {
    const {wrapper} = await chosen()

    song(wrapper).vm.$emit("search", " https://example.com ")
    await flushPromises()
    expect(song(wrapper).props("options")).toEqual([{key: "link", label: "Add https://example.com", note: "New song"}])

    vi.mocked(api.addVoteTrack).mockResolvedValueOnce(refusal(api.addVoteTrack, {code: "UnsupportedTrackLink"}))
    song(wrapper).vm.$emit("pick", "link")
    await flushPromises()
    expect(api.addVoteTrack).toHaveBeenCalledWith({body: {igdbId: 14593, url: "https://example.com", kind: "OST_TOP_50", year: 2026}})
    expect(wrapper.emitted("problem")!.at(-1)).toEqual(["Paste a link to a YouTube video or a Spotify track."])

    const added = aTrack(5, aGame(1, "Hollow Knight"))
    vi.mocked(api.addVoteTrack).mockResolvedValueOnce(answer(api.addVoteTrack, added))
    song(wrapper).vm.$emit("search", "https://youtu.be/dQw4w9WgXcQ")
    await flushPromises()
    song(wrapper).vm.$emit("pick", "link")
    await flushPromises()
    expect(wrapper.emitted("pick")).toEqual([[{candidate: added.candidate, track: added}]])
  })

  it("in a games-only vote, offers the year's games and puts a picked game in the top without a song", async () => {
    const goty = aGamesVote("VOTING")
    vi.mocked(api.storeVoteCandidate).mockResolvedValueOnce(refusal(api.storeVoteCandidate, {code: "GameSearchUnavailable"}, 503))
    const {wrapper, picker} = await chosen(goty)

    expect(picker.props("placeholder")).toBe("Search games released in 2026")
    expect(wrapper.findAllComponents(SearchPicker)).toHaveLength(1)
    expect(api.voteCandidateTracks).not.toHaveBeenCalled()
    expect(wrapper.emitted("problem")!.at(-1)![0]).toContain("unavailable")

    const stored = aGame(7, "Hollow Knight")
    vi.mocked(api.storeVoteCandidate).mockResolvedValueOnce(answer(api.storeVoteCandidate, stored))
    picker.vm.$emit("pick", "14593")
    await flushPromises()
    expect(api.storeVoteCandidate).toHaveBeenLastCalledWith({body: {igdbId: 14593, kind: "GAME_OF_THE_YEAR", year: 2026}})
    expect(wrapper.emitted("pick")).toEqual([[{candidate: stored, track: null}]])
  })

  it("stops a pending search when it goes away", async () => {
    const wrapper = mount(PlaceEditor, {props: {vote: aVote("VOTING")}})
    wrapper.findAllComponents(SearchPicker)[0]!.vm.$emit("search", "hol")
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(250)

    expect(api.searchVoteCandidates).not.toHaveBeenCalled()
  })
})
