import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import * as api from "@/services/api"
import SearchPicker from "@/components/island/SearchPicker.vue"
import BoardSongs from "@/domains/vote/island/BoardSongs.vue"
import {answer, refusal} from "../../../helpers/sdkAnswers"
import {aGame, aTrack} from "../fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  mergeVoteTrack: vi.fn(),
  renameVoteTrack: vi.fn(),
  voteSongs: vi.fn(),
}))

describe("the board's songs", () => {
  const celeste = aGame(1, "Celeste")
  const resurrections = aTrack(11, celeste)
  const firstSteps = aTrack(12, celeste, "SPOTIFY")
  const greenpath = aTrack(21, aGame(2, "Hollow Knight"))

  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    vi.mocked(api.voteSongs).mockResolvedValue(answer(api.voteSongs, [resurrections, firstSteps, greenpath]))
  })

  afterEach(() => vi.useRealTimers())

  const songs = async () => {
    const wrapper = mount(BoardSongs, {attachTo: document.body})
    await flushPromises()
    return wrapper
  }

  it("lists every song game by game, with its service, and a way to merge only where a game has another", async () => {
    const wrapper = await songs()

    expect(api.voteSongs).toHaveBeenCalledWith({query: {query: ""}})
    expect(wrapper.get("[data-testid=vote-songs-game-1]").text()).toContain("Celeste")
    expect(wrapper.get("[data-testid=vote-songs-song-12]").text()).toContain("Spotify")
    expect(wrapper.get("[data-testid=vote-songs-song-11]").get("a").attributes("href")).toBe(resurrections.url)
    expect(wrapper.get("[data-testid=vote-songs-song-11]").findComponent(SearchPicker).props("options")).toEqual([{key: "12", label: "Track 12"}])
    expect(wrapper.get("[data-testid=vote-songs-song-21]").findComponent(SearchPicker).exists()).toBe(false)
    wrapper.unmount()
  })

  it("finds songs by what is typed once typing settles, and says when nothing matches or nobody added one", async () => {
    const wrapper = await songs()

    vi.mocked(api.voteSongs).mockResolvedValue(answer(api.voteSongs, []))
    await wrapper.get("input[data-testid=vote-songs-search]").setValue("zzz")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(api.voteSongs).toHaveBeenLastCalledWith({query: {query: "zzz"}})
    expect(wrapper.get("[data-testid=vote-songs-none]").text()).toBe("No song or game matches that.")

    await wrapper.get("input[data-testid=vote-songs-search]").setValue("")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(wrapper.get("[data-testid=vote-songs-none]").text()).toBe("Nobody added a song yet.")
    wrapper.unmount()
  })

  it("says the songs could not be read", async () => {
    vi.mocked(api.voteSongs).mockResolvedValue(refusal(api.voteSongs, null, 500))
    const wrapper = await songs()

    expect(wrapper.get("[data-testid=vote-songs-unread]").exists()).toBe(true)
    wrapper.unmount()
  })

  it("renames and merges a song, reading the list again, and says why either was refused", async () => {
    const wrapper = await songs()
    const rename = async (title: string) => {
      await wrapper.get("[data-testid=vote-songs-title-11]").trigger("click")
      await wrapper.get("[data-testid=vote-songs-title-11-field]").setValue(title)
      await wrapper.get("[data-testid=vote-songs-title-11-field]").trigger("keydown", {key: "Enter"})
      await flushPromises()
    }
    const merge = async () => {
      wrapper.get("[data-testid=vote-songs-song-11]").findComponent(SearchPicker).vm.$emit("pick", "12")
      await flushPromises()
    }

    vi.mocked(api.renameVoteTrack).mockResolvedValueOnce(refusal(api.renameVoteTrack, {code: "TrackRenameNotAllowed"}, 403))
    await rename("Mine")
    expect(wrapper.get("[data-testid=vote-songs-problem]").text()).toContain("or the board")

    vi.mocked(api.renameVoteTrack).mockResolvedValueOnce(answer(api.renameVoteTrack, {...resurrections, title: "Resurrections"}))
    await rename("Resurrections")
    expect(api.renameVoteTrack).toHaveBeenLastCalledWith({path: {id: 11}, body: {title: "Resurrections"}})
    expect(api.voteSongs).toHaveBeenCalledTimes(2)

    vi.mocked(api.mergeVoteTrack).mockResolvedValueOnce(refusal(api.mergeVoteTrack, {code: "TrackMergeInvalid"}))
    await merge()
    expect(wrapper.get("[data-testid=vote-songs-problem]").text()).toContain("same game")

    vi.mocked(api.mergeVoteTrack).mockResolvedValueOnce(answer(api.mergeVoteTrack, firstSteps))
    await merge()
    expect(api.mergeVoteTrack).toHaveBeenLastCalledWith({path: {id: 11}, body: {intoId: 12}})
    expect(api.voteSongs).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it("stops a pending search when it goes away", async () => {
    const wrapper = await songs()
    await wrapper.get("input[data-testid=vote-songs-search]").setValue("cel")
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(250)

    expect(api.voteSongs).toHaveBeenCalledTimes(1)
  })
})
