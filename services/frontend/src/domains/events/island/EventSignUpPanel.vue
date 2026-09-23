<script lang="ts" setup>
import {computed, ref} from "vue"
import {DateTime} from "luxon"
import CutButton from "@/components/island/CutButton.vue"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"
import store, {type GuestSessionData} from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {type EventResponse, type EventSignUpResponse, withdrawSignUp} from ".."
import {downloadIcs} from "./eventCalendar"
import {whenOf} from "./eventFacts"

/**
 * Signing up to an event on its own page. A visitor gets the guest form and the event's own
 * questions, a member only the questions, and somebody going is told so, with the calendar
 * file and the way out. Where signing up is closed the panel says why instead.
 */
defineOptions({name: "EventSignUpPanel"})

const {event, signUp = undefined} = defineProps<{
  event: EventResponse
  /** The reader's own sign-up to this event, where they have one. */
  signUp?: EventSignUpResponse
}>()

const emit = defineEmits<{"update:signUp": [signUp: EventSignUpResponse]; "delete:signUp": [id: number]}>()

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const isMember = computed<boolean>(() => store.getters.isMember)
const hasForm = computed<boolean>(() => (event.signUpForm?.questions?.length ?? 0) > 0)
const going = computed<boolean>(() => signUp?.id !== undefined)
const changing = ref(false)
const withdrawing = ref(false)

const left = computed<number | null>(() =>
  (event.signUpLimit == null ? null : Math.max(event.signUpLimit - event.signUpCount, 0)))

/* Why nobody new can sign up, where nobody can; somebody already going can still change it. */
const closed = computed<string | null>(() => {
  if (!event.approved) return "This event is waiting for the board's approval."
  if (DateTime.fromISO(event.startTime) < DateTime.now()) return "This event has started."
  if (going.value) return null
  if (event.membersOnly && !isMember.value) return "This event is for members. Ask the board on our Discord if you would like to come along."
  if (event.signUpDeadline && DateTime.fromISO(event.signUpDeadline) < DateTime.now()) return "Sign-ups have closed."
  if (left.value === 0) return "This event is full."
  return null
})

const line = computed<string>(() => {
  const places = left.value === null ? "" : `${left.value} place${left.value === 1 ? "" : "s"} left. `
  return isLoggedIn.value
    ? `${places}Signing up with your account.`
    : `${places}Anybody can come; you do not need an account. We email you a link to change or withdraw your sign-up.`
})

const when = computed(() => whenOf(event))

function saved(one: EventSignUpResponse) {
  changing.value = false
  emit("update:signUp", one)
}

function removed(id: number) {
  changing.value = false
  emit("delete:signUp", id)
}

async function withdraw() {
  withdrawing.value = true
  try {
    const token = (store.getters.getGuestData as GuestSessionData | null)?.accessToken ?? null
    await withdrawSignUp(signUp!.id, token)
    emit("delete:signUp", signUp!.id)
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    withdrawing.value = false
  }
}
</script>

<template>
  <aside
    id="signup"
    class="panel"
    data-testid="event-signup-panel"
  >
    <template v-if="!event.signUp">
      <h2 class="panel__title">
        Just walk in
      </h2>
      <p class="panel__line">
        No sign-ups for this one: come along on the day.
      </p>
    </template>

    <template v-else-if="going && !changing">
      <div class="panel__going">
        <svg
          aria-hidden="true"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          viewBox="0 0 24 24"
        ><path d="M5 12.5 10 17.5 19 7" /></svg>
        <h2 class="panel__title">
          You are going
        </h2>
      </div>
      <p class="panel__line">
        {{ when.day }}, {{ when.hours }}<template v-if="event.location">
          at {{ event.location }}
        </template>.
      </p>
      <div class="panel__actions">
        <cut-button
          testid="event-panel-ics"
          @click="downloadIcs(event)"
        >
          Add to my calendar
        </cut-button>
        <cut-button
          v-if="hasForm"
          testid="event-panel-change"
          tone="quiet"
          @click="changing = true"
        >
          Change my answers
        </cut-button>
        <cut-button
          v-else
          :disabled="withdrawing"
          testid="event-panel-withdraw"
          tone="quiet"
          @click="withdraw"
        >
          Withdraw
        </cut-button>
      </div>
    </template>

    <template v-else-if="closed">
      <h2 class="panel__title">
        Sign up
      </h2>
      <p
        class="panel__line"
        data-testid="event-panel-closed"
      >
        {{ closed }}
      </p>
    </template>

    <template v-else>
      <h2 class="panel__title">
        Sign up
      </h2>
      <p class="panel__line">
        {{ line }}
      </p>
      <event-sign-up-form
        :event="event"
        :initial-sign-up="signUp"
        :show-guest-form="!isLoggedIn"
        @delete:sign-up="removed"
        @update:sign-up="saved"
      />
    </template>
  </aside>
</template>

<style scoped>
.panel {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  padding: 1.6rem 1.6rem 1.7rem;
  background-color: var(--band-ground);
}

/* A brand edge across its top, marking the one place on the page something is asked. */
.panel::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: var(--color-brand);
}

.panel__title {
  font-family: var(--font-display);
  font-size: 1.5rem;
  text-transform: uppercase;
}

.panel__line {
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--color-ash);
}

.panel__going {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.panel__going svg {
  flex: none;
  width: 2rem;
  height: 2rem;
  color: var(--color-eyebrow);
}

.panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

@media (max-width: 767px) {
  .panel {
    padding: 1.3rem 1.25rem 1.4rem;
  }
}
</style>
