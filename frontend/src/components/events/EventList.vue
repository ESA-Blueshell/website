<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useStore} from 'vuex'
import {useRoute} from 'vue-router'
import EventListItem from '@/components/events/EventListItem.vue'

// Services
import EventService from '@/services/EventService'
import EventSignUpService from '@/services/EventSignUpService'

// Types
import type EventModel from '@/models/EventModel.ts'
import type {EventSignUpModel} from '@/models'
import {DateTime} from "luxon";

// Track loaded events: null => not loaded yet, [] => loaded but empty
const events = ref<EventModel[] | null>(null)

// Map of eventId => formAnswers or `undefined` if not signed up
const eventSignups = ref<Record<number, EventSignUpModel>>({})

// Services
const eventService = new EventService()
const signUpService = new EventSignUpService()

// Access route and store
const route = useRoute()
const store = useStore()

// Computed helpers
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)

onMounted(async () => {
  const [fetchedEvents, signups] = await Promise.all([
    eventService.getEvents(DateTime.local().startOf('day').toISO(), undefined),
    isLoggedIn.value
      ? signUpService.getSignups()
      : Promise.resolve([]),
  ])

  // Assign signups first, as otherwise event rendering happens before they're available
  if (isLoggedIn.value) {
    eventSignups.value = Object.fromEntries(
      (signups as EventSignUpModel[]).map(s => [s.eventId as number, s])
    )
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
          :sign-up="eventSignups[event.id]"
          class="event-list-item"
        />
        <!-- only show divider when there's another item after -->
        <v-divider v-if="i < events.length - 1"/>
      </template>
    </v-list>
  </v-expand-transition>
</template>
<style lang="scss" scoped>
</style>
