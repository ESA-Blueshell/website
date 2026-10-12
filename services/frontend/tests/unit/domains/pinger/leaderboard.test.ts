/**
 * The leaderboard adapter: what it reads off the api, what it falls back to when a read is
 * refused, and how it finds the signed-in member's own row.
 */
import {beforeEach, describe, expect, it, vi} from "vitest"
import {EMPTY_LEADERBOARD, loadLeaderboard, ownStanding, type Leaderboard} from "@/domains/pinger"

const {mockBoard} = vi.hoisted(() => ({
  mockBoard: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, board: mockBoard}
})

const board: Leaderboard = {
  house: {label: "SiteCie", totalSent: 9000, peakPps: 900, peakAt: "2026-10-09T19:14:00Z"},
  members: [
    {memberId: 1, rank: 1, totalSent: 50, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
    {memberId: 2, rank: 2, totalSent: 20, discordTag: null, avatarUrl: null, username: "robin"},
  ],
  fastest: [
    {memberId: 1, rank: 1, peakPps: 400, peakAt: "2026-10-09T19:10:00Z", discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
  ],
  record: {pps: 1_300, at: "2026-10-09T19:14:00Z"},
}

describe("the leaderboard adapter", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("reads the board, and falls back to an empty board when the read is refused", async () => {
    mockBoard.mockResolvedValue({status: 200, data: board})
    expect(await loadLeaderboard()).toEqual(board)

    mockBoard.mockResolvedValue({error: {detail: "down"}, data: null})
    expect(await loadLeaderboard()).toEqual(EMPTY_LEADERBOARD)
  })

  it("finds the member's own row by the username the api prints, and nothing where there is none", () => {
    expect(ownStanding(board, "robin")?.memberId).toBe(2)
    expect(ownStanding(board, "ace#1")).toBeNull()
    expect(ownStanding(board, null)).toBeNull()
  })
})
