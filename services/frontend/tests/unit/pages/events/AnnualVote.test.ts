/**
 * The annual vote page: what a visitor, a member and the board see in each state of a vote, and a
 * member ranking, saving and changing their ballot, for both kinds.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import type {VueWrapper} from "@vue/test-utils"
import * as api from "@/services/api"
import type {StoredLogin} from "@/plugins/store"
import AnnualVote from "@/pages/events/AnnualVote.vue"
import PickFinder from "@/domains/vote/island/PickFinder.vue"
import BoardTools from "@/domains/vote/island/BoardTools.vue"
import {GAME_OF_THE_YEAR, OST_TOP_50, type VoteKindPage} from "@/domains/vote"
import {boardLogin, mountPage} from "../../helpers/mountPage"
import {answer, refusal} from "../../helpers/sdkAnswers"
import {settle, unmountAll} from "../../helpers/testUtils"
import {aGame, aGamesVote, aPick, aPlacing, aTrack, aVote} from "../../domains/vote/fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  annualVoteStandings: vi.fn(),
  castVoteBallot: vi.fn(),
  currentAnnualVote: vi.fn(),
  myVoteBallot: vi.fn(),
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

  const mount = async (signedIn: StoredLogin | null, page: VoteKindPage = OST_TOP_50) => {
    const Page = defineComponent({render: () => h(AnnualVote, {page})})
    const wrapper = await mountPage(Page, {path: page.path, login: signedIn})
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
  })

  afterEach(() => unmountAll(wrappers, "AnnualVote"))

  it("says there is no vote yet, without the board's tools for a member", async () => {
    voteIs(null)
    const wrapper = await mount(member)

    expect(wrapper.get("[data-testid=vote-none]").exists()).toBe(true)
    expect(wrapper.text()).toContain("coming soon")
    expect(wrapper.findComponent(BoardTools).exists()).toBe(false)
  })

  it("says when an upcoming vote opens and closes", async () => {
    voteIs(aVote("UPCOMING"))
    const wrapper = await mount(null)

    expect(wrapper.get("[data-testid=vote-upcoming]").text()).toMatch(/Voting opens .*2026 .* and closes 21 December 2026/)
    expect(api.currentAnnualVote).toHaveBeenCalledWith({path: {kind: "OST_TOP_50"}})
  })

  it("asks a visitor to log in to vote, and says the scoring and the dates", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(null)

    expect(wrapper.text()).toContain("voting is open")
    expect(wrapper.text()).toContain("Your first pick is worth 12 points, then 10, 8, 6 and 4.")
    expect(wrapper.text()).toContain("pick another track for it and it takes the old one's place")
    expect(wrapper.text()).toContain("Voting closes 21 December 2026")
    expect(wrapper.get("[data-testid=vote-sign-in-link]").attributes("to")).toBe("/login?redirect=/events/ost-top-50")
    expect(api.myVoteBallot).not.toHaveBeenCalled()
  })

  it("tells a signed-in account that is not a member that only members vote", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(login([]))

    expect(wrapper.get("[data-testid=vote-not-member]").exists()).toBe(true)
    expect(api.myVoteBallot).not.toHaveBeenCalled()
  })

  it("shows a member their ballot, ranked, with nothing to save until it changes", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(member)

    expect(api.myVoteBallot).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}})
    expect(wrapper.findAll("[data-testid=vote-pick]").map(row => row.text())).toEqual([
      expect.stringContaining("Hollow Knight"),
      expect.stringContaining("Portal 2"),
    ])
    expect(wrapper.get("[data-testid=vote-save]").text()).toBe("Save your ballot")
    expect(wrapper.get("[data-testid=vote-save]").attributes("disabled")).toBeDefined()
  })

  it("lets a member reorder, drop and add picks, and saves them at the version read", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(member)
    await wrapper.get("[data-testid=vote-pick-down-0]").trigger("click")
    await wrapper.get("[data-testid=vote-pick-remove-0]").trigger("click")
    const added = aTrack(3)
    wrapper.findComponent(PickFinder).vm.$emit("pick", aPick(added))
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
      body: {picks: [{gameId: 1, trackId: 1}, {gameId: 30, trackId: 3}], version: 1},
    })
    expect(wrapper.get("[data-testid=vote-saved]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=vote-ballot-head]").text()).toContain("4")
  })

  it("keeps the member's picks and says why a save was refused", async () => {
    voteIs(aVote("VOTING"))
    const wrapper = await mount(member)
    await wrapper.get("[data-testid=vote-pick-remove-1]").trigger("click")
    vi.mocked(api.castVoteBallot).mockResolvedValueOnce(refusal(api.castVoteBallot, {code: "WrongVoteState", state: "COUNTING"}, 409))
    await wrapper.get("[data-testid=vote-save]").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid=vote-problem]").text()).toBe("The vote has closed, so that cannot be done now.")
    expect(wrapper.findAll("[data-testid=vote-pick]")).toHaveLength(1)
  })

  it("starts a first ballot empty, and says when it is full", async () => {
    voteIs(aVote("VOTING"))
    vi.mocked(api.myVoteBallot).mockResolvedValue(refusal(api.myVoteBallot, null, 403))
    const wrapper = await mount(member)

    expect(wrapper.get("[data-testid=vote-ballot-empty]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=vote-save]").text()).toBe("Cast your ballot")
    for (const id of [11, 12, 13, 14, 15]) wrapper.findComponent(PickFinder).vm.$emit("pick", aPick(aTrack(id)))
    await settle()
    expect(wrapper.get("[data-testid=vote-ballot-full]").text()).toContain("or pick a new track")
  })

  it("runs a games-only vote under its own name, without tracks in what it says", async () => {
    vi.mocked(api.currentAnnualVote).mockResolvedValue(answer(api.currentAnnualVote, aGamesVote("VOTING", {points: [3, 2, 1]})))
    vi.mocked(api.myVoteBallot).mockResolvedValue(
      answer(api.myVoteBallot, {version: 1, submittedAt: "2026-12-02T18:00:00Z", picks: [{game: aGame(1)}, {game: aGame(2)}, {game: aGame(3)}]}),
    )
    const wrapper = await mount(member, GAME_OF_THE_YEAR)

    expect(api.currentAnnualVote).toHaveBeenCalledWith({path: {kind: "GAME_OF_THE_YEAR"}})
    expect(wrapper.text()).toContain("Game of the Year · 2026")
    expect(wrapper.text()).toContain("Rank your top 3")
    expect(wrapper.text()).toContain("Your first pick is worth 3 points, then 2 and 1. A game can be on your ballot once.")
    expect(wrapper.get("[data-testid=vote-ballot-full]").text()).toBe("Your ballot is full. Take a game off to add another.")
  })

  it("says the votes are being counted, and when they are revealed", async () => {
    voteIs(aVote("COUNTING", {ballots: 12}))
    const wrapper = await mount(null)

    expect(wrapper.text()).toContain("the votes are being counted")
    expect(wrapper.get("[data-testid=vote-counting]").text()).toMatch(/after 12 ballots\. The results are revealed 28 December 2026/)
  })

  it("shows the revealed top", async () => {
    voteIs(aVote("PUBLISHED", {ballots: 30, results: [aPlacing(1, hollow.game, hollow, 120, 12), aPlacing(2, portal.game, portal, 90, 9)]}))
    const wrapper = await mount(null)

    expect(wrapper.text()).toContain("the results are in")
    expect(wrapper.get("[data-testid=vote-results-head]").text()).toContain("From 30 ballots")
    expect(wrapper.findAll("[data-testid=vote-result]")).toHaveLength(2)
  })

  it("gives the board its tools, and reads the member's ballot again once the board's change opens voting", async () => {
    voteIs(aVote("UPCOMING"))
    const wrapper = await mount({...boardLogin, roles: ["BOARD", "MEMBER"] as StoredLogin["roles"]})
    const tools = wrapper.findComponent(BoardTools)
    expect(tools.props("year")).toBe(new Date().getFullYear())
    expect(tools.props("kind")).toBe("OST_TOP_50")

    tools.vm.$emit("changed", aVote("VOTING"))
    await settle()

    expect(api.myVoteBallot).toHaveBeenCalledWith({path: {kind: "OST_TOP_50", year: 2026}})
    expect(wrapper.findAll("[data-testid=vote-pick]")).toHaveLength(2)
  })
})
