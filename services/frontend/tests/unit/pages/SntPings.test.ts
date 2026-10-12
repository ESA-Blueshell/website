/**
 * The public SNTPings page after the event: the final record, the total sent and both boards, for
 * everyone, with a signed-in member's own row marked.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import type {Leaderboard} from "@/domains/pinger"
import type {StoredLogin} from "@/plugins/store"
import SntPings from "@/pages/SntPings.vue"
import {mountPage} from "../helpers/mountPage"
import {settle, unmountAll} from "../helpers/testUtils"

const {mockLoadBoard} = vi.hoisted(() => ({
  mockLoadBoard: vi.fn(),
}))

vi.mock("@/domains/pinger", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/pinger")>()
  return {...actual, loadLeaderboard: mockLoadBoard}
})

const board: Leaderboard = {
  house: {label: "SiteCie", totalSent: 9000, peakPps: 9000, peakAt: "2026-10-09T19:02:00Z"},
  members: [
    {memberId: 1, rank: 1, totalSent: 50, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
    {memberId: 2, rank: 2, totalSent: 20, discordTag: null, avatarUrl: null, username: "robin"},
  ],
  fastest: [
    {memberId: 2, rank: 1, peakPps: 2_000_000, peakAt: "2026-10-09T19:10:00Z", discordTag: null, avatarUrl: null, username: "robin"},
    {memberId: 1, rank: 2, peakPps: 900, peakAt: "2026-10-09T19:05:00Z", discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
  ],
  record: {pps: 2_400_000, at: "2026-10-09T19:14:00Z"},
}

const memberLogin = (username: string): StoredLogin => ({
  userId: 7,
  username,
  roles: ["MEMBER"] as StoredLogin["roles"],
  twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: true, required: false},
})

describe("SNTPings page", () => {
  const wrappers: VueWrapper[] = []

  const mount = async (login: StoredLogin | null) => {
    const wrapper = await mountPage(SntPings, {path: "/sntpings", login})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockLoadBoard.mockResolvedValue(board)
  })

  afterEach(() => unmountAll(wrappers, "SntPings"))

  it("shows the ranked rows with the house line, for everyone", async () => {
    const wrapper = await mount(null)

    expect(wrapper.get("[data-testid=snt-house]").text()).toContain("SiteCie")
    const rows = wrapper.findAll("[data-testid=snt-row]")
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain("ace#1")
    expect(rows[0].find("img").attributes("src")).toBe("https://cdn/ace.png")
    expect(rows[1].text()).toContain("robin")
  })

  it("shows the fastest board next to the total board, and the record and the total above them", async () => {
    const wrapper = await mount(memberLogin("robin"))

    const fastest = wrapper.get("[data-testid=snt-board-fastest]")
    expect(fastest.text()).toContain("Fastest")
    const rows = fastest.findAll("[data-testid=snt-fastest-row]")
    expect(rows.map(row => row.text())).toEqual([expect.stringContaining("robin"), expect.stringContaining("ace#1")])
    expect(rows[0].find("[data-testid=snt-fastest-you]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=snt-board-total]").findAll("[data-testid=snt-row]")).toHaveLength(2)
    expect(wrapper.get("[data-testid=snt-record-best]").text()).toContain("2.4M pings a second")
    expect(wrapper.get("[data-testid=snt-record-total]").text()).toBe("9.1K pings")
  })

  it("shows the empty state when nobody made the board", async () => {
    mockLoadBoard.mockResolvedValue({house: null, members: [], fastest: [], record: null})
    const wrapper = await mount(null)

    expect(wrapper.find("[data-testid=snt-empty]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=snt-record-total]").text()).toBe("0 pings")
  })
})
