<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useStore} from "vuex"
import {DateTime} from "luxon"
import EventCalendar from "@/components/base/EventCalendar.vue"

import {
  type AdvancedCommittee,
  type Event,
  type EventSignUp,
  findCommitteesForCurrentUser,
  findEvents,
  findEventSignUps,
  findEventSignUpsByAccessToken,
  type Guest,
  type Login,
} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import PastEventsPane from "@/components/base/PastEventsPane.vue"
import EventList from "@/components/common/lists/EventList.vue"

const store = useStore()

const events = ref<Event[]>([])
const committees = ref<AdvancedCommittee[]>([])
const eventSignUps = ref<EventSignUp[]>([])
const calendarRef = ref<InstanceType<typeof EventCalendar>>()

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed<Login | undefined>(() => store.getters.getLogin)
const guest = computed<Guest | null>(() => store.getters.getGuestData)
const guestAccessToken = computed<string | null>(() => guest.value?.accessToken ?? null)

const startOfTodayIso = DateTime.now().startOf("day").toISO()!

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
        path: {accessToken: guestAccessToken.value},
        throwOnError: true,
      })
      eventSignUps.value = resp.data ?? []
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
      const resp = await findCommitteesForCurrentUser({throwOnError: true})
      committees.value = (resp.data as AdvancedCommittee[]) ?? []
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
      title="Events"
      height="200x"
      m-height="100px"
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
        <p class="mt-8 mx-3 mb-4 text-h3 text-center">
          Upcoming Events
        </p>
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
