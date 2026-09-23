import {computed, type ComputedRef, onMounted, type Ref, ref, watch} from "vue"
import {DateTime} from "luxon"
import {listCommittees} from "@/domains/committees"
import {readEventPage} from "../adapters/events"
import type {EventQuery} from "../adapters/events"
import type {EventResponse} from ".."
import {monthsOf} from "./eventFacts"
import type {CommitteeOption} from "./useEventReader"

/** Six across on a desktop, four rows of them a page. */
export const PAST_PAGE = 24

/** How long typing rests before the search is sent, so a word is one read rather than five. */
export const SEARCH_REST_MS = 300

export const ALL_YEARS = "all"

/** An academic year, which starts on the first of September. */
export type AcademicYear = {key: string, label: string, from: DateTime, to: DateTime}

export function academicYearOf(at: DateTime): number {
  return at.month >= 9 ? at.year : at.year - 1
}

/** Every academic year from the one `first` falls in up to the one `now` falls in, newest first. */
export function academicYearsBetween(first: DateTime, now: DateTime): AcademicYear[] {
  const years: AcademicYear[] = []
  for (let start = academicYearOf(now); start >= academicYearOf(first); start--) {
    years.push({
      key: String(start),
      label: `${start}–${String(start + 1).slice(2)}`,
      from: DateTime.local(start, 9, 1),
      to: DateTime.local(start + 1, 9, 1),
    })
  }
  return years
}

/**
 * The events that have happened, newest first and a page at a time, narrowed to one academic
 * year and to a title. The years offered are the ones between the first event there is and
 * today, read once. Committee names are read for the line under each poster.
 */
export function usePastEvents(now: DateTime = DateTime.now()): {
  years: Ref<AcademicYear[]>
  year: Ref<string>
  search: Ref<string>
  events: Ref<EventResponse[]>
  total: Ref<number>
  answered: Ref<boolean>
  months: ComputedRef<ReturnType<typeof monthsOf>>
  hasOlder: ComputedRef<boolean>
  showOlder: () => Promise<void>
  committeeOf: (event: EventResponse) => string
} {
  const years = ref<AcademicYear[]>([])
  const year = ref<string>(ALL_YEARS)
  const search = ref("")
  const events = ref<EventResponse[]>([])
  const total = ref(0)
  const page = ref(0)
  const answered = ref(false)
  const committees = ref<CommitteeOption[]>([])

  const queryFor = (at: number): EventQuery => {
    const chosen = years.value.find(one => one.key === year.value)
    const until = chosen && chosen.to < now ? chosen.to : now
    const title = search.value.trim()
    return {
      approved: true,
      from: chosen?.from.toISO() ?? undefined,
      to: until.toISO()!,
      titleContains: title === "" ? undefined : title,
      page: at,
      size: PAST_PAGE,
      sort: ["startTime,desc"],
    }
  }

  /* A read that a newer one overtook is dropped, so a slow first answer never lands over the
     filter chosen after it. */
  let asked = 0
  const read = async (at: number) => {
    const mine = ++asked
    const found = await readEventPage(queryFor(at))
    if (mine !== asked) return
    events.value = at === 0 ? found.events : [...events.value, ...found.events]
    total.value = found.page?.totalElements ?? events.value.length
    page.value = at
    answered.value = true
  }

  const readYears = async () => {
    const first = await readEventPage({approved: true, to: now.toISO()!, size: 1, sort: ["startTime,asc"]})
    const oldest = first.events[0]
    years.value = oldest ? academicYearsBetween(DateTime.fromISO(oldest.startTime), now) : []
  }

  const readCommittees = async () => {
    try {
      committees.value = (await listCommittees()).flatMap(one =>
        typeof one.id === "number" && typeof one.name === "string" ? [{id: one.id, name: one.name}] : [])
    } catch {
      // A poster without its committee's name is still the event.
    }
  }

  onMounted(() => {
    void read(0)
    void readYears()
    void readCommittees()
  })

  watch(year, () => void read(0))

  let resting: ReturnType<typeof setTimeout> | undefined
  watch(search, () => {
    clearTimeout(resting)
    resting = setTimeout(() => void read(0), SEARCH_REST_MS)
  })

  const months = computed(() => monthsOf(events.value))
  const hasOlder = computed<boolean>(() => events.value.length < total.value)

  const committeeOf = (event: EventResponse): string => {
    if (event.committeeId == null) return "Member's initiative"
    return committees.value.find(one => one.id === event.committeeId)?.name ?? ""
  }

  return {
    years,
    year,
    search,
    events,
    total,
    answered,
    months,
    hasOlder,
    showOlder: () => read(page.value + 1),
    committeeOf,
  }
}
