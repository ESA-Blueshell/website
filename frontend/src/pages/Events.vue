<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import {DateTime} from "luxon"

import TopBanner from "@/components/common/banners/TopBanner.vue"
import EventCalendar from "@/components/base/EventCalendar.vue"
import EventList from "@/components/common/lists/EventList.vue"

import {
  type AdvancedCommittee,
  type Event,
  type EventSignUp,
  findCommitteesForCurrentUser,
  findEvents,
  findEventSignUpsByAccessToken,
  findEventSignUps,
  type Guest,
  type Login,
} from "@/services/api"
import PastEventsPane from "@/components/base/PastEventsPane.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const store = useStore()

const events = ref<Event[]>([])
const committees = ref<AdvancedCommittee[]>([])
const eventSignUps = ref<EventSignUp[]>([])
const calendarRef = ref<InstanceType<typeof EventCalendar>>()
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed<Login>(() => store.getters.getLogin)
const guest = computed<Guest>(() => store.getters.getGuest)

const startOfTodayIso = DateTime.now().startOf("day").toISO()!

onMounted(async () => {
  try {

    const [eventsResp, signupsResp, committeesResp] = await Promise.all([
      findEvents({
        query: {from: startOfTodayIso, sort: ["startTime", "asc"]},
      }),
      login.value
        ? findEventSignUps({
          query: {from: startOfTodayIso, userId: login.value.userId},
        })
        : guest.value?.accessToken
          ? findEventSignUpsByAccessToken({path: {accessToken: guest.value.accessToken}})
          : Promise.resolve({data: [] as EventSignUp[]}),
      isLoggedIn.value
        ? findCommitteesForCurrentUser()
        : Promise.resolve({data: [] as AdvancedCommittee[]}),
    ])

    events.value = eventsResp.data?.content ?? []
    if (isLoggedIn.value) {
      eventSignUps.value = signupsResp.data ?? []
      committees.value = (committeesResp.data as AdvancedCommittee[]) ?? []
    }
  } catch (e) {
    $handleNetworkError(e)
  }
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
  if (ev) {
    ev.signUpCount! -= 1
  }
  removeById<EventSignUp>(eventSignUps, id)
}
</script>

<template>
  <v-main>
    <top-banner title="Events" />
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

        <v-divider class="my-3" />

        <past-events-pane
          :committees="committees"
          :event-sign-ups="eventSignUps"
        />
      </div>
    </div>
  </v-main>
</template>
