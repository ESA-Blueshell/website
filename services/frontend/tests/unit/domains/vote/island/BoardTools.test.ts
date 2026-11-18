import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import * as api from "@/services/api"
import DateTimeInput from "@/components/island/DateTimeInput.vue"
import BoardTools from "@/domains/vote/island/BoardTools.vue"
import {isoOf, type AnnualVote} from "@/domains/vote"
import {answer, refusal} from "../../../helpers/sdkAnswers"
import {aGame, aGamesVote, aPlacing, aTrack, aVote} from "../fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  annualVoteStandings: vi.fn(),
  createAnnualVote: vi.fn(),
  mergeVoteTrack: vi.fn(),
  rescheduleAnnualVote: vi.fn(),
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

  const tools = async (vote: AnnualVote | null, year = 2026) => {
    const wrapper = mount(BoardTools, {props: {kind: vote?.kind ?? "OST_TOP_50", vote, year}})
    await flushPromises()
    return wrapper
  }

  const fields = (wrapper: Awaited<ReturnType<typeof tools>>) => wrapper.findAllComponents(DateTimeInput)

  it("starts this year's vote before the first, on December's dates unless changed", async () => {
    const started = aVote("UPCOMING")
    vi.mocked(api.createAnnualVote).mockResolvedValueOnce(answer(api.createAnnualVote, started))
    const wrapper = await tools(null)

    expect(fields(wrapper).map(field => field.props("modelValue"))).toEqual(["2026-12-01T00:00", "2026-12-21T23:59", "2026-12-28T20:00"])
    expect(wrapper.get("[data-testid=vote-board-save]").text()).toBe("Start the 2026 vote")
    expect(wrapper.find("[data-testid=vote-standings]").exists()).toBe(false)
    fields(wrapper)[2]!.vm.$emit("update:modelValue", "2026-12-30T20:00")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(api.createAnnualVote).toHaveBeenCalledWith({
      path: {kind: "OST_TOP_50"},
      body: {year: 2026, opensAt: isoOf("2026-12-01T00:00"), closesAt: isoOf("2026-12-21T23:59"), revealAt: isoOf("2026-12-30T20:00")},
    })
    expect(wrapper.emitted("changed")).toEqual([[started]])
  })

  it("starts a new year's vote once last year's is revealed, and not before", async () => {
    expect((await tools(aVote("REVEALED", {year: 2025}))).get("[data-testid=vote-board-save]").text()).toBe("Start the 2026 vote")
    expect((await tools(aVote("REVEALED"))).find("[data-testid=vote-board-dates]").exists()).toBe(false)
  })

  it("moves an unrevealed vote's dates at the version it read, shown in Amsterdam time", async () => {
    const vote = aVote("VOTING")
    vi.mocked(api.rescheduleAnnualVote).mockResolvedValueOnce(answer(api.rescheduleAnnualVote, vote))
    const wrapper = await tools(vote)

    expect(wrapper.get("[data-testid=vote-board-save]").text()).toBe("Save the dates")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(api.rescheduleAnnualVote).toHaveBeenCalledWith({
      path: {kind: "OST_TOP_50", year: 2026},
      body: {opensAt: vote.opensAt, closesAt: vote.closesAt, revealAt: vote.revealAt, version: 2},
    })
    expect(wrapper.emitted("changed")).toEqual([[vote]])
  })

  it("asks for all three dates, and says why a save was refused", async () => {
    const wrapper = await tools(aVote("UPCOMING"))
    fields(wrapper)[0]!.vm.$emit("update:modelValue", "")
    await wrapper.get("form").trigger("submit")
    expect(wrapper.get("[data-testid=vote-board-problem]").text()).toBe("Fill in all three dates.")
    expect(api.rescheduleAnnualVote).not.toHaveBeenCalled()

    vi.mocked(api.rescheduleAnnualVote).mockResolvedValueOnce(refusal(api.rescheduleAnnualVote, {code: "VoteDatesOutOfOrder"}))
    fields(wrapper)[0]!.vm.$emit("update:modelValue", "2026-12-25T00:00")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=vote-board-problem]").text()).toContain("opens before it closes")
    expect(wrapper.emitted("changed")).toBeUndefined()
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
