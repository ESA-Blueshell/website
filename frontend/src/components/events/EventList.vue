<script lang="ts" setup>
import {useRoute} from "vue-router"
import EventListItem from "@/components/events/EventListItem.vue"
import type {AdvancedCommittee, Event, EventSignUp} from "@/lib"

interface Emits {
  (e: "delete:event", id: number): void

  (e: "delete:signUp", id: number): void

  (e: "update:event", event: Event): void

  (e: "update:signUp", signUp: EventSignUp): void
}

interface Props {
  events: Event[]
  eventSignUps: EventSignUp[]
  committees: AdvancedCommittee[]
}

const emit = defineEmits<Emits>()
const props = defineProps<Props>()

const route = useRoute()

function updateEvent(event: Event): void {
  emit("update:event", event)
}

function updateSignUp(signUp: EventSignUp): void {
  emit("update:signUp", signUp)
}

function deleteEvent(id: number) {
  emit("delete:event", id)
}

function deleteSignUp(signUpId: number): void {
  emit("delete:signUp", signUpId)
}
</script>

<template>
  <!-- loader if parent chooses to pass null/undefined -->
  <v-progress-circular
    v-if="!props.events"
    indeterminate
  />

  <v-expand-transition
    v-else
    :disabled="!!route.hash"
  >
    <div v-if="props.events.length === 0">
      <p>No upcoming events found</p>
    </div>

    <div
      v-else
      class="mb-8"
    >
      <v-card
        v-for="event in props.events"
        :key="event.id"
        class="my-3"
        rounded="lg"
      >
        <event-list-item
          :committees="props.committees"
          :event="event"
          :sign-ups="props.eventSignUps"
          class="event-list-item"
          @delete:event="deleteEvent"
          @delete:sign-up="deleteSignUp"
          @update:sign-up="updateSignUp"
          @update:event="updateEvent"
        />
      </v-card>
    </div>
  </v-expand-transition>
</template>
