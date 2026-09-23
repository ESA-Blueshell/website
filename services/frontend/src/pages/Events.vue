<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {DateTime} from "luxon"

import {type EventResponse, type EventSignUpResponse, listEvents, useEventReader} from "@/domains/events"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import CallBand, {type Call} from "@/components/island/CallBand.vue"
import CutButton from "@/components/island/CutButton.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import Island from "@/components/island/Island.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import EventsBand from "@/domains/association/island/EventsBand.vue"
import EventAgenda from "@/domains/events/island/EventAgenda.vue"
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
      <header-band>
        <template #head>
          <div class="events-head">
            <div class="events-head__words">
              <p class="events-head__eyebrow">
                What is on at Blueshell
              </p>
              <h1 class="events-head__title">
                Events
              </h1>
              <p class="events-head__body">
                Game nights, tournaments, LANs and trips, most of them in the Esports Lounge Twente.
                Outside the kick-off in September most events are for members, but do not let that stop
                you. Know somebody in the association, or
                <a
                  class="events-head__link"
                  :href="DISCORD_INVITE"
                  rel="noopener"
                  target="_blank"
                >ask the board on our Discord</a>, and you are almost always welcome to come along and
                see what Blueshell is about.
              </p>
            </div>
            <div class="events-head__actions">
              <cut-button
                away
                :href="CALENDAR_URL"
                testid="event-calendar-subscribe-btn"
                tone="solid"
              >
                Add to Google Calendar
              </cut-button>
              <cut-button href="#past-events">
                Past events
              </cut-button>
            </div>
          </div>
        </template>
      </header-band>

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
        id="past-events"
        eyebrow=""
        heading="Past events"
        testid="events-past"
      />
    </island>
  </v-main>
</template>

<style scoped>
/* The island root fills a page; the Vuetify main around it already does. */
.events-page {
  min-height: 0;
}

.events-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem 2rem;
  padding-top: 1.5rem;
}

.events-head__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.events-head__title {
  margin-top: 0.7rem;
  font-family: var(--font-display);
  font-size: 4.5rem;
  line-height: 0.95;
  text-transform: uppercase;
}

.events-head__body {
  max-width: 36rem;
  margin-top: 0.9rem;
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--color-ash);
}

.events-head__link {
  color: var(--color-brand);
}

.events-head__link:hover,
.events-head__link:focus-visible {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.events-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

@media (max-width: 767px) {
  .events-head {
    padding-top: 0.5rem;
  }

  .events-head__title {
    font-size: 3rem;
  }
}
</style>
