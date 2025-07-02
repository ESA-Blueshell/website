<template>
  <!-- Render a loading indicator (or whatever you prefer) until data is fetched -->
  <v-main v-if="isLoaded">
    <top-banner title="EventModel Manager"/>

    <div
      class="mx-auto my-10"
      style="max-width: 800px"
    >
      <v-btn
        class="mx-3"
        block
        :disabled="noCommittees"
        to="create"
      >
        Create new event
      </v-btn>

      <!-- Only show non-public events if user is board AND there are non-visible events -->
      <template v-if="isBoard && events.filter(e => !e.visible).length > 0">
        <p class="mt-8 mx-3 text-h3 text-center">
          Non-public events (to be approved)
        </p>
        <event-manage-list
          :initial-events="events.filter(e => !e.visible)"
          :id-to-committee="idToCommittee"
        />
      </template>

      <p class="mt-8 mx-3 mb-4 text-h3 text-center">
        Upcoming Events
      </p>
      <event-manage-list
        :initial-events="events.filter(e => e.visible)"
        :id-to-committee="idToCommittee"
      />

      <p class="mt-8 mx-3 mb-4 text-h3 text-center">
        Past Events
      </p>
      <event-manage-list
        :initial-events="pastEvents"
        :id-to-committee="idToCommittee"
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
    <v-progress-circular indeterminate/>
    <!-- or you can just show some text: "Loading events..." -->
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useStore} from 'vuex'
import TopBanner from '@/components/banners/TopBanner.vue'
import EventManageList from '@/components/events/EventManageList.vue'
import {CommitteeService, EventService} from '@/services'
import {$require} from '@/plugins/require'
import type {CommitteeModel, EventModel} from "@/models";
import {DateTime} from 'luxon';

// Local “groupBy” helper if you don't have a built-in one
function groupBy<T, K extends keyof T & (string | number)>(
  items: T[],
  key: K                      // which property is the id?
): Record<T[K], T> {
  return items.reduce((acc, item) => {
    acc[item[key]] = item
    return acc
  }, {} as Record<T[K], T>)
}

// Reactive references for data
const events = ref<EventModel[]>([])
const pastEvents = ref<CommitteeModel[]>([])
const idToCommittee = ref<Record<number, CommitteeModel>>({})
const noCommittees = ref(false)
// Track if data has finished loading
const isLoaded = ref(false)

// Replace store references from template with a computed
const store = useStore()
const isBoard = computed(() => store.getters.isBoard)

// Provide a Composition API wrapper around $require
function myRequire(path: string) {
  return $require(path)
}

onMounted(async () => {
  try {
    const eventService = new EventService()
    const committeeService = new CommitteeService()

    const [upcoming, past, committees] = await Promise.all([
      eventService.getEvents(DateTime.local().startOf('day').toISO(), undefined),
      eventService.getEvents(
        DateTime.local().minus({months: 1}).startOf('day').toISO(),
        DateTime.local().startOf('day').toISO(),
      ),
      committeeService.getCommittees(false),
    ])

    events.value = upcoming
    pastEvents.value = past

    idToCommittee.value = groupBy(committees, 'id')
    if (committees.length === 0) {
      noCommittees.value = true
    }
  } finally {
    // Once everything is done, mark as loaded
    isLoaded.value = true
  }
})
</script>

<style scoped>
/* Your styles here, if needed */
</style>
