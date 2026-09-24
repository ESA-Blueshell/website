import {DateTime} from "luxon"
import type {PosterItem} from "@/components/island/PosterStrip.vue"
import {type Picture, srcsetOf} from "@/components/island/pictures"
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

/**
 * An event that has run, as the poster strip draws it: its poster or a date plate, the year where
 * it is not this one, and who it was for.
 */
export function pastPosterOf(event: EventResponse, now: DateTime = DateTime.now()): PosterItem {
  const poster = posterOf(event)
  const at = DateTime.fromISO(event.startTime)
  return {
    id: event.id,
    title: event.title,
    meta: [at.year === now.year ? "" : at.toFormat("yyyy"), event.membersOnly ? "members only" : ""].filter(Boolean).join(" · "),
    said: (event.description ?? "").replace(/\s+/gu, " ").trim(),
    banner: poster?.url,
    srcset: srcsetOf(poster),
    width: poster?.width ?? undefined,
    height: poster?.height ?? undefined,
    ...plateOf(event),
    where: event.location ?? undefined,
    href: `/events/${event.id}`,
  }
}

/**
 * The day and the hours, as the page writes them: "Tue 22 September" and "19:00-22:00".
 * Mirrored in the api's `EventLinkPreview.kt`.
 */
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

/**
 * What a date plate says for an event without a poster: the day of the month, the month, and
 * the hours, with the day it ends where that is another day.
 */
export function plateOf(event: {startTime: string, endTime?: string | null}): {day: string, month: string, when: string} {
  const from = DateTime.fromISO(event.startTime)
  const until = event.endTime ? DateTime.fromISO(event.endTime) : undefined
  const when = until === undefined
    ? from.toFormat("HH:mm")
    : from.hasSame(until, "day")
      ? `${from.toFormat("HH:mm")}-${until.toFormat("HH:mm")}`
      : `${from.toFormat("HH:mm")} to ${until.toFormat("ccc d LLL, HH:mm")}`
  return {day: from.toFormat("d"), month: from.toFormat("LLL"), when}
}

/**
 * How soon, for the tag beside "Next up": today, tomorrow, or in so many days; else nothing.
 * An event that has ended is not soon at all, and one still running is today's.
 */
export function soonOf(event: EventResponse, now: DateTime = DateTime.now()): string {
  if (DateTime.fromISO(event.endTime) < now) return ""
  const days = Math.round(DateTime.fromISO(event.startTime).startOf("day").diff(now.startOf("day"), "days").days)
  if (days <= 0) return "Today"
  if (days === 1) return "Tomorrow"
  return days < 14 ? `In ${days} days` : ""
}

/** Whether it happens on Discord, the one place an event is held online. */
export function isOnline(location?: string | null): boolean {
  return location?.toLowerCase().includes("discord") ?? false
}

/** What to say where there is nothing to sign up for: walk in, or join the call online. */
export function noSignUpsOf(location?: string | null): string {
  return isOnline(location) ? "No sign-ups, just join the call" : "No sign-ups, just walk in"
}

/** How full it is: places taken of a limit, heads counted without one, or none to take. */
export function placesOf(event: EventResponse): {said: string, taken?: number} {
  if (!event.signUp) return {said: noSignUpsOf(event.location)}
  const limit = event.signUpLimit
  if (limit == null) return {said: `${event.signUpCount} going`}
  return {said: `${event.signUpCount} of ${limit} taken`, taken: Math.min(event.signUpCount / limit, 1)}
}

/** Whether somebody can still sign up, in a few words beside the event. */
export function signUpStateOf(event: EventResponse, now: DateTime = DateTime.now()): string {
  if (!event.signUp) return noSignUpsOf(event.location)
  if (event.signUpDeadline && DateTime.fromISO(event.signUpDeadline) < now) return "Sign-ups closed"
  if (event.signUpLimit != null && event.signUpCount >= event.signUpLimit) return "Full"
  if (event.signUpDeadline) return `Sign-ups close ${DateTime.fromISO(event.signUpDeadline).toFormat("ccc d LLL")}`
  return "Sign-ups open"
}

/** Where to find it: the Discord for an event held there, a map search for anywhere else. */
export function directionsOf(location: string): string {
  if (isOnline(location)) return DISCORD_INVITE
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
