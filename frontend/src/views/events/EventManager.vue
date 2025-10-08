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
  findEventSignUps,
  type Login,
  type PageMetadata,
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

const PAST_PAGE_SIZE = 10
const pastEvents = ref<Event[]>([])
const pastPage = ref(1) // 1-based for the UI
const pastPageMeta = ref<PageMetadata | null>(null)

const noCommittees = ref(false)
const isLoaded = ref(false)

const isBoard = computed(() => store.getters.isBoard)

async function loadPastEventsPage(pageOneIndexed = 1) {
  const pageZeroIndexed = Math.max(0, pageOneIndexed - 1)

  const resp = await findEvents({
    query: {
      to: DateTime.local().startOf("day").toISO(),
      page: pageZeroIndexed,
      size: PAST_PAGE_SIZE,
      sort: ["startTime,desc"],
    },
  })

  pastEvents.value = resp.data?.content ?? []
  pastPageMeta.value = resp.data!.page!
  pastPage.value = (pastPageMeta.value.number ?? 0) + 1
}

onMounted(async () => {
  try {
    const [
      upcomingResp,
      committeesResp,
      signUpsResp,
    ] = await Promise.all([
      findEvents({
        query: {
          from: DateTime.local().startOf("day").toISO(),
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
    committees.value = committeesResp.data as AdvancedCommittee[] ?? []
    eventSignUps.value = signUpsResp.data ?? []
    if ((committees.value?.length ?? 0) === 0) {
      noCommittees.value = true
    }

    await loadPastEventsPage(1)
  } finally {
    isLoaded.value = true
  }
})
</script>

<template>
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

      <template v-if="isBoard">
        <v-divider class="mt-5 my-3" />
        <p class="mt-8 mx-3 text-h3 text-center">
          Non-public events (to be approved)
        </p>
        <template v-if="noCommittees">
          <h3 class="text-center">
            There are no unapproved events.
          </h3>
        </template>
        <template v-else>
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
      </template>

      <v-divider class="mt-5 my-3" />

      <p class="mx-3 mb-4 text-h3 text-center">
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

      <v-divider class="my-3" />

      <p class="mx-3 mb-2 text-h3 text-center">
        Past Events
      </p>

      <v-divider class="my-3" />

      <v-pagination
        v-if="(pastPageMeta?.totalPages ?? 1) > 1"
        v-model="pastPage"
        :length="pastPageMeta?.totalPages ?? 1"
        class="mx-3 mb-4"
        @update:model-value="(p: number) => loadPastEventsPage(p)"
      />

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

  <div
    v-else
    class="text-center mt-8"
  >
    <v-progress-circular indeterminate />
  </div>
</template>

<style lang="scss" scoped>
</style>
