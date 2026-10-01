import {DateTime} from "luxon"
import {ref} from "vue"
import {AnnounceChoice} from "@/services/api"

/* The api's AnnounceMorning makes the same morning; the two name each other. */
const ZONE = "Europe/Amsterdam"
const MORNING_HOUR = 8

/** Today's 08:00 Amsterdam time while it is still before it, tomorrow's from 08:00 on. */
export const nextMorning = (now: DateTime): DateTime => {
  const there = now.setZone(ZONE)
  const today = there.set({hour: MORNING_HOUR, minute: 0, second: 0, millisecond: 0})
  return there < today ? today : today.plus({days: 1})
}

/** What an event being approved needs said about its events-info post. */
export type AnnounceNeed = "none" | "now" | "ask"

/**
 * Nothing once the post is out; at once for an event starting before the next morning, where
 * waiting would post it too late; otherwise the board's choice.
 */
export const announceNeed = (event: {announced?: boolean | null; startTime: string}, now: DateTime): AnnounceNeed => {
  if (event.announced) return "none"
  return DateTime.fromISO(event.startTime) < nextMorning(now) ? "now" : "ask"
}

/** The later choice, in the viewer's own time: "Post today at 08:00" or "Post tomorrow at 09:00". */
export const morningSaid = (now: DateTime): string => {
  const morning = nextMorning(now).setZone(now.zone)
  const day = morning.hasSame(now, "day") ? "today" : "tomorrow"
  return `Post ${day} at ${morning.toFormat("HH:mm")}`
}

/**
 * Asks when an approved event's events-info post goes out. Answers the choice, undefined where
 * there is nothing to ask, and null where the board cancelled, which cancels the approval too.
 */
export function useAnnouncePrompt() {
  const open = ref(false)
  const later = ref("")
  let settle: ((choice: AnnounceChoice | null) => void) | null = null

  const ask = (event: {announced?: boolean | null; startTime: string}): Promise<AnnounceChoice | null | undefined> => {
    const now = DateTime.now()
    const need = announceNeed(event, now)
    if (need === "none") return Promise.resolve(undefined)
    if (need === "now") return Promise.resolve(AnnounceChoice.NOW)
    later.value = morningSaid(now)
    open.value = true
    return new Promise(resolve => {
      settle = resolve
    })
  }

  const answer = (choice: AnnounceChoice | null) => {
    open.value = false
    settle?.(choice)
    settle = null
  }

  return {open, later, ask, answer}
}
