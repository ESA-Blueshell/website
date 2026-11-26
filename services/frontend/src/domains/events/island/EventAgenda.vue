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

    <div class="agenda__list">
      <template
        v-for="month in months"
        :key="month.key"
      >
        <month-head
          class="agenda__month"
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
    </div>
  </lead-band>
</template>

<style scoped>
.agenda__empty {
  padding: 2rem 0 0.5rem;
  color: var(--color-ash);
}

/* The rows reach past the column on a wide screen, as far as the band has room to spare, while
   the heading stays on the line every band's heading starts on. The columns are every
   AgendaRow's, held here so each row takes them as a subgrid and a column lines up through every
   month even when one row's actions are wider. */
.agenda__list {
  margin-inline: calc(-1 * clamp(0rem, (100vw - 76rem) / 2, 5rem));
  display: grid;
  grid-template-columns: 4.5rem 6.5rem minmax(0, 1fr) 13rem auto;
  column-gap: 1.5rem;
}

.agenda__month,
.agenda__rows {
  grid-column: 1 / -1;
}

.agenda__rows {
  display: grid;
  grid-template-columns: subgrid;
  row-gap: 2px;
}

@media (--phone) {
  .agenda__list {
    grid-template-columns: 3rem 4.25rem minmax(0, 1fr);
    column-gap: 0.85rem;
  }
}
</style>
