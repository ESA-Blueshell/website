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
import {useRoute} from "vue-router"

const store = useStore()
const route = useRoute()

const login = computed<Login>(() => store.getters.getLogin)
const isBoard = computed<boolean>(() => store.getters.isBoard)

const events = ref<Event[]>([])
const committees = ref<AdvancedCommittee[]>([])
const eventSignUps = ref<EventSignUp[]>([])

const pastEvents = ref<Event[]>([])
const pastPageMeta = ref<PageMetadata>()

const isLoaded = ref(false)

const initialPage = (() => {
  const raw = Number(route.query.page ?? 1)
  return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 1
})()
const internalPage = ref<number>(initialPage)


const page = computed<number>({
  get() {
    return internalPage.value
  },
  set(p: number) {
    const next = Number.isFinite(p) && p > 0 ? Math.floor(p) : 1
    if (next === internalPage.value) return
    internalPage.value = next
    setUrlPage(next)
    void loadPast(next)
  },
})

function setUrlPage(nextPage: number) {
  const url = new URL(window.location.href)
  url.searchParams.set("page", String(nextPage))
  window.history.replaceState(window.history.state, "", url)
}

async function loadPast(pageOneIndexed = 1) {
  const pageZeroIndexed = Math.max(0, pageOneIndexed - 1)
  const resp = await findEvents({
    query: {
      to: DateTime.local().startOf("day").toISO(),
      page: pageZeroIndexed,
      size: 10,
      sort: ["startTime,desc"],
    },
  })
  pastEvents.value = resp.data?.content ?? []
  pastPageMeta.value = resp.data!.page!
}

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

    setUrlPage(internalPage.value)

    await loadPast(internalPage.value)
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

      <template v-if="isBoard">
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
        v-model="page"
        :length="pastPageMeta?.totalPages ?? 1"
        class="mx-3 mb-4"
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

<style lang="scss" scoped></style>
