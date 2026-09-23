import {createEvent} from "ics"
import {DateTime} from "luxon"
import type {EventResponse} from ".."

/** Hands the reader the event as a calendar file, named after it. */
export function downloadIcs(event: EventResponse): void {
  const start = DateTime.fromISO(event.startTime).toUTC()
  const end = DateTime.fromISO(event.endTime).toUTC()
  createEvent({
    title: event.title,
    description: event.description ?? undefined,
    location: event.location ?? undefined,
    start: [start.year, start.month, start.day, start.hour, start.minute],
    end: [end.year, end.month, end.day, end.hour, end.minute],
    startInputType: "utc",
    endInputType: "utc",
  }, (error, value) => {
    if (error) return
    const anchor = document.createElement("a")
    anchor.href = `data:text/calendar;charset=utf-8,${encodeURIComponent(value)}`
    anchor.download = `${event.title}.ics`
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
  })
}

/** The address of an event's own page, which is what a shared link should open. */
export const pageUrlOf = (event: EventResponse): string => `${globalThis.location.origin}/events/${event.id}`
