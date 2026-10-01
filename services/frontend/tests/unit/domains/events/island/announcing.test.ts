import {describe, expect, it} from "vitest"
import {DateTime} from "luxon"
import {announceNeed, morningSaid, nextMorning, useAnnouncePrompt} from "@/domains/events/island/announcing"
import {AnnounceChoice} from "@/services/api"

const amsterdam = (local: string) => DateTime.fromISO(local, {zone: "Europe/Amsterdam"})

describe("when an approved event's events-info post goes out", () => {
  it("waits for today's 08:00 Amsterdam time before it, and tomorrow's from 08:00 on", () => {
    expect(nextMorning(amsterdam("2026-10-01T06:30")).toISO()).toBe(amsterdam("2026-10-01T08:00").toISO())
    expect(nextMorning(amsterdam("2026-10-01T08:00")).toISO()).toBe(amsterdam("2026-10-02T08:00").toISO())
    expect(nextMorning(amsterdam("2026-10-01T14:00")).toISO()).toBe(amsterdam("2026-10-02T08:00").toISO())
    // The clocks go back on 25 October: that morning is 07:00 UTC, the one before 06:00.
    expect(nextMorning(amsterdam("2026-10-24T14:00")).toUTC().toISO()).toBe("2026-10-25T07:00:00.000Z")
  })

  it("asks nothing once the post is out, posts at once before the next morning, and asks otherwise", () => {
    const now = amsterdam("2026-10-01T14:00")
    expect(announceNeed({announced: true, startTime: "2026-12-01T20:00:00Z"}, now)).toBe("none")
    expect(announceNeed({announced: false, startTime: amsterdam("2026-10-01T20:00").toISO()!}, now)).toBe("now")
    expect(announceNeed({startTime: amsterdam("2026-10-02T09:00").toISO()!}, now)).toBe("ask")
  })

  it("says the later choice in the viewer's own time", () => {
    expect(morningSaid(amsterdam("2026-10-01T06:30"))).toBe("Post today at 08:00")
    expect(morningSaid(amsterdam("2026-10-01T14:00"))).toBe("Post tomorrow at 08:00")
    // 08:00 in Amsterdam is 07:00 in London.
    expect(morningSaid(DateTime.fromISO("2026-10-01T13:00", {zone: "Europe/London"}))).toBe("Post tomorrow at 07:00")
  })

  it("answers what the board picked, nothing where there is nothing to pick, and null on cancel", async () => {
    const prompt = useAnnouncePrompt()
    const later = DateTime.now().plus({days: 30}).toISO()!

    expect(await prompt.ask({announced: true, startTime: later})).toBeUndefined()
    expect(await prompt.ask({startTime: DateTime.now().plus({minutes: 1}).toISO()!})).toBe(AnnounceChoice.NOW)

    const picked = prompt.ask({startTime: later})
    expect(prompt.open.value).toBe(true)
    expect(prompt.later.value).toMatch(/^Post (today|tomorrow) at \d\d:\d\d$/)
    prompt.answer(AnnounceChoice.NEXT_MORNING)
    expect(await picked).toBe(AnnounceChoice.NEXT_MORNING)
    expect(prompt.open.value).toBe(false)

    const cancelled = prompt.ask({startTime: later})
    prompt.answer(null)
    expect(await cancelled).toBeNull()
  })
})
