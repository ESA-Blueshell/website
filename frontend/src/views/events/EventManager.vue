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

      <!-- Only show non-public events if user is board AND there are non-visible events -->
      <template v-if="isBoard && events.filter((e: Event) => !e.visible).length > 0">
        <p class="mt-8 mx-3 text-h3 text-center">
          Non-public events (to be approved)
        </p>
        <event-manage-list
          :initial-committees="committees"
          :initial-events="events.filter((e: Event) => !e.visible)"
        />
      </template>

      <p class="mt-8 mx-3 mb-4 text-h3 text-center">
        Upcoming Events
      </p>
      <event-manage-list
        :initial-committees="committees"
        :initial-events="events.filter((e: Event) => e.visible)"
      />

      <p class="mt-8 mx-3 mb-4 text-h3 text-center">
        Past Events
      </p>
      <event-manage-list
        :initial-committees="committees"
        :initial-events="pastEvents"
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

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import TopBanner from "@/components/banners/TopBanner.vue"
import EventManageList from "@/components/events/EventManageList.vue"
import {$require} from "@/plugins/require"
import {DateTime} from "luxon"
import {type AdvancedCommittee, type Event, findCommittees, findEvents} from "@/lib"

// Reactive references for data
const events = ref<Event[]>([])
const committees = ref<AdvancedCommittee[]>([])
const pastEvents = ref<Event[]>([])
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
    const [upcomingResp, pastResp, committeesResp] = await Promise.all([
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
      findCommittees(),
    ])

    events.value = upcomingResp.data?.content ?? []
    pastEvents.value = pastResp.data?.content ?? []
    committees.value = committeesResp.data as AdvancedCommittee[] ?? []
    console.log("committees: ", committees.value)
    if (committees.value.length === 0) {
      noCommittees.value = true
    }
  } finally {
    // Once everything is done, mark as loaded
    isLoaded.value = true
  }
})
</script>

<style lang="scss" scoped>

</style>
