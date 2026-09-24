<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {DateTime} from "luxon"
import BandHead from "@/components/island/BandHead.vue"
import PosterStrip from "@/components/island/PosterStrip.vue"
import {type EventQuery, listEvents, readEventPage} from "../adapters/events"
import type {EventResponse, EventSignUpResponse} from ".."
import EventAgenda from "./EventAgenda.vue"
import {pastPosterOf} from "./eventFacts"
import {useEventReader} from "./useEventReader"

/**
 * The events of one game or one committee: those still to come as the events page's agenda, and
 * those that have run in its poster strip, a page at a time. Where there are neither, the note
 * the page gives in the default slot.
 */
defineOptions({name: "ScopedEvents"})

const {scope, testid, mayAdd = false} = defineProps<{
  scope: Pick<EventQuery, "gameCode" | "committeeId">
  testid: string
  /** Offers adding an event from the agenda's head, which then shows even with nothing planned. */
  mayAdd?: boolean
}>()

const PAST_PAGE = 6

const {signUps, committees} = useEventReader()
const upcoming = ref<EventResponse[]>([])
const past = ref<EventResponse[]>([])
const pastTotal = ref<number | undefined>(undefined)
const answered = ref(false)
let page = 0
let ended = false
let reading = false

async function readUpcoming() {
  try {
    upcoming.value = await listEvents({...scope, from: DateTime.now().startOf("day").toISO()!, sort: ["startTime,asc"]})
  } catch {
    upcoming.value = []
  }
}

async function readPast() {
  if (ended || reading) return
  reading = true
  const {events, page: said} = await readEventPage({...scope, approved: true, to: DateTime.now().toISO()!, page, size: PAST_PAGE, sort: ["startTime,desc"]})
  if (events.length < PAST_PAGE) ended = true
  const known = new Set(past.value.map(one => one.id))
  past.value = [...past.value, ...events.filter(one => !known.has(one.id))]
  pastTotal.value = said?.totalElements
  page += 1
  reading = false
}

onMounted(async () => {
  await Promise.all([readUpcoming(), readPast()])
  answered.value = true
})

const posters = computed(() => past.value.map(event => pastPosterOf(event)))

const replace = (event: EventResponse) => {
  upcoming.value = upcoming.value.map(one => (one.id === event.id ? event : one))
}
const drop = (id: number) => {
  upcoming.value = upcoming.value.filter(one => one.id !== id)
}
const signedUp = (signUp: EventSignUpResponse) => {
  signUps.value = [...signUps.value.filter(one => one.id !== signUp.id), signUp]
}
const withdrawn = (id: number) => {
  signUps.value = signUps.value.filter(one => one.id !== id)
}
</script>

<template>
  <event-agenda
    v-if="upcoming.length > 0 || mayAdd"
    :committees="committees"
    :events="upcoming"
    :may-add="mayAdd"
    :sign-ups="signUps"
    @delete:event="drop"
    @delete:sign-up="withdrawn"
    @update:event="replace"
    @update:sign-up="signedUp"
  />

  <section
    v-if="posters.length > 0"
    class="w-full"
    :data-testid="`${testid}-past`"
  >
    <band-head
      class="mx-auto w-full max-w-6xl px-5 pt-10 pb-6 sm:px-8"
      :count="pastTotal"
      count-said="past events"
      heading="Past events"
    />
    <poster-strip
      class="pb-10"
      :items="posters"
      :testid-prefix="`${testid}-past-strip`"
      @needs-more="readPast"
    />
  </section>

  <p
    v-if="answered && upcoming.length === 0 && past.length === 0"
    class="scoped-events__none"
    :data-testid="`${testid}-none`"
  >
    <slot />
  </p>
</template>

<style scoped>
.scoped-events__none {
  max-width: 72rem;
  margin: 0 auto;
  padding: 2.5rem 1.25rem;
  font-size: 0.95rem;
  color: var(--color-ash);
}
</style>
