<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import {DateTime} from "luxon"
import BandRule from "@/components/island/BandRule.vue"
import BackBar from "@/components/island/BackBar.vue"
import CutButton from "@/components/island/CutButton.vue"
import Island from "@/components/island/Island.vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import PagePlaceholder from "@/components/island/PagePlaceholder.vue"
import store from "@/plugins/store"
import {tabTitle} from "@/plugins/tabTitle"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  downloadIcs,
  type EventRoster,
  type EventResponse,
  type EventSignUpResponse,
  listEvents,
  pageUrlOf,
  readEvent,
  readRoster,
  useEventReader,
} from "@/domains/events"
import {PeopleList} from "@/domains/discord"
import AlsoComingUp from "@/domains/events/island/AlsoComingUp.vue"
import EventActions from "@/domains/events/island/EventActions.vue"
import EventBand from "@/domains/events/island/EventBand.vue"
import EventSignUpPanel from "@/domains/events/island/EventSignUpPanel.vue"
import {useIsBoard} from "@/composables/useIsBoard"

defineOptions({name: "EventPage"})

const route = useRoute()
const router = useRouter()

const event = ref<EventResponse | null>(null)
const missing = ref(false)
const coming = ref<EventResponse[]>([])
const roster = ref<EventRoster | null>(null)
const {signUps, committees} = useEventReader()

const id = computed<number>(() => Number(route.params.id))

async function read() {
  missing.value = false
  try {
    const found = await readEvent(id.value)
    event.value = found ?? null
    missing.value = found == null
    if (found) void readWhoSignedUp(found)
  } catch (error) {
    missing.value = true
    $handleNetworkError(error)
  }
}

// Read again after the reader signs up or out, so their own face comes and goes with it.
async function readWhoSignedUp(found: EventResponse) {
  roster.value = found.signUp ? await readRoster(found.id) : null
}

async function readComing() {
  try {
    coming.value = await listEvents({from: DateTime.now().toISO()!, sort: ["startTime", "asc"]})
  } catch {
    coming.value = []
  }
}

onMounted(() => {
  void read()
  void readComing()
})
watch(id, () => void read())
// Served tags cover only the first load.
watch(event, (found) => {
  if (found) document.title = tabTitle(found.title)
})
const countOf = (on: EventRoster) => on.people.length + on.guests
const guestsLine = (on: EventRoster) => {
  const counted = `${on.guests} ${on.guests === 1 ? "guest" : "guests"} without an account.`
  return on.people.length ? `And ${counted}` : counted.charAt(0).toUpperCase() + counted.slice(1)
}

const committee = computed(() => committees.value.find(one => one.id === event.value?.committeeId)?.name)
const eyebrow = computed(() => committee.value ?? "Blueshell event")
const signUp = computed(() => signUps.value.find(one => one.eventId === event.value?.id))
const others = computed(() => coming.value.filter(one => one.id !== id.value))
const isBoard = useIsBoard()
const manages = computed<boolean>(() =>
  isBoard.value || committees.value.some(one => one.id === event.value?.committeeId))

async function copyLink() {
  await navigator.clipboard.writeText(pageUrlOf(event.value!))
  store.commit("setStatusSnackbarMessage", `Link for ${event.value!.title} copied`)
}

function signedUp(saved: EventSignUpResponse) {
  const known = signUps.value.some(one => one.id === saved.id)
  signUps.value = known ? signUps.value.map(one => (one.id === saved.id ? saved : one)) : [...signUps.value, saved]
  if (!known) event.value = {...event.value!, signUpCount: event.value!.signUpCount + 1}
  void readWhoSignedUp(event.value!)
}

function signedOut(signUpId: number) {
  signUps.value = signUps.value.filter(one => one.id !== signUpId)
  event.value = {...event.value!, signUpCount: Math.max(event.value!.signUpCount - 1, 0)}
  void readWhoSignedUp(event.value!)
}
</script>

