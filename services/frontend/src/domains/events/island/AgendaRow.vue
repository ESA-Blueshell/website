<script lang="ts" setup>
import {computed, ref} from "vue"
import {DateTime} from "luxon"
import PosterArt from "@/components/island/PosterArt.vue"
import {srcsetOf} from "@/components/island/pictures"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"
import store from "@/plugins/store"
import type {EventResponse, EventSignUpResponse} from ".."
import EventActions from "./EventActions.vue"
import {placesOf, posterOf, signUpStateOf, whenOf} from "./eventFacts"

/**
 * One event in the agenda: its day, a thumbnail of its poster, what and where, whether it can be
 * signed up for, and the way to. The sign-up form opens under the row.
 */
defineOptions({name: "AgendaRow"})

type CommitteeOption = {id: number, name: string}

const {event, signUps = [], committees = []} = defineProps<{
  event: EventResponse
  signUps?: EventSignUpResponse[]
  committees?: CommitteeOption[]
}>()

const emit = defineEmits<{
  "update:event": [event: EventResponse]
  "delete:event": [id: number]
  "update:signUp": [signUp: EventSignUpResponse]
  "delete:signUp": [id: number]
}>()

const signing = ref(false)
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)

const starts = computed(() => DateTime.fromISO(event.startTime))
const poster = computed(() => posterOf(event))
const when = computed(() => whenOf(event))
const committee = computed(() => committees.find(one => one.id === event.committeeId)?.name ?? "")
const meta = computed(() => [when.value.hours, event.location, committee.value].filter(Boolean).join(" · "))
const state = computed(() => signUpStateOf(event))
const places = computed(() => (event.signUp ? placesOf(event).said : ""))
const signUp = computed(() => signUps.find(one => one.eventId === event.id))

function signedUp(saved: EventSignUpResponse) {
  signing.value = false
  emit("update:signUp", saved)
}

function signedOut(id: number) {
  signing.value = false
  emit("delete:signUp", id)
}
</script>

<template>
  <div
    :id="String(event.id)"
    class="row"
    :data-testid="`event-card-${event.id}`"
  >
    <div class="row__line">
      <span class="row__date">
        <span class="row__day">{{ starts.toFormat("d") }}</span>
        <span class="row__weekday">{{ starts.toFormat("ccc") }}</span>
      </span>
      <poster-art
        :banner="poster?.url"
        class="row__art"
        sizes="5.5rem"
        :srcset="poster ? srcsetOf(poster) : undefined"
        :title="event.title"
      />
      <span class="row__main">
        <span class="row__title">{{ event.title }}</span>
        <span class="row__meta">{{ meta }}</span>
      </span>
      <span class="row__state">
        <span>{{ state }}</span>
        <span v-if="places && places !== state">{{ places }}</span>
        <span
          v-if="event.membersOnly"
          class="row__tag"
        >Members only</span>
      </span>
      <span class="row__act">
        <event-actions
          v-model:signing="signing"
          :committees="committees"
          :event="event"
          :sign-ups="signUps"
          @delete:event="emit('delete:event', $event)"
          @delete:sign-up="signedOut"
          @update:event="emit('update:event', $event)"
        />
      </span>
    </div>

    <event-sign-up-form
      v-if="signing"
      class="row__form"
      :event="event"
      :initial-sign-up="signUp"
      :show-guest-form="!isLoggedIn"
      @delete:sign-up="signedOut"
      @update:sign-up="signedUp"
    />
  </div>
</template>

<style scoped>
.row {
  position: relative;
  background-color: var(--band-ground);
  transition: background-color 220ms ease;
}

.row:hover {
  background-color: color-mix(in oklab, var(--color-surface) 94%, transparent);
}

/* The mark on the house lean, rising under the pointer. */
.row::before {
  content: "";
  position: absolute;
  top: 0.7rem;
  bottom: 0.7rem;
  left: 0.55rem;
  width: 3px;
  background: var(--color-brand);
  transform: skewX(-12deg);
  scale: 1 0;
  transition: scale 320ms var(--ease-out-quint);
}

.row:hover::before {
  scale: 1 1;
}

.row__line {
  display: grid;
  grid-template-columns: 4.5rem 5.5rem minmax(0, 1fr) 13rem auto;
  gap: 0 1.5rem;
  align-items: center;
  padding: 0.85rem 1.25rem 0.85rem 1.6rem;
}

.row__date {
  display: flex;
  flex-direction: column;
  line-height: 1;
}

.row__day {
  font-family: var(--font-display);
  font-size: 2.4rem;
  color: var(--color-chalk);
}

.row__weekday {
  margin-top: 0.3rem;
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.row__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.row__title {
  font-family: var(--font-display);
  font-size: 1.3rem;
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
  overflow-wrap: break-word;
}

.row__meta {
  margin-top: 0.35rem;
  font-size: 0.92rem;
  color: var(--color-ash);
}

.row__state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.4rem;
  font-size: 0.88rem;
  color: var(--color-ash);
}

.row__tag {
  padding: 0.2rem 0.5rem;
  font-size: 0.64rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-ash);
  white-space: nowrap;
  border: 1px solid currentColor;
}

.row__act {
  justify-self: end;
}

.row__form {
  padding: 0 1.25rem 1.25rem 1.6rem;
}

@media (prefers-reduced-motion: reduce) {
  .row,
  .row::before {
    transition: none;
  }
}

/* On a phone the state and the actions drop under the title, and nothing is hidden. */
@media (max-width: 767px) {
  .row__line {
    grid-template-columns: 3rem 4.25rem minmax(0, 1fr);
    gap: 0 0.85rem;
    padding: 0.75rem 0.9rem 0.75rem 1.1rem;
  }

  .row__day {
    font-size: 1.8rem;
  }

  .row__title {
    font-size: 1rem;
  }

  .row__meta {
    font-size: 0.82rem;
  }

  .row__state,
  .row__act {
    grid-column: 3;
    margin-top: 0.45rem;
  }

  .row__state {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    font-size: 0.8rem;
  }

  .row__act {
    justify-self: start;
  }

  .row__form {
    padding: 0 0.9rem 1rem;
  }
}
</style>
