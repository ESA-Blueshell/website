<script lang="ts" setup>
import {computed} from "vue"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import type {EventResponse, EventSignUpResponse} from ".."
import AgendaRow from "./AgendaRow.vue"
import MonthHead from "./MonthHead.vue"
import {monthsOf} from "./eventFacts"

/**
 * Everything after the next event, as a dated agenda grouped by month. A committee member adds
 * an event from its head.
 */
defineOptions({name: "EventAgenda"})

type CommitteeOption = {id: number, name: string}

const {events, signUps = [], committees = [], mayAdd = false} = defineProps<{
  events: EventResponse[]
  signUps?: EventSignUpResponse[]
  committees?: CommitteeOption[]
  mayAdd?: boolean
}>()

const emit = defineEmits<{
  "update:event": [event: EventResponse]
  "delete:event": [id: number]
  "update:signUp": [signUp: EventSignUpResponse]
  "delete:signUp": [id: number]
}>()

const months = computed(() => monthsOf(events))
</script>

<template>
  <lead-band
    accent="var(--color-brand)"
    testid="events-agenda"
  >
    <band-head
      :count="events.length"
      count-said="upcoming events"
      heading="Upcoming"
    >
      <cut-button
        v-if="mayAdd"
        href="/events/create"
        testid="event-create-btn"
      >
        Add an event
      </cut-button>
    </band-head>

    <p
      v-if="events.length === 0"
      class="agenda__empty"
    >
      Nothing else is planned yet. New events land here, and in your calendar if you subscribe.
    </p>

    <template
      v-for="month in months"
      :key="month.key"
    >
      <month-head
        :count="month.events.length"
        :name="month.name"
      />
      <div class="agenda__rows">
        <agenda-row
          v-for="event in month.events"
          :key="event.id"
          :committees="committees"
          :event="event"
          :sign-ups="signUps"
          @delete:event="emit('delete:event', $event)"
          @delete:sign-up="emit('delete:signUp', $event)"
          @update:event="emit('update:event', $event)"
          @update:sign-up="emit('update:signUp', $event)"
        />
      </div>
    </template>
  </lead-band>
</template>

<style scoped>
.agenda__empty {
  padding: 2rem 0 0.5rem;
  color: var(--color-ash);
}

.agenda__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
</style>
