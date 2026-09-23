import {describe, expect, it} from "vitest"
import {DateTime} from "luxon"
import {directionsOf, monthsOf, placesOf, posterOf, signUpStateOf, soonOf, whenOf} from "@/domains/events/island/eventFacts"

const event = (over: Record<string, unknown> = {}) => ({
  id: 1,
  title: "LAN",
  startTime: "2026-09-22T19:00:00",
  endTime: "2026-09-22T22:00:00",
  signUp: true,
  signUpCount: 6,
  signUpLimit: 24,
  membersOnly: false,
  approved: true,
  ...over,
}) as never

const now = DateTime.fromISO("2026-09-21T12:00:00")

describe("what the events page says about an event", () => {
  it("writes the day and its hours, or the day it runs into", () => {
    expect(whenOf(event())).toEqual({day: "Tue 22 September", hours: "19:00-22:00"})
    expect(whenOf(event({endTime: "2026-09-24T02:00:00"})).hours).toBe("19:00 to Thu 24 September, 02:00")
  })

  it("says how soon: today, tomorrow, in so many days, or nothing far off", () => {
    expect(soonOf(event({startTime: "2026-09-21T19:00:00"}), now)).toBe("Today")
    expect(soonOf(event(), now)).toBe("Tomorrow")
    expect(soonOf(event({startTime: "2026-09-25T19:00:00"}), now)).toBe("In 4 days")
    expect(soonOf(event({startTime: "2026-10-30T19:00:00"}), now)).toBe("")
    expect(soonOf(event({startTime: "2026-09-21T19:00:00"}))).toBeTypeOf("string")
  })

  it("says how full: places taken of a limit, heads without one, or nothing to take", () => {
    expect(placesOf(event())).toEqual({said: "6 of 24 taken", taken: 0.25})
    expect(placesOf(event({signUpCount: 30})).taken).toBe(1)
    expect(placesOf(event({signUpLimit: null}))).toEqual({said: "6 going"})
    expect(placesOf(event({signUp: false}))).toEqual({said: "No sign-ups, just walk in"})
  })

  it("says whether sign-ups are open, closing, closed or full", () => {
    expect(signUpStateOf(event({signUp: false}), now)).toBe("No sign-ups, just walk in")
    expect(signUpStateOf(event({signUpDeadline: "2026-09-20T12:00:00"}), now)).toBe("Sign-ups closed")
    expect(signUpStateOf(event({signUpCount: 24}), now)).toBe("Full")
    expect(signUpStateOf(event({signUpDeadline: "2026-09-22T12:00:00"}), now)).toBe("Sign-ups close Tue 22 Sep")
    expect(signUpStateOf(event(), now)).toBe("Sign-ups open")
    expect(signUpStateOf(event({signUpLimit: null}))).toBe("Sign-ups open")
  })

  it("leads to the Discord for an event held there, and a map for anywhere else", () => {
    expect(directionsOf("Our Discord")).toBe("https://discord.gg/23YMFQy")
    expect(directionsOf("Esports Lounge Twente")).toBe("https://www.google.com/maps/search/?api=1&query=Esports%20Lounge%20Twente")
  })

  it("groups events by the month they start in, keeping their order", () => {
    const months = monthsOf([event({id: 1}), event({id: 2, startTime: "2026-09-30T19:00:00"}), event({id: 3, startTime: "2026-10-02T19:00:00"})])

    expect(months.map(one => [one.name, one.events.length])).toEqual([["September 2026", 2], ["October 2026", 1]])
  })

  it("resolves the poster's paths against the api, and draws none where there is none", () => {
    const poster = posterOf(event({banner: {image: {url: "/files/p.webp", path: "p.webp", width: 1080, height: 1080, renditions: [{url: "/files/p-540.webp", width: 540}]}}}))

    expect(poster?.url).toMatch(/\/files\/p\.webp$/u)
    expect(poster?.renditions[0]?.width).toBe(540)
    expect(posterOf(event({banner: {image: {url: "/files/p.webp"}}}))).toMatchObject({path: "", width: undefined, renditions: []})
    expect(posterOf(event())).toBeNull()
  })
})
