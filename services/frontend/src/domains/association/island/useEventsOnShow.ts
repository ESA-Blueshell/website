import {computed, type ComputedRef, onMounted, ref} from "vue"
import {DateTime} from "luxon"
import type {PosterItem} from "@/components/island/PosterStrip.vue"
import {srcsetOf} from "@/components/island/pictures"
import {type EventOnShow, loadEventsOnShow} from "@/domains/association/adapters/association"
import {plateOf} from "@/domains/events"

/** How many are asked for at a time, and the fewest that make a band worth drawing. */
const PAGE = 6
const ENOUGH = 3

/**
 * Recent events with their own art, as posters, fetched a page at a time.
 *
 * The band is absent rather than short: two events under a heading that promises what goes on
 * here reads as an association where nothing goes on. Nothing is drawn while the first read is
 * in flight either, so the page never grows a heading and then empties it.
 *
 * [more] is what the strip calls when it is two posters from its end: the next page is asked
 * for then rather than when the reader arrives at nothing, and a page that answers short is the
 * end of the events there are.
 */
export function useEventsOnShow(): {
  posters: ComputedRef<PosterItem[]>
  more: () => void
} {
  const events = ref<EventOnShow[]>([])
  const page = ref(0)
  const ended = ref(false)
  const reading = ref(false)

  const read = async () => {
    if (ended.value || reading.value) return
    reading.value = true
    try {
      const answered = await loadEventsOnShow(PAGE, page.value)
      // The api answers with what it has, so a short page is the last one.
      if (answered.length < PAGE) ended.value = true
      // An event drawn twice is worse than one not drawn: a page can overlap after an edit.
      const known = new Set(events.value.map(one => one.id))
      events.value = [...events.value, ...answered.filter(one => !known.has(one.id))]
      page.value += 1
    } catch {
      // Nothing to show is a fine answer for a band that only ever shows off.
      ended.value = true
    } finally {
      reading.value = false
    }
  }

  onMounted(read)

  const posters = computed<PosterItem[]>(() => {
    if (events.value.length < ENOUGH) return []
    return events.value.map((one): PosterItem => ({
      id: one.id,
      title: one.title,
      meta: metaOf(one),
      said: (one.description ?? "").replace(/\s+/gu, " ").trim(),
      banner: one.banner?.url,
      srcset: one.banner ? srcsetOf(one.banner) : undefined,
      width: one.banner?.width ?? undefined,
      height: one.banner?.height ?? undefined,
      // Written onto the date plate, where nobody made a poster for this one.
      ...plateOf(one),
      where: one.location,
      href: `/events/${one.id}`,
    }))
  })

  return {posters, more: () => void read()}
}

/**
 * The year, where it is not this one, and who it was for.
 *
 * A poster carries its own date, so repeating this year's under it says nothing. An older year
 * is said, because "September" on a poster reads as this September. Members-only is said rather
 * than hidden: on a page selling membership, the events a member gets are the argument.
 */
function metaOf(event: EventOnShow): string {
  const at = DateTime.fromISO(event.startTime)
  const year = at.year === DateTime.now().year ? "" : at.toFormat("yyyy")
  return [year, event.membersOnly ? "members only" : ""].filter(said => said !== "").join(" · ")
}
