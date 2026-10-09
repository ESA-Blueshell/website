/**
 * The leaderboard adapter: what it reads off the api, what it falls back to when a read is
 * refused, the choice it writes back, and the live stream it opens and closes.
 */
import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  EMPTY_LEADERBOARD,
  loadLeaderboard,
  openLeaderboardStream,
  ownStanding,
  type Leaderboard,
} from "@/domains/pinger"

const {mockBoard} = vi.hoisted(() => ({
  mockBoard: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, board: mockBoard}
})

const board: Leaderboard = {
  house: {label: "SiteCie", online: true, totalSent: 9000},
  members: [
    {memberId: 1, rank: 1, totalSent: 50, online: true, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
    {memberId: 2, rank: 2, totalSent: 20, online: false, discordTag: null, avatarUrl: null, username: "robin"},
  ],
}

class FakeEventSource {
  public static instances: FakeEventSource[] = []
  public onmessage: ((event: MessageEvent<string>) => void) | null = null
  public closed = false

  public constructor(public url: string) {
    FakeEventSource.instances.push(this)
  }

  public close(): void {
    this.closed = true
  }

  public send(data: string): void {
    this.onmessage?.(new MessageEvent("message", {data}))
  }
}

describe("the leaderboard adapter", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    FakeEventSource.instances = []
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

  it("opens the stream, paints each snapshot, ignores a bad frame, and closes on the handle", () => {
    vi.stubGlobal("EventSource", FakeEventSource)
    const seen: Leaderboard[] = []
    const close = openLeaderboardStream((snapshot) => seen.push(snapshot))
    const source = FakeEventSource.instances[0]

    source.send(JSON.stringify(board))
    source.send("not json")
    expect(seen).toEqual([board])

    close()
    expect(source.closed).toBe(true)
    vi.unstubAllGlobals()
  })
})
