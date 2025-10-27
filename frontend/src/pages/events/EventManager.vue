<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {DateTime} from "luxon"
import {
  type AdvancedCommittee,
  type Event,
  type EventSignUp,
  findCommitteesForCurrentUser,
  findEvents,
  findEventSignUps,
  type Login,
} from "@/services/api"
import EventList from "@/components/common/lists/EventList.vue"
import PastEventsPane from "@/components/base/PastEventsPane.vue"

const store = useStore()

const login = computed<Login>(() => store.getters.getLogin)

const events = ref<Event[]>([])
const committees = ref<AdvancedCommittee[]>([])
const eventSignUps = ref<EventSignUp[]>([])

const isLoaded = ref(false)

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

onMounted(async () => {
  try {
    const [upcomingResp, committeesResp, signUpsResp] = await Promise.all([
      findEvents({query: {from: DateTime.local().startOf("day").toISO()}}),
      findCommitteesForCurrentUser(),
      findEventSignUps({
        query: {
          from: DateTime.local().minus({months: 1}).startOf("day").toISO(),
          userId: login.value.userId,
        },
      }),
    ])

    events.value = upcomingResp.data?.content ?? []
    committees.value = (committeesResp.data as AdvancedCommittee[]) ?? []
    eventSignUps.value = signUpsResp.data ?? []
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
        :disabled="!committees.length"
        block
        class="mx-3"
        to="create"
      >
        Create new event
      </v-btn>

      <v-divider class="mt-5 my-3" />
      <p class="mt-8 mx-3 text-h3 text-center">
        Non-public events (to be approved)
      </p>
      <template v-if="!committees.length">
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

      <p
        v-if="events.length === 0"
        class="mx-3 text-h5"
      >
        Doesn't look like you have any upcoming events for your committees 😔😔😔
        maybe create one? or two?
      </p>

      <v-divider class="my-3" />

      <past-events-pane
        :committees="committees"
        :event-sign-ups="eventSignUps"
      />

      <v-img
        v-if="!committees.length"
        :src="$require('@/assets/noCommittees.jpg')"
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
