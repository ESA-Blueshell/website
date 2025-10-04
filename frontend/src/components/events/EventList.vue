<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import {useRoute} from "vue-router"
import EventListItem from "@/components/events/EventListItem.vue"
import {
  type AdvancedCommittee,
  type Event,
  type EventSignUp,
  findCommitteesForCurrentUser,
  findEvents,
  findEventSignUps, type Login,
} from "@/lib"
import {DateTime} from "luxon"

const emit = defineEmits<{
  (e: "deleted", id: number): void
}>()

const events = ref<Event[] | null>(null)
const committees = ref<AdvancedCommittee[] | null>(null)
const eventSignups = ref<EventSignUp[] | null>(null)

// Access route and store
const route = useRoute()
const store = useStore()

// Computed helpers
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
          userId: login.value.userId
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
    eventSignups.value = signupsResp.data ?? []
    committees.value = committeesResp.data as AdvancedCommittee[] ?? []
  }
  events.value = fetchedEvents
})

function handleSignUpDelete(signUpId: number): void {
  eventSignups.value = (eventSignups.value ?? []).filter(
    (es: EventSignUp) => es.id !== signUpId,
  )
}


function handleSignUpUpdate(signUp: EventSignUp): void {
  const list = eventSignups.value ?? []
  const idx = list.findIndex(es => es.id === signUp.id)
  if (idx >= 0) {
    eventSignups.value = [
      ...list.slice(0, idx),
      signUp,
      ...list.slice(idx + 1),
    ]
  } else {
    eventSignups.value = [...list, signUp]
  }
}

function deleteEvent(id: number) {
  events.value = events.value?.filter((e: Event) => e.id !== id) ?? []
  emit("deleted", id)
}

</script>
<template>
  <!-- 1) If events is null, we're still loading -->
  <v-progress-circular
    v-if="!events"
    indeterminate
  />

  <v-expand-transition
    v-else
    :disabled="!!route.hash"
  >
    <!-- If empty -->
    <div v-if="events.length === 0">
      <p>No upcoming events found</p>
    </div>

    <!-- Otherwise, we have events -->
    <v-list v-if="events.length > 0">
      <template
        v-for="(event, i) in events"
        :key="event.id"
      >
        <event-list-item
          :event="event"
          :committees="committees"
          :sign-ups="eventSignups"
          class="event-list-item"
          @deleted="deleteEvent"
          @sign-up:updated="handleSignUpUpdate"
          @sign-up:deleted="handleSignUpDelete"
        />
        <!-- only show divider when there's another item after -->
        <v-divider v-if="i < events.length - 1" />
      </template>
    </v-list>
  </v-expand-transition>
</template>
<style lang="scss" scoped>
</style>
