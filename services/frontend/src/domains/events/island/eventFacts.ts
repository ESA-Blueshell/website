import {DateTime} from "luxon"
import type {Picture} from "@/components/island/pictures"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import {eventFileUrl} from "../adapters/events"
import type {EventResponse} from ".."

/** The event's poster as the island draws a picture, its paths resolved against the api. */
export function posterOf(event: EventResponse): Picture | null {
  const stored = event.banner?.image
  if (!stored?.url) return null
  return {
    url: eventFileUrl(stored.url),
    path: stored.path ?? "",
    width: stored.width ?? undefined,
    height: stored.height ?? undefined,
    renditions: (stored.renditions ?? []).map(one => ({url: eventFileUrl(one.url), width: one.width})),
  }
}

/** The day and the hours, as the page writes them: "Tue 22 September" and "19:00-22:00". */
export function whenOf(event: EventResponse): {day: string, hours: string} {
  const from = DateTime.fromISO(event.startTime)
  const until = DateTime.fromISO(event.endTime)
  const sameDay = from.hasSame(until, "day")
  return {
    day: from.toFormat("ccc d LLLL"),
    hours: sameDay
      ? `${from.toFormat("HH:mm")}-${until.toFormat("HH:mm")}`
      : `${from.toFormat("HH:mm")} to ${until.toFormat("ccc d LLLL, HH:mm")}`,
  }
}

/** How soon, for the tag beside "Next up": today, tomorrow, or in so many days; else nothing. */
export function soonOf(event: EventResponse, now: DateTime = DateTime.now()): string {
  const days = Math.round(DateTime.fromISO(event.startTime).startOf("day").diff(now.startOf("day"), "days").days)
  if (days <= 0) return "Today"
  if (days === 1) return "Tomorrow"
  return days < 14 ? `In ${days} days` : ""
}

/** How full it is: places taken of a limit, heads counted without one, or none to take. */
export function placesOf(event: EventResponse): {said: string, taken?: number} {
  if (!event.signUp) return {said: "No sign-ups, just walk in"}
  const limit = event.signUpLimit
  if (limit == null) return {said: `${event.signUpCount} going`}
  return {said: `${event.signUpCount} of ${limit} taken`, taken: Math.min(event.signUpCount / limit, 1)}
}

/** Whether somebody can still sign up, in a few words beside the event. */
export function signUpStateOf(event: EventResponse, now: DateTime = DateTime.now()): string {
  if (!event.signUp) return "No sign-ups, just walk in"
  if (event.signUpDeadline && DateTime.fromISO(event.signUpDeadline) < now) return "Sign-ups closed"
  if (event.signUpLimit != null && event.signUpCount >= event.signUpLimit) return "Full"
  if (event.signUpDeadline) return `Sign-ups close ${DateTime.fromISO(event.signUpDeadline).toFormat("ccc d LLL")}`
  return "Sign-ups open"
}

/** Where to find it: the Discord for an event held there, a map search for anywhere else. */
export function directionsOf(location: string): string {
  if (location.toLowerCase().includes("discord")) return DISCORD_INVITE
  return encodeURI(`https://www.google.com/maps/search/?api=1&query=${location}`)
}

/** Events grouped by the month they start in, in the order they were given. */
export function monthsOf(events: EventResponse[]): {key: string, name: string, events: EventResponse[]}[] {
  const months: {key: string, name: string, events: EventResponse[]}[] = []
  for (const event of events) {
    const at = DateTime.fromISO(event.startTime)
    const key = at.toFormat("yyyy-MM")
    const last = months.at(-1)
    if (last?.key === key) last.events.push(event)
    else months.push({key, name: at.toFormat("LLLL yyyy"), events: [event]})
  }
  return months
}

const euros = new Intl.NumberFormat("nl-NL", {style: "currency", currency: "EUR"})
/* The house writes €9,50 without the space the Dutch format puts after the sign. */
const eurosOf = (amount: number): string => euros.format(amount).replace(/\s/gu, "")

/** What it costs, and who for: one price, a member's and everybody else's, or nothing at all. */
export function priceOf(event: EventResponse): {said: string, sub: string} {
  const member = event.memberPrice ?? 0
  const others = event.publicPrice ?? 0
  const who = event.membersOnly ? "Members only" : "Open to anybody"
  if (member === 0 && (event.membersOnly || others === 0)) return {said: "Free", sub: who}
  if (event.membersOnly || member === others) return {said: eurosOf(event.membersOnly ? member : others), sub: who}
  return {said: `${eurosOf(member)} · ${eurosOf(others)}`, sub: "Members · everybody else"}
}

/** When sign-ups close, in the words under how full it is; nothing where they never do. */
export function deadlineOf(event: EventResponse, now: DateTime = DateTime.now()): string {
  if (!event.signUp || !event.signUpDeadline) return ""
  const at = DateTime.fromISO(event.signUpDeadline)
  return `${at < now ? "Sign-ups closed" : "Sign-ups close"} ${at.toFormat("ccc d LLL, HH:mm")}`
}
