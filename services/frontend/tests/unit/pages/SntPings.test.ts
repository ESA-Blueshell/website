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
  house: {label: "SiteCie", online: true, totalSent: 9000},
  members: [
    {memberId: 1, rank: 1, totalSent: 50, online: true, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
    {memberId: 2, rank: 2, totalSent: 20, online: false, discordTag: null, avatarUrl: null, username: "robin"},
  ],
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

  it("swaps the rows live when the stream pushes a reordered snapshot", async () => {
    const wrapper = await mount(null)
    expect(wrapper.findAll("[data-testid=snt-row]")[0].text()).toContain("ace#1")

    streamPush!({
      house: board.house,
      members: [
        {memberId: 2, rank: 1, totalSent: 80, online: true, discordTag: null, avatarUrl: null, username: "robin"},
        {memberId: 1, rank: 2, totalSent: 50, online: true, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
      ],
    })
    await settle()

    expect(wrapper.findAll("[data-testid=snt-row]")[0].text()).toContain("robin")
  })

  it("shows the empty state and hides the members-only block from a signed-out visitor", async () => {
    mockLoadBoard.mockResolvedValue({house: null, members: []})
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
    expect(text).toContain("chmod +x on the AppImage")
  })

  it("keeps the download block out of a signed-out visitor's view", async () => {
    const wrapper = await mount(null)

    expect(wrapper.find("[data-testid=snt-download]").exists()).toBe(false)
  })

  it("reads the canvas as painting when a member is online but the house is not", async () => {
    mockLoadBoard.mockResolvedValue({
      house: {label: "SiteCie", online: false, totalSent: 100},
      members: [{memberId: 1, rank: 1, totalSent: 50, online: true, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null}],
    })
    const wrapper = await mount(null)

    expect(wrapper.text()).toContain("Painting now")
  })

  it("scrolls down to the live canvas when the hero's watch button is pressed", async () => {
    const wrapper = await mount(null)
    const scroll = vi.fn()
    vi.spyOn(document, "getElementById").mockReturnValue({scrollIntoView: scroll} as unknown as HTMLElement)

    await wrapper.get("[data-testid=snt-watch]").trigger("click")

    expect(scroll).toHaveBeenCalled()
  })
})
