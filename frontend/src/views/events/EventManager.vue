<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import TopBanner from "@/components/banners/TopBanner.vue"
import {DateTime} from "luxon"
import {
  type AdvancedCommittee,
  type Event,
  type EventSignUp,
  findCommitteesForCurrentUser,
  findEvents,
  findEventSignUps, type Login,
} from "@/lib"
import EventList from "@/components/events/EventList.vue"

const store = useStore()
const login = computed<Login>(() => store.getters.getLogin)

const events = ref<Event[] | null>(null)
const committees = ref<AdvancedCommittee[] | null>(null)
const eventSignUps = ref<EventSignUp[] | null>(null)

function updateEvent(event: Event): void {
  const list = events.value ?? []
  const idx = list.findIndex(es => es.id === event.id)
  if (idx >= 0) {
    events.value = [
      ...list.slice(0, idx),
      event,
      ...list.slice(idx + 1),
    ]
  } else {
    events.value = [...list, event]
  }
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
    eventSignUps.value = [...list, signUp]
  }
}

function deleteEvent(id: number) {
  events.value = events.value?.filter((e: Event) => e.id !== id) ?? []
}

function deleteSignUp(signUpId: number): void {
  eventSignUps.value = (eventSignUps.value ?? []).filter(
    (es: EventSignUp) => es.id !== signUpId,
  )
}

const pastEvents = ref<Event[]>([])
const noCommittees = ref(false)
const isLoaded = ref(false)

const isBoard = computed(() => store.getters.isBoard)

onMounted(async () => {
  try {
    const [
      upcomingResp,
      pastResp,
      committeesResp,
      signUpsResp,
    ] = await Promise.all([
      findEvents({
        query: {
          from: DateTime.local().startOf("day").toISO(),
        },
      }),
      findEvents({
        query: {
          from: DateTime.local().minus({months: 1}).startOf("day").toISO(),
          to: DateTime.local().startOf("day").toISO(),
        },
      }),
      findCommitteesForCurrentUser(),
      findEventSignUps({
          query: {
            from: DateTime.local().minus({months: 1}).startOf("day").toISO(),
            userId: login.value.userId,
          },
        },
      ),
    ])

    events.value = upcomingResp.data?.content ?? []
    pastEvents.value = pastResp.data?.content ?? []
    committees.value = committeesResp.data as AdvancedCommittee[] ?? []
    eventSignUps.value = signUpsResp.data ?? []
    if (committees.value.length === 0) {
      noCommittees.value = true
    }
  } finally {
    // Once everything is done, mark as loaded
    isLoaded.value = true
  }
})
</script>
<template>
  <!-- Render a loading indicator (or whatever you prefer) until data is fetched -->
  <v-main v-if="isLoaded">
    <top-banner title="Event Manager" />

    <div
      class="mx-auto my-10"
      style="max-width: 800px"
    >
      <v-btn
        :disabled="noCommittees"
        block
        class="mx-3"
        to="create"
      >
        Create new event
      </v-btn>

      <!-- Only show non-public events if user is board AND there are non-approved events -->
      <template v-if="isBoard && events.filter((e: Event) => !e.approved).length > 0">
        <p class="mt-8 mx-3 text-h3 text-center">
          Non-public events (to be approved)
        </p>
        <event-list
          :committees="committees"
          :event-sign-ups="eventSignUps"
          :events="events.filter((e: Event) => !e.approved)"
          @delete:event="deleteEvent"
          @update:event="updateEvent"
          @delete:sign-up="deleteSignUp"
          @update:sign-up="updateSignUp"
        />
      </template>

      <p class="mt-8 mx-3 mb-4 text-h3 text-center">
        Upcoming Events
      </p>
      <event-list
        :committees="committees"
        :event-sign-ups="eventSignUps"
        :events="events.filter((e: Event) => e.approved)"
        @delete:event="deleteEvent"
        @update:event="updateEvent"
        @delete:sign-up="deleteSignUp"
        @update:sign-up="updateSignUp"
      />

      <p class="mt-8 mx-3 mb-4 text-h3 text-center">
        Past Events
      </p>
      <event-list
        :committees="committees"
        :event-sign-ups="eventSignUps"
        :events="pastEvents"
      />

      <p
        v-if="events.length === 0"
        class="mx-3 text-h5"
      >
        Doesn't look like you have any upcoming events for your committees 😔😔😔
        maybe create one? or two?
      </p>

      <v-img
        v-if="noCommittees"
        :src="myRequire('../@/assets/noCommittees.jpg')"
      />
    </div>
  </v-main>

  <!-- Show a loading indicator (or placeholder) while fetching data -->
  <div
    v-else
    class="text-center mt-8"
  >
    <v-progress-circular indeterminate />
    <!-- or you can just show some text: "Loading events..." -->
  </div>
</template>


<style lang="scss" scoped>

</style>
