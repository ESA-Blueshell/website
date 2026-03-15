<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useStore} from "vuex"
import {DateTime} from "luxon"
import EventCalendar from "@/components/base/EventCalendar.vue"
import type {GuestSessionData} from "@/plugins/store.ts"

import {
  findCommittees,
  findCommitteesByUserId,
  type CommitteeDetailResponse,
  type EventResponse,
  type EventSignUpResponse,
  findEvents,
  findEventSignUps,
  findEventSignUpsByAccessToken,
  type LoginResponse,
} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import PastEventsPane from "@/components/base/PastEventsPane.vue"
import EventList from "@/components/common/lists/EventList.vue"

const store = useStore()

type CommitteeOption = Pick<CommitteeDetailResponse, "id" | "name">
type Event = EventResponse
type EventSignUp = EventSignUpResponse
type Login = LoginResponse

const events = ref<Event[]>([])
const committees = ref<CommitteeOption[]>([])
const eventSignUps = ref<EventSignUp[]>([])
const calendarRef = ref<InstanceType<typeof EventCalendar>>()
const hashAccessToken = ref<string | null>(null)

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const isBoard = computed<boolean>(() => store.getters.isBoard)
const login = computed<Login | undefined>(() => store.getters.getLogin)
const guest = computed<GuestSessionData | null>(() => store.getters.getGuestData)
const guestAccessToken = computed<string | null>(() => guest.value?.accessToken ?? hashAccessToken.value)

const startOfTodayIso = DateTime.now().startOf("day").toISO()!
const guestAccessHeader = "X-Guest-Access-Token"

async function loadEvents() {
  try {
    const resp = await findEvents({
      query: {from: startOfTodayIso, sort: ["startTime", "asc"]},
    })
    events.value = resp.data?.content ?? []
  } catch (e) {
    $handleNetworkError(e)
  }
}

async function loadSignUps() {
  try {
    if (isLoggedIn.value && login.value?.userId != null) {
      const resp = await findEventSignUps({
        query: {from: startOfTodayIso, userId: login.value.userId},
        throwOnError: true,
      })
      eventSignUps.value = resp.data ?? []
    } else if (guestAccessToken.value) {
      const resp = await findEventSignUpsByAccessToken({
        headers: {[guestAccessHeader]: guestAccessToken.value},
        throwOnError: true,
      })
      eventSignUps.value = resp.data ?? []
      const firstGuest = resp.data?.[0]?.guest
      if (firstGuest != null) {
        store.commit("saveGuestData", {
          ...firstGuest,
          accessToken: guestAccessToken.value,
        } satisfies GuestSessionData)
      }
    } else {
      eventSignUps.value = []
    }
  } catch (e) {
    $handleNetworkError(e)
  }
}

async function loadCommittees() {
  try {
    if (isLoggedIn.value) {
      const resp = isBoard.value
        ? await findCommittees({throwOnError: true})
        : await findCommitteesByUserId({throwOnError: true})
      committees.value = ((resp.data ?? []) as unknown[])
        .map((committee) => {
          const value = committee as Record<string, unknown>
          const id = typeof value.id === "number" ? value.id : null
          const name = typeof value.name === "string" ? value.name : null
          if (id == null || name == null) return null
          return {id, name}
        })
        .filter((committee): committee is CommitteeOption => committee != null)
    } else {
      committees.value = []
    }
  } catch (e) {
    $handleNetworkError(e)
  }
}

/** Reactively fetch sign-ups/committees when login/guest readiness changes. */
watch([isLoggedIn, login, guestAccessToken], () => {
  void loadSignUps()
  void loadCommittees()
}, {immediate: true})

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
  calendarRef.value?.updateEvent(event)
}

const deleteEvent = (id: number) => {
  removeById<Event>(events, id)
  calendarRef.value?.deleteEvent(id)
}

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
    <top-banner
      height="200x"
      m-height="100px"
      title="Events"
    />
    <div class="mx-3">
      <div
        class="mx-auto my-5"
        style="max-width: 1200px"
      >
        <event-calendar ref="calendarRef" />
      </div>
      <div
        class="mx-auto mt-5"
        style="max-width: 800px"
      >
        <p class="mt-4 mx-3 mb-4 text-h3 text-center">
          Upcoming Events
        </p>
        <v-btn
          v-if="committees.length"
          block
          to="/events/create"
        >
          Create new event
        </v-btn>
        <event-list
          :committees="committees"
          :event-sign-ups="eventSignUps"
          :events="events"
          @update:event="updateEvent"
          @delete:event="deleteEvent"
          @update:sign-up="updateSignUp"
          @delete:sign-up="deleteSignUp"
        />

        <past-events-pane
          :committees="committees"
          :event-sign-ups="eventSignUps"
        />
      </div>
    </div>
  </v-main>
</template>
