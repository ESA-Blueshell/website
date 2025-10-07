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

  // Assign signups first, as otherwise event rendering happens before they're available
  if (isLoggedIn.value) {
    eventSignUps.value = signupsResp.data ?? []
    committees.value = committeesResp.data as AdvancedCommittee[] ?? []
  }
  events.value = fetchedEvents
})

function deleteSignUp(signUpId: number): void {
  const signUp = eventSignUps.value.find((es) => es.id === signUpId)!
  events.value.find((e: Event) => e.id === signUp.eventId)!.signUpCount! -= 1
  eventSignUps.value = (eventSignUps.value ?? []).filter(
    (es: EventSignUp) => es.id !== signUpId,
  )
}

function updateSignUp(signUp: EventSignUp): void {
  const list = eventSignUps.value ?? []
  const idx = list.findIndex(es => es.id === signUp.id)
  if (idx >= 0) {
    eventSignUps.value = [
      ...list.slice(0, idx),
      signUp,
      ...list.slice(idx + 1),
    ]
  } else {
    events.value.find((e: Event) => e.id === signUp.eventId)!.signUpCount! += 1
    eventSignUps.value = [...list, signUp]
  }
}

function updateEvent(event: Event): void {
  const list = events.value
  const idx = list.findIndex(e => e.id === event.id)
  if (idx >= 0) {
    events.value = [
      ...list.slice(0, idx),
      event,
      ...list.slice(idx + 1),
    ]
  } else {
    events.value = [...list, event]
  }
  calendarRef.value?.updateEvent(event)
}

function deleteEvent(id: number) {
  events.value = events.value?.filter((e: Event) => e.id !== id) ?? []
  calendarRef.value?.deleteEvent?.(id)
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
