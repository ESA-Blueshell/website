import {DateTime} from "luxon"
import {onMounted, ref} from "vue"
import {listCommittees} from "@/domains/committees"
import {listEvents} from "@/domains/events"
import type {Choice} from "../declarations"

/**
 * The committees and events an expense may be booked to. Events reach back a year, since a
 * declaration often follows the event by weeks, and forward three months for costs paid ahead.
 */
export function useBookingChoices() {
  const committees = ref<Choice[]>([])
  const events = ref<Choice[]>([])

  onMounted(async () => {
    const [held, recent] = await Promise.allSettled([
      listCommittees(),
      listEvents({
        from: DateTime.now().minus({years: 1}).toISO()!,
        to: DateTime.now().plus({months: 3}).toISO()!,
        size: 200,
        sort: ["startTime,desc"],
      }),
    ])
    if (held.status === "fulfilled") {
      committees.value = held.value.filter(one => !one.archived).map(one => ({id: one.id, name: one.name}))
    }
    if (recent.status === "fulfilled") {
      events.value = recent.value.map(one => ({
        id: one.id,
        name: one.title,
        note: DateTime.fromISO(one.startTime).toFormat("d LLL yyyy"),
      }))
    }
  })

  return {committees, events}
}
