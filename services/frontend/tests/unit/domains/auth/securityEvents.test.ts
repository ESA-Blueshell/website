import {describe, expect, it} from "vitest"
import {DateTime} from "luxon"
import {formatSecurityMoment, sayCount, securityLogByDay} from "@/domains/auth"

const now = DateTime.fromISO("2026-09-24T15:00:00")

describe("a moment in the log's words", () => {
  it("is today or yesterday at a time, and the day further back", () => {
    expect(formatSecurityMoment("2026-09-24T09:12:00", now)).toBe("today at 09:12")
    expect(formatSecurityMoment("2026-09-23T22:40:00", now)).toBe("yesterday at 22:40")
    expect(formatSecurityMoment("2026-09-14T08:00:00", now)).toBe("Mon 14 Sep")
  })

  it("names the days the log is grouped by the same way", () => {
    const event = (id: number, occurredAt: string) => ({id, kind: "SIGNED_IN", actorKind: "PERSON", occurredAt}) as never
    const days = securityLogByDay([event(3, "2026-09-24T09:00:00"), event(2, "2026-09-23T09:00:00"), event(1, "2026-09-14T09:00:00")], now)

    expect(days.map(day => day.name)).toEqual(["Today", "Yesterday", "Mon 14 Sep"])
  })
})

describe("a count in words", () => {
  it("is singular for one", () => {
    expect(sayCount(1, "sign-in")).toBe("1 sign-in")
    expect(sayCount(3, "sign-in")).toBe("3 sign-ins")
  })
})
