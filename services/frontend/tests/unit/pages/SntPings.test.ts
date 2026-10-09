/**
 * The public SNTPings page: it paints the canvas and the board for everyone, swaps rows live off
 * the stream, and shows a signed-in member the members-only block with the per-platform download.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import type {Leaderboard} from "@/domains/pinger"
import type {StoredLogin} from "@/plugins/store"
import SntPings from "@/pages/SntPings.vue"
import {mountPage} from "../helpers/mountPage"
import {settle, unmountAll} from "../helpers/testUtils"

const {mockLoadPaint, mockLoadBoard, mockOpenStream} = vi.hoisted(() => ({
  mockLoadPaint: vi.fn(),
  mockLoadBoard: vi.fn(),
  mockOpenStream: vi.fn(),
}))

vi.mock("@/domains/pinger", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/pinger")>()
  return {
    ...actual,
    loadPaintJob: mockLoadPaint,
    loadLeaderboard: mockLoadBoard,
    openLeaderboardStream: mockOpenStream,
  }
})

const paint = {
  prefix: null,
  ratePps: 128,
  siteCieEnabled: true,
  placements: [{id: 1, imageUrl: "/files/public/pinger-paint/art.webp", originX: 100, originY: 100, width: 900, height: 720}],
}

const board: Leaderboard = {
  house: {label: "SiteCie", online: true, totalSent: 9000, pps: 3000, peakPps: 9000, peakAt: "2026-10-09T19:02:00Z"},
  members: [
    {memberId: 1, rank: 1, totalSent: 50, online: true, pps: 400, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
    {memberId: 2, rank: 2, totalSent: 20, online: false, pps: 0, discordTag: null, avatarUrl: null, username: "robin"},
  ],
  fastest: [
    {memberId: 2, rank: 1, peakPps: 2_000_000, peakAt: "2026-10-09T19:10:00Z", discordTag: null, avatarUrl: null, username: "robin"},
    {memberId: 1, rank: 2, peakPps: 900, peakAt: "2026-10-09T19:05:00Z", discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
  ],
  record: {pps: 2_400_000, at: "2026-10-09T19:14:00Z"},
  combinedPps: 3_400,
}

const memberLogin = (username: string): StoredLogin => ({
  userId: 7,
  username,
  roles: ["MEMBER"] as StoredLogin["roles"],
  twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: true, required: false},
})

describe("SNTPings page", () => {
  const wrappers: VueWrapper[] = []
  let streamPush: ((snapshot: Leaderboard) => void) | null = null

  const mount = async (login: StoredLogin | null) => {
    const wrapper = await mountPage(SntPings, {path: "/sntpings", login})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    streamPush = null
    mockLoadPaint.mockResolvedValue(paint)
    mockLoadBoard.mockResolvedValue(board)
    mockOpenStream.mockImplementation((cb: (snapshot: Leaderboard) => void) => {
      streamPush = cb
      return vi.fn()
    })
  })

  afterEach(() => unmountAll(wrappers, "SntPings"))

  it("paints the canvas image and the ranked rows with the house line, for everyone", async () => {
    const wrapper = await mount(null)

    expect(wrapper.find(".target__dim").attributes("src")).toContain("/pinger-paint/art.webp")
    expect(wrapper.get("[data-testid=snt-house]").text()).toContain("SiteCie")

    const rows = wrapper.findAll("[data-testid=snt-row]")
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain("ace#1")
    expect(rows[0].find("img").attributes("src")).toBe("https://cdn/ace.png")
    expect(rows[1].text()).toContain("robin")
  })

  it("shows the fastest board next to the total board, and the combined record above them", async () => {
    const wrapper = await mount(memberLogin("robin"))

    const fastest = wrapper.get("[data-testid=snt-board-fastest]")
    expect(fastest.text()).toContain("Fastest")
    const rows = fastest.findAll("[data-testid=snt-fastest-row]")
    expect(rows.map(row => row.text())).toEqual([expect.stringContaining("robin"), expect.stringContaining("ace#1")])
    expect(rows[0].find("[data-testid=snt-fastest-you]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=snt-board-total]").findAll("[data-testid=snt-row]")).toHaveLength(2)
    expect(wrapper.get("[data-testid=snt-record-best]").text()).toContain("2.4M pings a second")
    expect(wrapper.get("[data-testid=snt-record-now]").text()).toContain("3.4K pings a second")
  })

  it("reorders the fastest board when the stream pushes a new peak", async () => {
    const wrapper = await mount(null)

    streamPush!({...board, fastest: [{...board.fastest[1], rank: 1, peakPps: 3_000_000}, {...board.fastest[0], rank: 2}]})
    await settle()

    expect(wrapper.findAll("[data-testid=snt-fastest-row]")[0].text()).toContain("ace#1")
  })

  it("swaps the rows live when the stream pushes a reordered snapshot", async () => {
    const wrapper = await mount(null)
    expect(wrapper.findAll("[data-testid=snt-row]")[0].text()).toContain("ace#1")

    streamPush!({
      ...board,
      members: [
        {memberId: 2, rank: 1, totalSent: 80, online: true, discordTag: null, avatarUrl: null, username: "robin"},
        {memberId: 1, rank: 2, totalSent: 50, online: true, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
      ],
    })
    await settle()

    expect(wrapper.findAll("[data-testid=snt-row]")[0].text()).toContain("robin")
  })

  it("shows the empty state and hides the members-only block from a signed-out visitor", async () => {
    mockLoadBoard.mockResolvedValue({house: null, members: [], fastest: [], record: null, combinedPps: 0})
    const wrapper = await mount(null)

    expect(wrapper.find("[data-testid=snt-member]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=snt-empty]").exists()).toBe(true)
  })

  it("shows a signed-in member the members-only block with the download", async () => {
    const wrapper = await mount(memberLogin("robin"))

    expect(wrapper.get("[data-testid=snt-member]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=snt-permission]").text()).toContain("listed on the leaderboards")
    expect(wrapper.find("[data-testid=snt-download]").exists()).toBe(true)
  })

  it("offers a member a download per platform with its open steps, pointed at the member endpoint", async () => {
    const wrapper = await mount(memberLogin("robin"))

    const buttons = wrapper.findAll("[data-testid=snt-download]")
    expect(buttons.map(b => b.attributes("data-os"))).toEqual(["macos", "windows", "linux"])

    const hrefs = buttons.map(b => b.attributes("href"))
    expect(hrefs[0]).toContain("/pinger/app/download?os=macos")
    expect(hrefs[1]).toContain("/pinger/app/download?os=windows")
    expect(hrefs[2]).toContain("/pinger/app/download?os=linux")

    const text = wrapper.get("[data-testid=snt-member]").text()
    expect(text).toContain("Right-click the app and choose Open")
    expect(text).toContain("More info then Run anyway")
    expect(text).toContain("Protection history in Windows Security")
    expect(text).toContain("chmod +x on the file")
  })

  it("keeps the download block out of a signed-out visitor's view", async () => {
    const wrapper = await mount(null)

    expect(wrapper.find("[data-testid=snt-download]").exists()).toBe(false)
  })

  it("counts down to the event start while it is still ahead", async () => {
    vi.useFakeTimers({toFake: ["Date"]})
    vi.setSystemTime(new Date("2025-12-05T15:30:00+01:00")) // 2h30 before the 18:00 CET start
    try {
      const wrapper = await mount(null)
      const el = wrapper.get("[data-testid=snt-countdown]")
      expect(el.text()).toContain("Event starts in")
      expect(el.text()).toContain("02:30:00")
    } finally {
      vi.useRealTimers()
    }
  })

  it("hides the countdown once the event has started", async () => {
    vi.useFakeTimers({toFake: ["Date"]})
    vi.setSystemTime(new Date("2025-12-05T18:00:01+01:00")) // one second past the start
    try {
      const wrapper = await mount(null)
      expect(wrapper.find("[data-testid=snt-countdown]").exists()).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it("reads the canvas as painting when a member is online but the house is not", async () => {
    mockLoadBoard.mockResolvedValue({
      ...board,
      house: {label: "SiteCie", online: false, totalSent: 100},
      members: [{memberId: 1, rank: 1, totalSent: 50, online: true, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null}],
    })
    const wrapper = await mount(null)

    expect(wrapper.text()).toContain("Painting now")
  })

  it("reads the rate off the board rather than the growth of the totals", async () => {
    const wrapper = await mount(null)
    const pps = () => wrapper.get("[data-testid=snt-progress-pps]").text()
    expect(pps()).toBe("3,400")

    // A member's report lands a ten-second jump in its total; the rate stays what the board says.
    streamPush?.({...board, members: [{...board.members[0], totalSent: 4_000_050}, board.members[1]]})
    await settle()

    expect(pps()).toBe("3,400")
  })

  it("sends the hero's watch button to the SNTPings site in a new tab", async () => {
    const wrapper = await mount(null)

    const watch = wrapper.get("[data-testid=snt-watch]")
    expect(watch.attributes("href")).toBe("https://pings.utwente.io")
    expect(watch.attributes("target")).toBe("_blank")
    expect(watch.attributes("rel")).toBe("noopener")
  })
})
