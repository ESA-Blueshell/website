import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import * as api from "@/services/api"
import BoardTools from "@/domains/vote/island/BoardTools.vue"
import type {AnnualVote} from "@/domains/vote"
import {answer, refusal} from "../../../helpers/sdkAnswers"
import {aGame, aGamesVote, aPlacing, aTrack, aVote} from "../fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  annualVoteStandings: vi.fn(),
  mergeVoteTrack: vi.fn(),
  renameVoteTrack: vi.fn(),
  voteCandidateTracks: vi.fn(),
}))

describe("the board's vote tools", () => {
  const hollow = aGame(1, "Hollow Knight")
  const leading = aTrack(1, hollow)
  const duplicate = aTrack(2, hollow)

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.annualVoteStandings).mockResolvedValue(answer(api.annualVoteStandings, [aPlacing(1, hollow, leading, 22, 2)]))
    vi.mocked(api.voteCandidateTracks).mockResolvedValue(answer(api.voteCandidateTracks, [leading, duplicate]))
  })

  const tools = async (vote: AnnualVote | null) => {
    const wrapper = mount(BoardTools, {props: {vote}})
    await flushPromises()
    return wrapper
  }

  it("asks to start a vote where there is none or the last is revealed, and to change the current one otherwise", async () => {
    const none = await tools(null)
    await none.get("[data-testid=vote-board-start]").trigger("click")
    expect(none.emitted("start")).toHaveLength(1)
    expect(none.find("[data-testid=vote-standings]").exists()).toBe(false)

    expect((await tools(aVote("REVEALED"))).find("[data-testid=vote-board-start]").exists()).toBe(true)
    const open = await tools(aVote("VOTING"))
    await open.get("[data-testid=vote-board-edit]").trigger("click")
    expect(open.emitted("edit")).toHaveLength(1)
  })

  it("shows the standings so far, and none once revealed", async () => {
    const wrapper = await tools(aVote("VOTING"))
    expect(wrapper.get("[data-testid=vote-standing]").text()).toContain("22 pts · 2 voters")
    expect(wrapper.text()).toContain("merge a duplicate track")

    vi.mocked(api.annualVoteStandings).mockResolvedValueOnce(answer(api.annualVoteStandings, []))
    expect((await tools(aVote("CLOSED"))).text()).toContain("No ballots yet.")

    vi.mocked(api.annualVoteStandings).mockResolvedValueOnce(refusal(api.annualVoteStandings, null, 500))
    const unread = await tools(aVote("CLOSED"))
    expect(unread.get("[data-testid=vote-standings-unread]").text()).toContain("could not be read")
    expect(unread.text()).not.toContain("No ballots yet.")

    await wrapper.setProps({vote: aVote("REVEALED")})
    await flushPromises()
    expect(wrapper.find("[data-testid=vote-standings]").exists()).toBe(false)
  })

  it("lists a games-only vote's standings without tracks to merge", async () => {
    vi.mocked(api.annualVoteStandings).mockResolvedValueOnce(answer(api.annualVoteStandings, [aPlacing(1, hollow, null)]))
    const wrapper = await tools(aGamesVote("VOTING"))

    expect(wrapper.text()).not.toContain("merge a duplicate track")
    expect(wrapper.get("[data-testid=vote-standing-open-100]").attributes("disabled")).toBeDefined()
  })

  it("renames any song of an opened game, and says why one could not be", async () => {
    const wrapper = await tools(aVote("VOTING"))
    await wrapper.get("[data-testid=vote-standing-open-100]").trigger("click")
    await flushPromises()
    const rename = async (title: string) => {
      await wrapper.get("[data-testid=vote-track-title-2]").trigger("click")
      await wrapper.get("[data-testid=vote-track-title-2-field]").setValue(title)
      await wrapper.get("[data-testid=vote-track-title-2-field]").trigger("keydown", {key: "Enter"})
      await flushPromises()
    }

    vi.mocked(api.renameVoteTrack).mockResolvedValueOnce(refusal(api.renameVoteTrack, {code: "TrackRenameNotAllowed"}, 403))
    await rename("Mine")
    expect(wrapper.get("[data-testid=vote-board-problem]").text()).toContain("or the board")

    vi.mocked(api.renameVoteTrack).mockResolvedValueOnce(answer(api.renameVoteTrack, {...duplicate, title: "Greenpath"}))
    await rename("Greenpath")
    expect(api.renameVoteTrack).toHaveBeenLastCalledWith({path: {id: 2}, body: {title: "Greenpath"}})
    expect(wrapper.find("[data-testid=vote-board-problem]").exists()).toBe(false)
    expect(api.voteCandidateTracks).toHaveBeenCalledTimes(2)
  })

  it("merges a duplicate track into the one leading its game, and says why one cannot", async () => {
    const wrapper = await tools(aVote("CLOSED"))

    await wrapper.get("[data-testid=vote-standing-open-100]").trigger("click")
    await flushPromises()
    expect(wrapper.find("[data-testid=vote-merge-1]").exists()).toBe(false)
    expect(wrapper.text()).toContain("leading")

    vi.mocked(api.mergeVoteTrack).mockResolvedValueOnce(refusal(api.mergeVoteTrack, {code: "TrackMergeInvalid"}))
    await wrapper.get("[data-testid=vote-merge-2]").trigger("click")
    await flushPromises()
    expect(wrapper.get("[data-testid=vote-board-problem]").text()).toContain("same game")

    vi.mocked(api.mergeVoteTrack).mockResolvedValueOnce(answer(api.mergeVoteTrack, leading))
    vi.mocked(api.voteCandidateTracks).mockResolvedValueOnce(answer(api.voteCandidateTracks, [leading]))
    await wrapper.get("[data-testid=vote-merge-2]").trigger("click")
    await flushPromises()
    expect(api.mergeVoteTrack).toHaveBeenLastCalledWith({path: {id: 2}, body: {intoId: 1}})
    expect(wrapper.find("[data-testid=vote-merge-2]").exists()).toBe(false)
    expect(api.annualVoteStandings).toHaveBeenCalledTimes(2)

    await wrapper.get("[data-testid=vote-standing-open-100]").trigger("click")
    expect(wrapper.find(".standing__tracks").exists()).toBe(false)
  })
})
