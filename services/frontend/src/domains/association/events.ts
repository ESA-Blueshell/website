import {DateTime} from "luxon"
import {sizeOf, srcsetOf, type Picture} from "@/components/island/pictures"
import type {AssociationEvent} from "./adapters/association"

/**
 * One event as the island's slice band draws it.
 *
 * Structural rather than the band's own type, which lives inside an SFC: the band asks for a
 * title, a line under it and a picture, and this states exactly those.
 */
export interface EventSlice {
  id: number
  title: string
  meta: string
  banner: string
  srcset?: string
  width?: number
  height?: number
  expandable: boolean
  /** Where it happened, drawn in the panel a slice opens onto. */
  where: string
}

/** How many events the band draws. The row is designed and styled for six pictures. */
export const SAMPLED = 6

/**
 * The fewest events that still make a band.
 *
 * Under four, an open slice takes so much of the row that the rest are wide slabs and the band's
 * fixed 30px diagonal reads as a rendering fault rather than a cut. Three events is also not a
 * sample of a busy calendar, it is the whole of one, which is the opposite of what the page is
 * arguing. So the band is absent rather than short: a partial band is the defect this prevents.
 */
export const FEWEST = 4

/** The picture on an event, and nothing where the art is not actually servable. */
function artOf(event: AssociationEvent): Picture | null {
  const image = event.banner?.image
  return image?.url ? image : null
}

/**
 * One event drawn as a slice.
 *
 * `src` is the widest stored copy rather than the master, since it is only what a browser falls
 * back to: the ladder goes beside it in `srcset` and the band, which owns the layout, declares
 * the widths it will draw them at.
 */
function sliceOf(event: AssociationEvent, art: Picture): EventSlice {
  const day = DateTime.fromISO(event.startTime).toFormat("d LLL yyyy")
  const where = event.location?.trim() ?? ""
  return {
    id: event.id,
    title: event.title,
    // Members-only is said on the slice rather than hidden: that these events are for members
    // is the argument the page is making with them.
    meta: event.membersOnly ? `${day} · Members only` : day,
    banner: art.renditions[art.renditions.length - 1]?.url ?? art.url,
    srcset: srcsetOf(art),
    // A slice with nothing behind it grows onto an empty panel, so only an event that recorded
    // where it happened opens at all.
    expandable: where !== "",
    where,
    ...sizeOf(art),
  }
}

/**
 * The events the band draws, newest first, or none at all.
 *
 * Events with no servable art are dropped before the count is taken: an event the api counts as
 * having a banner whose file it cannot serve would be a slice with a hole in it.
 */
export function eventSlices(events: AssociationEvent[] | null): EventSlice[] {
  const drawable = (events ?? [])
    .map(event => ({event, art: artOf(event)}))
    .filter((one): one is {event: AssociationEvent; art: Picture} => one.art !== null)
    .slice(0, SAMPLED)
  if (drawable.length < FEWEST) return []
  return drawable.map(one => sliceOf(one.event, one.art))
}
