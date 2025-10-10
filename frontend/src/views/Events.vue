<script lang="ts" setup>
import TopBanner from "@/components/banners/TopBanner.vue"
import Calendar from "@/components/events-calendar/Calendar.vue"
import EventList from "@/components/events/EventList.vue"
import {
  type AdvancedCommittee,
  type Event,
  type EventSignUp,
  findCommitteesForCurrentUser,
  findEvents,
  findEventSignUps,
  type Login,
} from "@/lib"
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import {DateTime} from "luxon"

const store = useStore()
const calendarRef = ref()

const events = ref<Event[]>([])
const committees = ref<AdvancedCommittee[]>([])
const eventSignUps = ref<EventSignUp[]>([])

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed<Login>(() => store.getters.getLogin)

onMounted(async () => {
  const [eventsResp, signupsResp, committeesResp] = await Promise.all([
    findEvents({
        query: {
          from: DateTime.now().startOf("day").toISO()!,
          sort: ["startTime", "asc"],
        },
      },
    ),
    isLoggedIn.value
      ? findEventSignUps({
        query: {
          from: DateTime.now().startOf("day").toISO()!,
          userId: login.value.userId,
        },
      })
      : Promise.resolve({data: [] as EventSignUp[]}),
    isLoggedIn.value
      ? findCommitteesForCurrentUser()
      : Promise.resolve({data: [] as AdvancedCommittee[]}),
  ])

  const fetchedEvents = eventsResp.data?.content ?? []

  if (isLoggedIn.value) {
    eventSignUps.value = signupsResp.data ?? []
    committees.value = committeesResp.data as AdvancedCommittee[] ?? []
  }
  events.value = fetchedEvents
})

type RefLike<V> = { value: V }

type WithOptionalId = { id?: number }

function upsert<T extends WithOptionalId>(
  listRef: RefLike<T[] | undefined>,
  item: T,
) {
  const list = listRef.value ?? []
  const idx = list.findIndex(e => e.id === item.id)
  listRef.value =
    idx === -1
      ? [...list, item]
      : [...list.slice(0, idx), item, ...list.slice(idx + 1)]
}

function removeById<T extends WithOptionalId>(
  listRef: RefLike<T[] | undefined>,
  id: number,
) {
  const list = listRef.value ?? []
  listRef.value = list.filter(e => e.id !== id)
}

function updateEvent(event: Event) {
  upsert(events, event)
}

function deleteEvent(id: number) {
  removeById<Event>(events, id)
}

function updateSignUp(su: EventSignUp) {
  events.value.find((e: Event) => e.id === su.eventId)!.signUpCount! += 1
  upsert(eventSignUps, su)
}

function deleteSignUp(id: number) {
  const signUp = eventSignUps.value.find((es) => es.id === id)!
  events.value.find((e: Event) => e.id === signUp.eventId)!.signUpCount! -= 1
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
        <calendar ref="calendarRef" />
      </div>
      <div
        class="mx-auto mt-5"
        style="max-width: 800px"
      >
        <p class="mt-8 mx-3 mb-4 text-h3 text-center">
          Upcoming Events
        </p>
        <event-list
          :event-sign-ups="eventSignUps"
          :events="events"
          :committees="committees"
          @update:event="updateEvent"
          @delete:event="deleteEvent"
          @update:sign-up="updateSignUp"
          @delete:sign-up="deleteSignUp"
        />
      </div>
    </div>
  </v-main>
</template>
