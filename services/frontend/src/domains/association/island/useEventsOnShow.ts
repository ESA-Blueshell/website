import {computed, type ComputedRef, onMounted, ref} from "vue"
import {DateTime} from "luxon"
import type {SliceItem} from "@/components/island/SliceBand.vue"
import {srcsetOf} from "@/components/island/pictures"
import {type EventOnShow, loadEventsOnShow} from "@/domains/association/adapters/association"

/** How many are asked for, and the fewest that make a band worth drawing. */
const WANTED = 6
const ENOUGH = 3

/**
 * Recent events with their own art, as slices.
 *
 * The band is absent rather than short: two events under a heading that promises what goes on
 * here reads as an association where nothing goes on. Nothing is drawn while the read is in
 * flight either, so the page never grows a heading and then empties it.
 */
export function useEventsOnShow(): {slices: ComputedRef<SliceItem[]>} {
  const events = ref<EventOnShow[]>([])

  onMounted(async () => {
    try {
      events.value = await loadEventsOnShow(WANTED)
    } catch {
      // Nothing to show is a fine answer for a band that only ever shows off.
    }
  })

  const slices = computed<SliceItem[]>(() => {
    if (events.value.length < ENOUGH) return []
    return events.value.map((one): SliceItem => ({
      id: one.id,
      title: one.title,
      meta: metaOf(one),
      banner: one.banner.url,
      srcset: srcsetOf(one.banner),
      width: one.banner.width ?? undefined,
      height: one.banner.height ?? undefined,
      href: "/events",
    }))
  })

  return {slices}
}

/**
 * When it happened, and for whom.
 *
 * Members-only is said rather than hidden: on a page selling membership, the events a member
 * gets are the argument.
 */
function metaOf(event: EventOnShow): string {
  const when = DateTime.fromISO(event.startTime).toFormat("LLLL yyyy")
  return event.membersOnly ? `${when} · members only` : when
}
