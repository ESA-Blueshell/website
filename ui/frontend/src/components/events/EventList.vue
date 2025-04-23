<script setup lang="ts">
import {ref, onMounted, computed} from 'vue'
import {useStore} from 'vuex'
import {useRoute} from 'vue-router'
import EventListItem from '@/components/events/EventListItem.vue'

// Services
import EventService from '@/services/EventService'
import EventSignUpService from '@/services/EventSignUpService'

// Types
import type EventModel from '@/models/EventModel.ts'
import type {EventSignUpModel} from '@/models'

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
  const [ fetchedEvents, signups ] = await Promise.all([
    eventService.getUpcomingEvents(),
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
      <event-list-item
        v-for="event in events"
        :key="event.id"
        :event="event"
        :sign-up="eventSignups[event.id as number]"
        style="scroll-margin-top: 64px;"
      />
    </v-list>
  </v-expand-transition>
</template>
<style scoped>
.list-item-buffer {
  /* Spacing around your list items */
  padding: 8px 0;
}

.form-border {
  border-width: 1px;
  border-color: rgb(var(--v-theme-accent));
  border-style: solid;
}

.form {
  padding: 16px;
}
</style>
