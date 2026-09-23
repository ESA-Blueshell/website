<script lang="ts" setup>
import {computed, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"
import store from "@/plugins/store"
import type {EventResponse, EventSignUpResponse} from ".."
import EventActions from "./EventActions.vue"
import EventBand from "./EventBand.vue"

/**
 * The next event, as the events page's focal band: the event band with its description cut
 * short, the way to sign up and the way to the event's own page. The sign-up form opens under it.
 */
defineOptions({name: "NextEventBand"})

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
const signUp = computed(() => signUps.find(one => one.eventId === event.id))
const committee = computed(() => committees.find(one => one.id === event.committeeId)?.name ?? "")

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
  <event-band
    blurb
    :event="event"
    eyebrow="Next up"
  >
    <template #actions>
      <event-actions
        v-model:signing="signing"
        :committees="committees"
        :event="event"
        :sign-ups="signUps"
        @delete:event="emit('delete:event', $event)"
        @delete:sign-up="signedOut"
        @update:event="emit('update:event', $event)"
      />
      <cut-button
        :href="`/events/${event.id}`"
        testid="next-event-page"
      >
        See the event
      </cut-button>
      <span
        v-if="committee"
        class="next__by"
      >By {{ committee }}</span>
    </template>

    <event-sign-up-form
      v-if="signing"
      :event="event"
      :initial-sign-up="signUp"
      :show-guest-form="!isLoggedIn"
      @delete:sign-up="signedOut"
      @update:sign-up="signedUp"
    />
  </event-band>
</template>

<style scoped>
.next__by {
  font-size: 0.82rem;
  color: var(--color-ash);
}
</style>