<template>
  <v-main>
    <island
      class="event-page"
      testid="event-island"
    >
      <back-bar
        label="All events"
        testid="event-page-back"
        to="/events"
      />

      <template v-if="event">
        <div
          v-if="manages"
          class="event-page__organiser"
          data-testid="event-organiser"
        >
          <div class="event-page__wrap event-page__organiser-row">
            <p class="event-page__organiser-note">
              You organise this event
            </p>
            <event-actions
              :committees="committees"
              :event="event"
              manage-only
              @delete:event="router.push('/events')"
              @update:event="event = $event"
            />
          </div>
        </div>

        <event-band
          :event="event"
          :eyebrow="eyebrow"
          full
          heading="h1"
        >
          <template #actions>
            <cut-button
              v-if="event.signUp && !signUp"
              href="#signup"
              testid="event-page-signup"
              tone="solid"
            >
              Sign up
            </cut-button>
            <cut-button
              testid="event-page-ics"
              @click="downloadIcs(event)"
            >
              Add to my calendar
            </cut-button>
            <cut-button
              testid="event-page-copy"
              tone="quiet"
              @click="copyLink"
            >
              Copy link
            </cut-button>
          </template>
        </event-band>

        <band-rule />

        <section class="event-page__wrap event-page__body">
          <div>
            <p class="event-page__eyebrow">
              About this event
            </p>
            <markdown-view
              class="event-page__prose"
              data-testid="event-page-description"
              :source="event.description ?? ''"
            />
            <section
              v-if="roster && countOf(roster) > 0"
              class="event-page__roster"
              data-testid="event-roster"
            >
              <p class="event-page__eyebrow">
                Signed up · {{ countOf(roster) }}
              </p>
              <people-list
                one-row
                :people="roster.people"
                testid="event-roster-people"
              />
              <p
                v-if="roster.guests > 0"
                class="event-page__guests"
                data-testid="event-roster-guests"
              >
                {{ guestsLine(roster) }}
              </p>
            </section>
          </div>
          <event-sign-up-panel
            :event="event"
            :sign-up="signUp"
            @delete:sign-up="signedOut"
            @update:sign-up="signedUp"
          />
        </section>

        <also-coming-up
          :events="others.slice(0, 3)"
          :total="others.length"
        />
      </template>

      <section
        v-else-if="missing"
        class="event-page__wrap event-page__missing"
        data-testid="event-page-missing"
      >
        <h1 class="event-page__missing-title">
          No such event
        </h1>
        <p class="event-page__eyebrow">
          It may have been taken down, or the link is not quite right.
        </p>
        <cut-button
          href="/events"
          tone="solid"
        >
          See what is on
        </cut-button>
      </section>
      <page-placeholder
        v-else
        testid="event-placeholder"
      />
    </island>
  </v-main>
</template>

<style scoped>

/* The island root fills a page; the Vuetify main around it already does. */
.event-page {
  min-height: 0;
}

.event-page__roster {
  margin-top: 2rem;
}

.event-page__guests {
  margin-top: 0.6rem;
  font-size: 0.92rem;
  color: var(--color-ash);
}

.event-page__wrap {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto;
  padding: 0 2rem;
}

.event-page__organiser {
  background-color: var(--band-ground);
}

.event-page__organiser-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  padding-top: 0.5rem;
  padding-bottom: 0.5rem;
}

.event-page__organiser-note {
  margin-right: auto;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.event-page__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 26rem;
  gap: 4.5rem;
  align-items: start;
  padding-top: 3rem;
  padding-bottom: 3.5rem;
}

.event-page__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.event-page__prose {
  max-width: 42rem;
  margin-top: 1rem;
  font-size: 1.04rem;
  line-height: 1.7;
  color: color-mix(in oklab, var(--color-chalk) 86%, transparent);
}

.event-page__missing {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  padding-top: 3rem;
  padding-bottom: 5rem;
}

.event-page__missing-title {
  font-family: var(--font-display);
  font-size: 3rem;
  text-transform: uppercase;
}

@media (--phone) {
  .event-page__wrap {
    padding: 0 1.25rem;
  }

  .event-page__body {
    grid-template-columns: 1fr;
    gap: 2rem;
    padding-top: 1.75rem;
    padding-bottom: 2.5rem;
  }
}
</style>
