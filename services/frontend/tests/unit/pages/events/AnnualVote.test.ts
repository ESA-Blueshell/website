/**
 * The annual vote page: what a visitor, a member and the board see in each state of a vote, and a
 * member ranking, saving and changing their ballot, for both kinds.
 */
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import type {VueWrapper} from "@vue/test-utils"
import * as api from "@/services/api"
import type {StoredLogin} from "@/plugins/store"
import AnnualVote from "@/pages/events/AnnualVote.vue"
import BallotList from "@/domains/vote/island/BallotList.vue"
import BoardTools from "@/domains/vote/island/BoardTools.vue"
import Timeline from "@/components/island/Timeline.vue"
import SegmentedChoice from "@/components/island/SegmentedChoice.vue"
import VoteDatesDialog from "@/domains/vote/island/VoteDatesDialog.vue"
import {GAME_OF_THE_YEAR, OST_TOP_50, type VoteKindPage} from "@/domains/vote"
import {boardLogin, mountPage} from "../../helpers/mountPage"
import {answer, refusal} from "../../helpers/sdkAnswers"
import {settle, unmountAll} from "../../helpers/testUtils"
import {aGame, aGamesVote, aPick, aPlacing, aTrack, aVote} from "../../domains/vote/fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  annualVote: vi.fn(),
  annualVoteStandings: vi.fn(),
  annualVoteYears: vi.fn(),
  voteSongs: vi.fn(),
  castVoteBallot: vi.fn(),
  currentAnnualVote: vi.fn(),
  myVoteBallot: vi.fn(),
  renameVoteTrack: vi.fn(),
}))

const login = (roles: string[]): StoredLogin => ({
  userId: 7,
  username: "member.paid",
  roles: roles as StoredLogin["roles"],
  twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: true, required: false},
})
const member = login(["MEMBER"])

