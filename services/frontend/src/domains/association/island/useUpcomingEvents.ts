import {computed, type ComputedRef, onMounted, ref, type Ref} from "vue"
import {DateTime} from "luxon"
import type {PosterItem} from "@/components/island/PosterStrip.vue"
import {srcsetOf} from "@/components/island/pictures"
import {loadUpcomingEvents, type UpcomingEvent} from "@/domains/association/adapters/association"
import {noSignUpsOf, plateOf} from "@/domains/events"

/** Two views of four, so the strip has somewhere to travel before it asks for more. */
const PAGE = 8

/**
 * The events still to come, as posters, fetched a page at a time as the strip nears its end.
 *
 * Every upcoming event is drawn, poster or not: an event without art gets the template, because
 * leaving it out would hide something somebody could go to. [total] is the api's count, for the
 * badge, and is zero until the first read lands so no heading promises a number early.
 */
export function useUpcomingEvents(): {
  posters: ComputedRef<PosterItem[]>
  total: Ref<number>
  /** Whether every upcoming event has been read, so the strip can end on the way to them all. */
  ended: Ref<boolean>
  more: () => void
} {
  const events = ref<UpcomingEvent[]>([])
  const total = ref(0)
  const page = ref(0)
  const ended = ref(false)
  const reading = ref(false)

  const read = async () => {
    if (ended.value || reading.value) return
    reading.value = true
    try {
      const answered = await loadUpcomingEvents(PAGE, page.value)
      if (answered.events.length < PAGE) ended.value = true
      const known = new Set(events.value.map(one => one.id))
      events.value = [...events.value, ...answered.events.filter(one => !known.has(one.id))]
      total.value = Math.max(answered.total, events.value.length)
      page.value += 1
    } catch {
      ended.value = true
    } finally {
      reading.value = false
    }
  }

  onMounted(read)

  const posters = computed<PosterItem[]>(() => events.value.map((one): PosterItem => ({
    id: one.id,
    title: one.title,
    said: (one.description ?? "").replace(/\s+/gu, " ").trim(),
    banner: one.banner?.url,
    srcset: one.banner ? srcsetOf(one.banner) : undefined,
    width: one.banner?.width ?? undefined,
    height: one.banner?.height ?? undefined,
    ...plateOf(one),
    where: one.location,
    href: `/events/${one.id}`,
    state: stateOf(one),
  })))

  return {posters, total, ended, more: () => void read()}
}

/**
 * Whether somebody can sign up, and how full it is, in the words under a poster.
 *
 * Places left where there is a limit, heads counted where there is none: a number with no
 * ceiling says how lively it is, one with a ceiling says whether to hurry.
 */
export function stateOf(event: UpcomingEvent, now: DateTime = DateTime.now()): string {
  if (!event.signUp) return noSignUpsOf(event.location)
  const deadline = event.signUpDeadline ? DateTime.fromISO(event.signUpDeadline) : undefined
  if (deadline !== undefined && deadline < now) return "Sign-ups closed"

  const who = event.membersOnly ? " · members only" : ""
  if (event.signUpLimit !== undefined) {
    const left = Math.max(event.signUpLimit - event.signUpCount, 0)
    if (left === 0) return `Sign-ups full${who}`
    return `Sign-ups open${who} · ${left} of ${event.signUpLimit} places`
  }
  return `Sign-ups open${who} · ${event.signUpCount} going`
}
