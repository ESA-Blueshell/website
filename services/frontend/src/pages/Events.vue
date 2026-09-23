<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {DateTime} from "luxon"

import {type EventResponse, type EventSignUpResponse, listEvents, useEventReader} from "@/domains/events"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import CallBand, {type Call} from "@/components/island/CallBand.vue"
import CutButton from "@/components/island/CutButton.vue"
import Island from "@/components/island/Island.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import EventsBand from "@/domains/association/island/EventsBand.vue"
import EventAgenda from "@/domains/events/island/EventAgenda.vue"
import EventsHead from "@/domains/events/island/EventsHead.vue"
import NextEventBand from "@/domains/events/island/NextEventBand.vue"

/** The association's public calendar, which people subscribe to once and keep. */
const CALENDAR_URL =
  "https://calendar.google.com/calendar/u/1/r?cid=87r5v7ep7k9ronlrg8n2q9033s@group.calendar.google.com"

const CALENDAR_CALL: Call = {
  eyebrow: "Never miss one",
  headline: "Every event, in your own calendar",
  body: "Subscribe to the Blueshell calendar once and new events turn up in Google Calendar by themselves.",
  testid: "events-calendar-call",
  actions: [
    {label: "Add to Google Calendar", href: CALENDAR_URL, tone: "solid", away: true, testid: "events-calendar-call-subscribe"},
    {label: "Ask on Discord", href: DISCORD_INVITE, away: true, testid: "events-calendar-call-discord"},
  ],
}

type Event = EventResponse
type EventSignUp = EventSignUpResponse

const events = ref<Event[]>([])
const hashAccessToken = ref<string | null>(null)
const {signUps: eventSignUps, committees} = useEventReader(hashAccessToken)

const startOfTodayIso = DateTime.now().startOf("day").toISO()!

async function loadEvents() {
  try {
    events.value = await listEvents({from: startOfTodayIso, sort: ["startTime", "asc"]})
  } catch (e) {
    $handleNetworkError(e)
  }
}

onMounted(() => {
  const hash = window.location.hash
  if (hash.startsWith("#")) {
    const token = new URLSearchParams(hash.slice(1)).get("accessToken")
    if (token) {
      hashAccessToken.value = token
      window.history.replaceState(
        window.history.state,
        document.title,
        `${window.location.pathname}${window.location.search}`
      )
    }
  }
  void loadEvents()
})

type WithOptionalId = { id?: number }
type RefLike<T> = { value: T }

function upsert<T extends WithOptionalId>(listRef: RefLike<T[] | undefined>, item: T) {
  const list = listRef.value ?? []
  const idx = list.findIndex(e => e.id === item.id)
  listRef.value = idx === -1 ? [...list, item] : [...list.slice(0, idx), item, ...list.slice(idx + 1)]
}

function removeById<T extends WithOptionalId>(listRef: RefLike<T[] | undefined>, id: number) {
  const list = listRef.value ?? []
  listRef.value = list.filter(e => e.id !== id)
}

const updateEvent = (event: Event) => {
  upsert(events, event)
}

const deleteEvent = (id: number) => {
  removeById<Event>(events, id)
}

/* The first event still to come leads the page; the agenda is everything after it. */
const next = computed<Event | undefined>(() => events.value[0])
const rest = computed<Event[]>(() => events.value.slice(1))

const updateSignUp = (su: EventSignUp) => {
  const ev = events.value.find(e => e.id === su.eventId)
  const newSignUp = !eventSignUps.value.some(e => e.id === su.id)
  if (ev && newSignUp) {
    ev.signUpCount! += 1
  }
  upsert(eventSignUps, su)
}

const deleteSignUp = (id: number) => {
  const su = eventSignUps.value.find(es => es.id === id)
  if (!su) return
  const ev = events.value.find(e => e.id === su.eventId)
  if (ev) ev.signUpCount! -= 1
  removeById<EventSignUp>(eventSignUps, id)
}
</script>

<template>
  <v-main>
    <island
      class="events-page"
      testid="events-island"
    >
      <events-head
        eyebrow="What is on at Blueshell"
        heading="Events"
      >
        Game nights, tournaments, LANs and trips, most of them in the Esports Lounge Twente.
        Outside the kick-off in September most events are for members, but do not let that stop
        you. Know somebody in the association, or
        <a
          :href="DISCORD_INVITE"
          rel="noopener"
          target="_blank"
        >ask the board on our Discord</a>, and you are almost always welcome to come along and
        see what Blueshell is about.
        <template #actions>
          <cut-button
            away
            :href="CALENDAR_URL"
            testid="event-calendar-subscribe-btn"
            tone="solid"
          >
            Add to Google Calendar
          </cut-button>
          <cut-button
            href="/events/past"
            testid="events-past-link"
          >
            Past events
          </cut-button>
        </template>
      </events-head>

      <next-event-band
        v-if="next"
        :committees="committees"
        :event="next"
        :sign-ups="eventSignUps"
        @delete:event="deleteEvent"
        @delete:sign-up="deleteSignUp"
        @update:event="updateEvent"
        @update:sign-up="updateSignUp"
      />

      <event-agenda
        :committees="committees"
        :events="rest"
        :may-add="committees.length > 0"
        :sign-ups="eventSignUps"
        @delete:event="deleteEvent"
        @delete:sign-up="deleteSignUp"
        @update:event="updateEvent"
        @update:sign-up="updateSignUp"
      />

      <call-band v-bind="CALENDAR_CALL" />

      <events-band
        eyebrow=""
        heading="Past events"
        testid="events-past"
      >
        <cut-button
          href="/events/past"
          testid="events-past-all"
        >
          Every past event
        </cut-button>
      </events-band>
    </island>
  </v-main>
</template>

<style scoped>
/* The island root fills a page; the Vuetify main around it already does. */
.events-page {
  min-height: 0;
}
</style>
