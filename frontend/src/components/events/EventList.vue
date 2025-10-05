<script lang="ts" setup>
import { useRoute } from "vue-router"
import EventListItem from "@/components/events/EventListItem.vue"
import type { AdvancedCommittee, Event, EventSignUp } from "@/lib"

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

    <v-list v-else>
      <template
        v-for="(event, i) in props.events"
        :key="event.id"
      >
        <event-list-item
          :event="event"
          :committees="props.committees"
          :sign-ups="props.eventSignUps"
          class="event-list-item"
          @delete:event="deleteEvent"
          @delete:sign-up="deleteSignUp"
          @update:sign-up="updateSignUp"
          @update:event="updateEvent"
        />
        <v-divider v-if="i < props.events.length - 1" />
      </template>
    </v-list>
  </v-expand-transition>
</template>
