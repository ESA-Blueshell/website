<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRouter} from "vue-router"
import {DateTime} from "luxon"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import IconButton from "@/components/island/IconButton.vue"
import store, {type GuestSessionData} from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {deleteEvent, type EventResponse, type EventSignUpResponse, setEventApproved, withdrawSignUp} from ".."

/**
 * What can be done with one event from where it is listed: signing up, and for its committee
 * and the board, approving it, reading its sign-ups, editing it and deleting it.
 *
 * The sign-up form itself is the listing's to place, under the event rather than in a row of
 * buttons, so whether it is open is handed back through `signing`.
 */
defineOptions({name: "EventActions"})

type CommitteeOption = {id: number, name: string}

const {event, signUps = [], committees = [], manageOnly = false} = defineProps<{
  event: EventResponse
  signUps?: EventSignUpResponse[]
  /** The committees the reader belongs to, or every one for the board. */
  committees?: CommitteeOption[]
  /** Only the organiser's actions, where the page offers signing up somewhere of its own. */
  manageOnly?: boolean
}>()

const emit = defineEmits<{
  "update:event": [event: EventResponse]
  "delete:event": [id: number]
  "delete:signUp": [id: number]
}>()

const signing = defineModel<boolean>("signing", {default: false})

const router = useRouter()

const isMember = computed<boolean>(() => store.getters.isMember)
const isBoard = computed<boolean>(() => store.getters.isBoard)

const signUp = computed(() => signUps.find(one => one.eventId === event.id))
const isSignedUp = computed<boolean>(() => signUp.value?.id !== undefined)
const hasForm = computed<boolean>(() => (event.signUpForm?.questions?.length ?? 0) > 0)
/* Signed up to an event that asked nothing: there is nothing to edit, so the press signs out. */
const signsOutAtOnce = computed<boolean>(() => isSignedUp.value && !hasForm.value)

const started = computed<boolean>(() => DateTime.fromISO(event.startTime) < DateTime.now())
const manages = computed<boolean>(() => isBoard.value || committees.some(one => one.id === event.committeeId))

const blocked = computed<string | null>(() => {
  if (!event.approved) return "This event is waiting for approval"
  if (event.membersOnly && !isMember.value) return "This event is for members"
  if (started.value) return "This event has started"
  if (isSignedUp.value) return null
  if (event.signUpDeadline && DateTime.fromISO(event.signUpDeadline) < DateTime.now()) return "Sign-ups have closed"
  if (event.signUpLimit != null && event.signUpCount >= event.signUpLimit) return "This event is full"
  return null
})

const signUpLabel = computed<string>(() => {
  if (signsOutAtOnce.value) return "Sign me out"
  if (signing.value) return "Close"
  return isSignedUp.value ? "Edit sign-up" : "Sign up"
})

const signingOut = ref(false)

async function pressSignUp() {
  if (!signsOutAtOnce.value) {
    signing.value = !signing.value
    return
  }
  signingOut.value = true
  try {
    const token = (store.getters.getGuestData as GuestSessionData | null)?.accessToken ?? null
    await withdrawSignUp(signUp.value!.id as number, token)
    emit("delete:signUp", signUp.value!.id as number)
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    signingOut.value = false
  }
}

async function toggleApproved() {
  emit("update:event", await setEventApproved(event.id, !event.approved))
}

const deleting = ref(false)
const working = ref(false)
const failure = ref<string | null>(null)

async function confirmDelete() {
  working.value = true
  failure.value = null
  try {
    await deleteEvent(event.id)
    store.commit("setStatusSnackbarMessage", `Deleted “${event.title}”`)
    deleting.value = false
    emit("delete:event", event.id)
  } catch {
    failure.value = `Couldn't delete “${event.title}”`
  } finally {
    working.value = false
  }
}
</script>

<template>
  <span class="event-actions">
    <template v-if="manages">
      <button
        class="event-actions__approval"
        :class="{'event-actions__approval--on': event.approved}"
        :data-testid="`event-approve-btn-${event.id}`"
        :disabled="!isBoard || started"
        :title="event.approved ? 'Mark as awaiting approval' : 'Mark as approved'"
        type="button"
        @click="toggleApproved"
      >
        {{ event.approved ? "Approved" : "Awaiting approval" }}
      </button>
      <icon-button
        :disabled="!event.signUp"
        :label="`Sign-ups, ${event.signUpCount}`"
        :testid="`event-signups-btn-${event.id}`"
        @click="router.push(`/events/signups/${event.id}`)"
      >
        <svg
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          viewBox="0 0 24 24"
        ><path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01" /></svg>
      </icon-button>
      <icon-button
        label="Edit event"
        :testid="`event-edit-btn-${event.id}`"
        @click="router.push(`/events/edit/${event.id}`)"
      >
        <svg
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          viewBox="0 0 24 24"
        ><path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" /></svg>
      </icon-button>
      <icon-button
        danger
        label="Delete event"
        :testid="`event-delete-btn-${event.id}`"
        @click="deleting = true"
      >
        <svg
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          viewBox="0 0 24 24"
        ><path d="M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13" /></svg>
      </icon-button>
    </template>

    <cut-button
      v-if="event.signUp && !manageOnly"
      :disabled="blocked !== null || signingOut"
      :testid="`event-signup-toggle-btn-${event.id}`"
      :title="blocked ?? undefined"
      :tone="isSignedUp || signing ? 'plain' : 'solid'"
      @click="pressSignUp"
    >
      {{ signUpLabel }}
    </cut-button>

    <confirm-dialog
      confirm-label="Delete"
      :failure="failure"
      :open="deleting"
      :question="`Delete “${event.title}”? Its sign-ups go with it, and this can't be undone.`"
      testid="event-delete-dialog"
      title="Delete event"
      :working="working"
      working-label="Deleting"
      @confirm="confirmDelete"
      @update:open="deleting = $event"
    />
  </span>
</template>

<style scoped>
.event-actions {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
}

.event-actions__approval {
  padding: 0.26rem 0.55rem;
  margin-right: 0.35rem;
  font-family: var(--font-body);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-warning);
  white-space: nowrap;
  cursor: pointer;
  background: none;
  border: 1px solid currentColor;
}

.event-actions__approval--on {
  color: var(--color-ok);
}

.event-actions__approval:disabled {
  cursor: default;
}
</style>
