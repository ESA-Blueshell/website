import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import Log from "@/pages/login/security/Log.vue"
import {mountInApp, settle} from "../../helpers"

const {mockAuth} = vi.hoisted(() => ({mockAuth: {readMySecurityLog: vi.fn()}}))

vi.mock("@/domains/auth", async (importOriginal) => ({...(await importOriginal<Record<string, unknown>>()), ...mockAuth}))

vi.mock("@/components/common/AccountFrame.vue", async () => ({default: (await import("./stubs")).frameStub}))

const entry = (id: number, kind: string, occurredAt: string, extra: Record<string, unknown> = {}) =>
  ({id, kind, actorKind: "PERSON", occurredAt, browser: "Firefox", platform: "Linux", ...extra})

const open = async () => {
  const wrapper = mountInApp(Log)
  await settle()
  return wrapper
}

describe("the security log page", () => {
  beforeEach(() => {
    vi.useFakeTimers({toFake: ["Date"]})
    vi.setSystemTime(new Date("2026-09-24T15:00:00"))
    mockAuth.readMySecurityLog.mockResolvedValueOnce({
      events: [
        entry(4, "SIGNED_IN", "2026-09-24T09:12:00"),
        entry(3, "ROLES_CHANGED", "2026-09-23T14:30:00", {actorName: "Ro Admin", browser: null, platform: null}),
        entry(2, "TWO_FACTOR_RESET", "2026-09-12T20:04:00", {actorKind: "OPERATOR", note: "lost the phone"}),
      ],
      page: 0,
      totalPages: 2,
      totalElements: 4,
    }).mockResolvedValueOnce({
      events: [entry(1, "PASSWORD_CHANGED", "2026-09-12T19:58:00")],
      page: 1,
      totalPages: 2,
      totalElements: 4,
    })
  })

  afterEach(() => vi.useRealTimers())

  it("groups the log by day, naming who made a change that was not the person", async () => {
    const wrapper = await open()

    expect(wrapper.findAll("[data-testid=security-log-day]").map(day => day.text())).toEqual(["Today", "Yesterday", "Sat 12 Sep"])
    const entries = wrapper.findAll("[data-testid=security-log-entry]")
    expect(entries.map(one => one.find(".log__what").text())).toEqual(["Signed in", "Roles changed", "Two-factor reset"])
    expect(entries[0]!.text()).toContain("09:12")
    expect(entries[0]!.text()).toContain("Firefox on Linux")
    expect(entries[1]!.text()).toContain("by Ro Admin")
    expect(entries[2]!.text()).toContain("by an operator · lost the phone")
  })

  it("reads a page at a time, adding the older one under the same day", async () => {
    const wrapper = await open()

    await wrapper.get("[data-testid=security-log-older-btn]").trigger("click")
    await settle()

    expect(mockAuth.readMySecurityLog).toHaveBeenLastCalledWith(1)
    expect(wrapper.findAll("[data-testid=security-log-entry]")).toHaveLength(4)
    expect(wrapper.findAll("[data-testid=security-log-day]")).toHaveLength(3)
    expect(wrapper.find("[data-testid=security-log-older-btn]").exists()).toBe(false)
  })

  it("says so when there is nothing to show", async () => {
    mockAuth.readMySecurityLog.mockReset().mockResolvedValue(null)
    const wrapper = await open()

    expect(wrapper.get("[data-testid=security-log]").text()).toContain("Nothing in the last twelve months.")
  })
})
