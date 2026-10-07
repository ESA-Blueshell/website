/**
 * The public SNTPings page: it paints the canvas and the board for everyone, swaps rows live off
 * the stream, and shows a signed-in member their own status and the opt-in a visitor never sees.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import {VSwitch} from "vuetify/components"
import type {Leaderboard} from "@/domains/pinger"
import type {StoredLogin} from "@/plugins/store"
import SntPings from "@/pages/SntPings.vue"
import {mountPage} from "../helpers/mountPage"
import {settle, unmountAll} from "../helpers/testUtils"

const {mockLoadPaint, mockLoadBoard, mockLoadOptIn, mockSaveOptIn, mockOpenStream} = vi.hoisted(() => ({
  mockLoadPaint: vi.fn(),
  mockLoadBoard: vi.fn(),
  mockLoadOptIn: vi.fn(),
  mockSaveOptIn: vi.fn(),
  mockOpenStream: vi.fn(),
}))

vi.mock("@/domains/pinger", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/pinger")>()
  return {
    ...actual,
    loadPaintJob: mockLoadPaint,
    loadLeaderboard: mockLoadBoard,
    loadOptIn: mockLoadOptIn,
    saveOptIn: mockSaveOptIn,
    openLeaderboardStream: mockOpenStream,
  }
})

const paint = {prefix: null, ratePps: 128, originX: 100, originY: 100, width: 900, height: 720, imageUrl: "/files/public/pinger-paint/art.webp"}

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
    mockLoadOptIn.mockResolvedValue(false)
    mockSaveOptIn.mockResolvedValue({ok: true, saved: true})
    mockOpenStream.mockImplementation((cb: (snapshot: Leaderboard) => void) => {
      streamPush = cb
      return vi.fn()
    })
  })

  afterEach(() => unmountAll(wrappers, "SntPings"))

  it("paints the canvas image and the ranked rows with the house line, for everyone", async () => {
    const wrapper = await mount(null)

    expect(wrapper.find(".snt-box__img").attributes("src")).toContain("/pinger-paint/art.webp")
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

  it("hides the members-only block from a signed-out visitor, and shows the empty state", async () => {
    mockLoadBoard.mockResolvedValue({house: null, members: []})
    const wrapper = await mount(null)

    expect(wrapper.find("[data-testid=snt-member]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=snt-empty]").exists()).toBe(true)
    expect(mockLoadOptIn).not.toHaveBeenCalled()
  })

  it("shows a signed-in member their own standing and the opt-in toggle", async () => {
    const wrapper = await mount(memberLogin("robin"))

    expect(wrapper.get("[data-testid=snt-member]").exists()).toBe(true)
    expect(wrapper.get("[data-testid=snt-mine]").text()).toContain("#2")
    expect(wrapper.find("[data-testid=snt-download]").exists()).toBe(true)
  })

  it("saves a member's choice to appear when they turn the switch on", async () => {
    const wrapper = await mount(memberLogin("robin"))

    wrapper.findComponent(VSwitch).vm.$emit("update:modelValue", true)
    await settle()

    expect(mockSaveOptIn).toHaveBeenCalledWith(true)
  })

  it("reverts the switch and names the reason when the choice is refused", async () => {
    mockSaveOptIn.mockResolvedValue({ok: false, reason: "locked out"})
    const wrapper = await mount(memberLogin("robin"))

    wrapper.findComponent(VSwitch).vm.$emit("update:modelValue", true)
    await settle()

    expect(wrapper.text()).toContain("locked out")
  })

  it("tells an opted-in member with no pings yet that they appear", async () => {
    mockLoadOptIn.mockResolvedValue(true)
    const wrapper = await mount(memberLogin("ghost"))

    expect(wrapper.get("[data-testid=snt-mine]").text()).toContain("You appear on the board")
  })

  it("prompts a member who has not opted in to turn the switch on", async () => {
    mockLoadOptIn.mockResolvedValue(false)
    const wrapper = await mount(memberLogin("ghost"))

    expect(wrapper.get("[data-testid=snt-mine]").text()).toContain("Turn on the switch")
  })
})