describe("annual vote page", () => {
  const wrappers: VueWrapper[] = []
  const hollow = aTrack(1, aGame(1, "Hollow Knight"))
  const portal = aTrack(2, aGame(2, "Portal 2"))

  const mount = async (signedIn: StoredLogin | null, page: VoteKindPage = OST_TOP_50, year?: number) => {
    const Page = defineComponent({render: () => h(AnnualVote, {page, year})})
    // The page takes its kind as a prop; a kind switched off has no route, so it mounts at the one that has.
    const wrapper = await mountPage(Page, {path: OST_TOP_50.path, login: signedIn})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  const voteIs = (vote: ReturnType<typeof aVote> | null) =>
    vi.mocked(api.currentAnnualVote).mockResolvedValue(
      vote ? answer(api.currentAnnualVote, vote) : refusal(api.currentAnnualVote, {code: "NoAnnualVote"}, 404),
    )

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.myVoteBallot).mockResolvedValue(
      answer(api.myVoteBallot, {version: 1, submittedAt: "2026-12-02T18:00:00Z", picks: [aPick(hollow), aPick(portal)]}),
    )
    vi.mocked(api.annualVoteStandings).mockResolvedValue(answer(api.annualVoteStandings, []))
    vi.mocked(api.annualVoteYears).mockResolvedValue(answer(api.annualVoteYears, [2026]))
    vi.mocked(api.voteSongs).mockResolvedValue(answer(api.voteSongs, []))
  })

  afterEach(() => unmountAll(wrappers, "AnnualVote"))

  // The page says its dates in the reader's own time; pinning it keeps "21 December" true east of UTC+1.
  beforeAll(() => {
    vi.stubEnv("TZ", "Europe/Amsterdam")
  })
  afterAll(() => {
    vi.unstubAllEnvs()
  })

  it("says there is no vote yet, without the board's tools for a member", async () => {
    voteIs(null)
    const wrapper = await mount(member)

    expect(wrapper.get("[data-testid=vote-none]").exists()).toBe(true)
    expect(wrapper.text()).toContain("no vote yet")
    expect(wrapper.findComponent(BoardTools).exists()).toBe(false)
  })

  it("says when an upcoming vote opens and closes", async () => {
    voteIs(aVote("UPCOMING"))
    const wrapper = await mount(null)

    expect(wrapper.get("[data-testid=vote-upcoming]").text()).toMatch(/Voting opens .*2026 .* and closes 31 December 2026/)
    expect(api.currentAnnualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50"}})
    expect(wrapper.get("[data-testid=vote-opens]").text()).toMatch(/^Opens \d+ Dec/)
    expect(wrapper.get("[data-testid=vote-closes]").text()).toBe("Closes 31 Dec, 17:00")
    expect(wrapper.get("[data-testid=vote-reveals]").text()).toBe("Results 31 Dec, 20:00")
    expect(wrapper.find("[data-testid=vote-ballots]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=vote-help]").exists()).toBe(false)
  })

  it("asks a visitor to log in to vote, and says how it works and the dates", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(null)

    expect(wrapper.text()).toContain("voting has started")
    expect(wrapper.text()).toContain("Every member ranks their 5 favourites.")
    expect(wrapper.text()).toContain("Pick another track for it and the new track takes the old one's place.")
    expect(wrapper.get("[data-testid=vote-closes]").text()).toBe("Closes 31 Dec, 17:00")
    expect(wrapper.get("[data-testid=vote-ballots]").text()).toBe("3 votes")
    expect(wrapper.get("[data-testid=vote-sign-in-link]").attributes("to")).toBe("/login?redirect=%2Fevents%2Fost-top-50")
    expect(api.myVoteBallot).not.toHaveBeenCalled()
  })

  it("tells a signed-in account that is not a member that only members vote", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(login([]))

    expect(wrapper.get("[data-testid=vote-not-member]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=vote-become-member]").attributes("to")).toBe("/membership")
    expect(api.myVoteBallot).not.toHaveBeenCalled()
  })

  it("says a ballot it could not read, and offers nothing to cast over it", async () => {
    voteIs(aVote("VOTING"))
    vi.mocked(api.myVoteBallot).mockResolvedValue(refusal(api.myVoteBallot, null, 500))
    const wrapper = await mount(member)

    expect(wrapper.get("[data-testid=vote-ballot-unread]").text()).toContain("could not be read")
    expect(wrapper.find("[data-testid=vote-save]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=vote-ballot]").exists()).toBe(false)
  })

  it("shows a member their ballot, ranked, with nothing to save until it changes", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(member)

    expect(api.myVoteBallot).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}})
    expect(wrapper.findAll("[data-testid=vote-pick]").map(row => row.text())).toEqual([
      expect.stringContaining("Hollow Knight"),
      expect.stringContaining("Portal 2"),
    ])
    expect(wrapper.get("[data-testid=vote-save]").text()).toBe("Save your top 5")
    expect(wrapper.get("[data-testid=vote-save]").attributes("disabled")).toBeDefined()
    expect(wrapper.get("[data-testid=vote-ballot-head]").text()).toBe("Your top 5")
    expect(wrapper.get("[data-testid=vote-help]").attributes("to")).toBe("/account/help#help-events-annual-vote")
  })

  it("lets a member reorder, drop and add picks, and saves them at the version read", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(member)
    await wrapper.get("[data-testid=vote-pick-down-0]").trigger("click")
    await wrapper.get("[data-testid=vote-pick-remove-0]").trigger("click")
    const added = aTrack(3)
    wrapper.findComponent(BallotList).vm.$emit("pick", aPick(added))
    wrapper.findComponent(BallotList).vm.$emit("reorder", 1, 0)
    wrapper.findComponent(BallotList).vm.$emit("reorder", 1, 0)
    await settle()

    expect(wrapper.findAll("[data-testid=vote-pick]").map(row => row.text())).toEqual([
      expect.stringContaining("Hollow Knight"),
      expect.stringContaining("Game 30"),
    ])
    const saved = {version: 2, submittedAt: "2026-12-03T10:00:00Z", picks: [aPick(hollow), aPick(added)]}
    vi.mocked(api.castVoteBallot).mockResolvedValueOnce(answer(api.castVoteBallot, saved))
    voteIs(aVote("VOTING", {ballots: 4}))
    await wrapper.get("[data-testid=vote-save]").trigger("click")
    await settle()

    expect(api.castVoteBallot).toHaveBeenCalledWith({
      path: {kind: "OST_TOP_50", year: 2026},
      body: {picks: [{candidateId: 1, trackId: 1}, {candidateId: 30, trackId: 3}], version: 1},
    })
    expect(wrapper.get("[data-testid=vote-saved]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=vote-ballots]").text()).toBe("4 votes")
  })

  it("keeps the member's picks and says why a save was refused", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(member)
    await wrapper.get("[data-testid=vote-pick-remove-1]").trigger("click")
    vi.mocked(api.castVoteBallot).mockResolvedValueOnce(refusal(api.castVoteBallot, {code: "WrongVoteState", state: "CLOSED"}, 409))
    await wrapper.get("[data-testid=vote-save]").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid=vote-problem]").text()).toBe("The vote has closed, so that cannot be done now.")
    expect(wrapper.findAll("[data-testid=vote-pick]")).toHaveLength(1)

    wrapper.findComponent(BallotList).vm.$emit("problem", "")
    await settle()
    expect(wrapper.find("[data-testid=vote-problem]").exists()).toBe(false)
  })

  it("starts a first ballot empty, and says when it is full", async () => {
    voteIs(aVote("VOTING"))
    vi.mocked(api.myVoteBallot).mockResolvedValue(answer(api.myVoteBallot, {version: null, submittedAt: null, picks: []}))
    const wrapper = await mount(member)

    expect(wrapper.findAll("[data-testid=vote-free]")).toHaveLength(5)
    expect(wrapper.get("[data-testid=vote-save]").text()).toBe("Cast your vote")
    for (const id of [11, 12, 13, 14, 15]) wrapper.findComponent(BallotList).vm.$emit("pick", aPick(aTrack(id)))
    await settle()
    expect(wrapper.get("[data-testid=vote-ballot-full]").text()).toBe("Your top is full. Take a game off to add another.")
  })

  it("runs a games-only vote under its own name, without tracks in what it says", async () => {
    vi.mocked(api.currentAnnualVote).mockResolvedValue(answer(api.currentAnnualVote, aGamesVote("VOTING", {points: [3, 2, 1]})))
    vi.mocked(api.myVoteBallot).mockResolvedValue(
      answer(api.myVoteBallot, {version: 1, submittedAt: "2026-12-02T18:00:00Z", picks: [{candidate: aGame(1)}, {candidate: aGame(2)}, {candidate: aGame(3)}]}),
    )
    const wrapper = await mount(member, GAME_OF_THE_YEAR)

    expect(api.currentAnnualVote).toHaveBeenCalledWith({path: {kind: "GAME_OF_THE_YEAR"}})
    expect(wrapper.text()).toContain("Game of the Year · 2026")
    expect(wrapper.text()).toContain("Your top 3")
    expect(wrapper.text()).toContain("A game is in your top once. Drag")
    expect(wrapper.text()).not.toContain("track")
    expect(wrapper.get("[data-testid=vote-ballot-full]").text()).toBe("Your top is full. Take a game off to add another.")
  })

  it("reads the other kind's vote when the router reuses the page for it", async () => {
    voteIs(aVote("VOTING"))
    const shown = ref<VoteKindPage>(OST_TOP_50)
    const Page = defineComponent({render: () => h(AnnualVote, {page: shown.value})})
    const wrapper = await mountPage(Page, {path: OST_TOP_50.path, login: member})
    wrappers.push(wrapper)
    await settle()
    expect(wrapper.findAll("[data-testid=vote-pick]")).toHaveLength(2)

    vi.mocked(api.currentAnnualVote).mockResolvedValue(answer(api.currentAnnualVote, aGamesVote("UPCOMING")))
    shown.value = GAME_OF_THE_YEAR
    await settle()

    expect(api.currentAnnualVote).toHaveBeenLastCalledWith({path: {kind: "GAME_OF_THE_YEAR"}})
    expect(wrapper.text()).toContain("Game of the Year · 2026")
    expect(wrapper.findAll("[data-testid=vote-pick]")).toHaveLength(0)
  })

  it("says voting has closed, and when they are revealed", async () => {
    voteIs(aVote("CLOSED", {ballots: 1}))
    const wrapper = await mount(null)

    expect(wrapper.text()).toContain("voting has closed")
    expect(wrapper.get("[data-testid=vote-closed]").text()).toMatch(/after 1 vote\. The results are revealed 31 December 2026/)
    expect(wrapper.get("[data-testid=vote-ballots]").text()).toBe("1 vote")
    expect(wrapper.find("[data-testid=vote-closes]").exists()).toBe(false)
  })

  it("shows the revealed top", async () => {
    voteIs(aVote("REVEALED", {ballots: 30, results: [aPlacing(1, hollow.candidate, hollow, 120, 12), aPlacing(2, portal.candidate, portal, 90, 9)]}))
    const wrapper = await mount(null)

    expect(wrapper.text()).toContain("the results are in")
    expect(wrapper.get("[data-testid=vote-results-head]").text()).toBe("OST Top 50 of 2026")
    expect(wrapper.get("[data-testid=vote-ballots]").text()).toBe("30 votes")
    expect(wrapper.find("[data-testid=vote-reveals]").exists()).toBe(false)
    expect(wrapper.findAll("[data-testid=vote-result]")).toHaveLength(2)
  })

  it("shows a revealed vote's results first, and switches to the member's own top as they cast it", async () => {
    voteIs(aVote("REVEALED", {ballots: 30, results: [aPlacing(1, hollow.candidate, hollow, 120, 12)]}))
    const wrapper = await mount(member)

    expect(api.myVoteBallot).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}})
    expect(wrapper.get("[data-testid=vote-results-head]").text()).toBe("OST Top 50 of 2026")
    expect(wrapper.findAll("[data-testid=vote-result]")).toHaveLength(1)
    wrapper.findComponent(SegmentedChoice).vm.$emit("update:modelValue", "mine")
    await settle()

    expect(wrapper.get("[data-testid=vote-results-head]").text()).toBe("Your top 5")
    const mine = wrapper.get("[data-testid=vote-mine]")
    expect(mine.findAll("[data-testid=vote-pick]")).toHaveLength(2)
    expect(mine.find("[data-testid=vote-free]").exists()).toBe(false)
    expect(mine.find("[data-testid=vote-pick-grip-0]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=vote-result]").exists()).toBe(false)

    vi.mocked(api.myVoteBallot).mockResolvedValue(answer(api.myVoteBallot, {version: null, submittedAt: null, picks: []}))
    const none = await mount(member)
    expect(none.findComponent(SegmentedChoice).exists()).toBe(false)
    expect(none.find("[data-testid=vote-mine]").exists()).toBe(false)
  })

  it("shows a member their own top under a closed vote, which has no results yet", async () => {
    voteIs(aVote("CLOSED"))
    const wrapper = await mount(member)

    expect(wrapper.get("[data-testid=vote-mine]").findAll("[data-testid=vote-pick]")).toHaveLength(2)
  })

  it("lets a member rename the songs they added, in their top and in a place just filled, and says why one could not be", async () => {
    voteIs(aVote("VOTING"))
    vi.mocked(api.myVoteBallot).mockResolvedValue(
      answer(api.myVoteBallot, {version: 1, submittedAt: "2026-12-02T18:00:00Z", picks: [aPick(hollow), aPick(portal)], renamableTrackIds: [1]}),
    )
    const wrapper = await mount(member)
    const list = wrapper.findComponent(BallotList)
    expect(list.props("renamable")).toEqual([1])

    vi.mocked(api.renameVoteTrack).mockResolvedValueOnce(refusal(api.renameVoteTrack, {code: "TrackRenameNotAllowed"}, 403))
    list.vm.$emit("rename", 1, "Mine")
    await settle()
    expect(wrapper.get("[data-testid=vote-problem]").text()).toContain("or the board")

    vi.mocked(api.renameVoteTrack).mockResolvedValueOnce(answer(api.renameVoteTrack, {...hollow, title: "Greenpath"}))
    list.vm.$emit("rename", 1, "Greenpath")
    await settle()
    expect(api.renameVoteTrack).toHaveBeenLastCalledWith({path: {id: 1}, body: {title: "Greenpath"}})
    expect(wrapper.findAll("[data-testid=vote-pick]")[0]!.text()).toContain("Greenpath")
    expect(wrapper.get("[data-testid=vote-save]").attributes("disabled")).toBeDefined()

    list.vm.$emit("added", 9)
    await settle()
    expect(list.props("renamable")).toEqual([1, 9])
  })

  it("lets the board rename every song in its top", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount({...boardLogin, roles: ["BOARD", "MEMBER"] as StoredLogin["roles"]})

    expect(wrapper.findComponent(BallotList).props("renamable")).toEqual(expect.arrayContaining([1, 2]))
  })

  it("draws the years the vote ran as a timeline, oldest first, the current one now, and opens the one pressed", async () => {
    voteIs(aVote("VOTING"))
    vi.mocked(api.annualVoteYears).mockResolvedValue(answer(api.annualVoteYears, [2026, 2025, 2024]))
    const now = await mount(null)
    const line = now.findComponent(Timeline)

    expect(line.props("selectedId")).toBe(2026)
    expect(line.props("stops").map((stop: {label: string; sublabel: string; mark: string}) => [stop.label, stop.sublabel, stop.mark])).toEqual([
      ["2024", "results", ""], ["2025", "results", ""], ["2026", "voting has started", "Now"],
    ])
    const push = vi.spyOn(now.vm.$router, "push")
    line.vm.$emit("select", 2025)
    line.vm.$emit("select", 2026)
    expect(push.mock.calls.map(call => call[0])).toEqual(["/events/ost-top-50/2025", "/events/ost-top-50"])
  })

  it("names a year after the current one as coming, and draws no timeline for a kind with no vote", async () => {
    voteIs(aVote("VOTING"))
    vi.mocked(api.annualVoteYears).mockResolvedValue(answer(api.annualVoteYears, [2027, 2026]))
    expect((await mount(null)).findComponent(Timeline).props("stops")[1].sublabel).toBe("coming")

    voteIs(null)
    vi.mocked(api.annualVoteYears).mockResolvedValue(answer(api.annualVoteYears, []))
    expect((await mount(null)).findComponent(Timeline).exists()).toBe(false)
  })

  it("shows a past year's results, without the board's band", async () => {
    voteIs(aVote("VOTING"))
    vi.mocked(api.annualVote).mockResolvedValue(
      answer(api.annualVote, aVote("REVEALED", {year: 2025, ballots: 9, results: [aPlacing(1, hollow.candidate, hollow, 40, 4)]})),
    )
    const past = await mount({...boardLogin, roles: ["BOARD", "MEMBER"] as StoredLogin["roles"]}, OST_TOP_50, 2025)

    expect(api.annualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2025}})
    expect(past.text()).toContain("OST Top 50 · 2025")
    expect(past.findAll("[data-testid=vote-result]")).toHaveLength(1)
    expect(past.findComponent(BoardTools).exists()).toBe(false)
  })

  it("starts and changes a year's vote in the dialog, from the timeline or the board's band, and reads it again", async () => {
    voteIs(aVote("UPCOMING"))
    vi.mocked(api.annualVoteYears).mockResolvedValue(answer(api.annualVoteYears, [2026, 2025]))
    vi.mocked(api.annualVote).mockResolvedValue(answer(api.annualVote, aVote("REVEALED", {year: 2025})))
    const wrapper = await mount({...boardLogin, roles: ["BOARD", "MEMBER"] as StoredLogin["roles"]})
    const dialog = wrapper.findComponent(VoteDatesDialog)
    const line = wrapper.findComponent(Timeline)
    expect(line.props("mayEdit")).toBe(true)

    wrapper.findComponent(BoardTools).vm.$emit("edit")
    await settle()
    expect(dialog.props("open")).toBe(true)
    expect(dialog.props("vote").year).toBe(2026)

    line.vm.$emit("edit", 2025)
    await settle()
    expect(api.annualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2025}})
    expect(dialog.props("vote").state).toBe("REVEALED")

    vi.mocked(api.annualVote).mockResolvedValueOnce(refusal(api.annualVote, {code: "AnnualVoteNotFound"}, 404))
    dialog.vm.$emit("update:open", false)
    line.vm.$emit("edit", 2019)
    await settle()
    expect(dialog.props("open")).toBe(false)

    // This year already has its vote, so starting one opens it: one vote a year.
    line.vm.$emit("add")
    await settle()
    expect(dialog.props("vote").year).toBe(2026)

    vi.mocked(api.annualVoteYears).mockResolvedValue(answer(api.annualVoteYears, [2026, 2025]))
    dialog.vm.$emit("changed", aVote("VOTING"))
    await settle()
    expect(api.myVoteBallot).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}})
    expect(wrapper.findAll("[data-testid=vote-pick]")).toHaveLength(2)
  })

  it("starts this year's vote from the board's band where the kind has none", async () => {
    voteIs(null)
    vi.mocked(api.annualVoteYears).mockResolvedValue(answer(api.annualVoteYears, []))
    const wrapper = await mount({...boardLogin, roles: ["BOARD", "MEMBER"] as StoredLogin["roles"]})

    wrapper.findComponent(BoardTools).vm.$emit("start")
    await settle()
    expect(wrapper.findComponent(VoteDatesDialog).props("year")).toBe(new Date().getFullYear())

    wrapper.findComponent(VoteDatesDialog).vm.$emit("changed", aVote("UPCOMING"))
    await settle()
    expect(wrapper.text()).toContain("coming soon")
  })

})
