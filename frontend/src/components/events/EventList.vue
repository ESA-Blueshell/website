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
  findEventSignUpsForCurrentUser,
} from "@/lib"
import {DateTime} from "luxon"

const events = ref<Event[] | null>(null)
const committees = ref<AdvancedCommittee[] | null>(null)
const eventSignups = ref<EventSignUp[] | null>(null)

// Access route and store
const route = useRoute()
const store = useStore()

// Computed helpers
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)

onMounted(async () => {
  const [eventsResp, signupsResp, committeesResp] = await Promise.all([
    findEvents({
        query: {
          from: DateTime.now().startOf("day").toISO()!,
          sort: ["startTime", "asc"]
        },
      },
    ),
    isLoggedIn.value
      ? findEventSignUpsForCurrentUser()
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
          @deleted="(id: number) => events = events.filter((e: Event) => e.id !== id)"
        />
        <!-- only show divider when there's another item after -->
        <v-divider v-if="i < events.length - 1" />
      </template>
    </v-list>
  </v-expand-transition>
</template>
<style lang="scss" scoped>
</style>
