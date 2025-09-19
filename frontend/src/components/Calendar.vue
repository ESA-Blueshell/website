<template>
  <v-calendar
    v-model="focus"
    :events="events"
    :show-adjacent-months="true"
    :hide-week-number="hideWeekNumber"
    :weekdays="weekdays"
    @click:event="showEvent"
  />

  <v-menu
    v-if="selectedEvent"
    v-model="selectedOpen"
    :close-on-content-click="false"
    :activator="selectedElement as HTMLElement"
    location="start"
  >
    <EventDetails :selected-event="selectedEvent" />
  </v-menu>
</template>
<script setup lang="ts">
import { VCalendar } from 'vuetify/labs/VCalendar'
import EventDetails from "@/components/events/EventDetails.vue"
import { useDisplay, useLocale } from 'vuetify'
import { computed, onMounted, ref, watch } from 'vue'
import { DateTime } from "luxon";
import { findEvents } from "@/lib";
import type { Event, PageEvent } from "@/lib"
import type {CalendarWeekdays} from "vuetify/lib/composables/calendar";

// State
const focus = ref<Date[]>([new Date()])
const selectedEvent = ref<Event | null>(null)
const selectedElement = ref<HTMLElement | null>(null)
const selectedOpen = ref(false)
const events = ref<Event[]>([])
const collectedMonths = ref<string[]>([])
const monthsLoading = ref(0)

// Localization
const { current: localeCurrent } = useLocale()
localeCurrent.value = 'en'

// Responsive display
const display = useDisplay()
const isXs = computed(() => display.xs.value)
const hideWeekNumber = computed(() => isXs.value)
const weekdays = computed(() => (isXs.value ? [1, 2, 3, 4, 5] : [1, 2, 3, 4, 5, 6, 0] as CalendarWeekdays[]))

// Data fetching using generated client
const loadEventsForMonth = async (month: Date) => {
  const from: string = DateTime.fromJSDate(month).startOf("month").toISO()!
  const to: string = DateTime.fromJSDate(month).endOf("month").toISO()!
  if (collectedMonths.value.includes(from)) return

  monthsLoading.value++
  try {
    const { data } = await findEvents({
      query: {
        from,
        to,
      }
    })
    const page = (data ?? {}) as PageEvent
    if (page.content) events.value = [...events.value, ...page.content]
    collectedMonths.value.push(from)
  } finally {
    monthsLoading.value--
  }
}

// Watchers
watch(
  focus,
  ([newMonth]) => {
    const adjusted = new Date(newMonth)
    adjusted.setDate(1)
    loadEventsForMonth(adjusted)
  },
  { deep: true }
)

// Initial load
onMounted(() => {
  const initialFocus = new Date(focus.value[0])
  initialFocus.setDate(1)
  loadEventsForMonth(initialFocus)
})

// Event handling
const showEvent = ({ nativeEvent, event }: { nativeEvent: MouseEvent; event: Event }) => {
  const toggle = () => {
    selectedEvent.value = event
    selectedElement.value = nativeEvent.target as HTMLElement
    selectedOpen.value = !selectedOpen.value
  }
  if (selectedOpen.value) {
    setTimeout(toggle, 10)
    return
  } else {
   toggle()
  }

  nativeEvent.stopPropagation()
}

</script>
<style scoped lang="scss">
@use "sass:map";

@media #{map.get($display-breakpoints, 'xs')} {
  .v-calendar-header__title { font-size: 6vw; }
  .v-calendar-header__today { margin-inline-end: 6px; }
  .v-calendar-header__title { margin-inline-start: 6px; }
}

.v-calendar-weekly__day-alldayevents-container { min-height: 0; }

.v-calendar-month__day {
  min-height: 84.5px;

  .v-calendar-weekly__day-events-container { padding: 0 4px; }

  .v-chip {
    background-color: rgb(var(--v-theme-primary));
    color: rgb(var(--v-theme-on-primary));
    padding: 0 5px;

    .v-badge { display: none; }
  }
}

.v-calendar-weekly__head-weekday,
.v-calendar-weekly__head-weekday-with-weeknumber {
  border-bottom: thin solid #e0e0e0;
}

.v-calendar-month__weeknumber { border-right: thin solid #e0e0e0; }

.v-calendar-weekly__head-weeknumber {
  border-right: thin solid #e0e0e0;
  border-bottom: thin solid #e0e0e0;
}

.v-calendar__container {
  border-radius: $border-radius-root;

  .v-calendar-weekly__head-weeknumber {
    border-top-left-radius: $border-radius-root;
  }

  :nth-last-child(1 of .v-calendar-month__weeknumber) {
    border-bottom-left-radius: $border-radius-root;
  }

  @media #{map.get($display-breakpoints, 'xs')} {
    :nth-last-child(5 of .v-calendar-month__day) {
      border-bottom-left-radius: $border-radius-root;
    }
  }

  :nth-last-child(1 of .v-calendar-month__day) {
    border-bottom-right-radius: $border-radius-root;
  }
}
</style>
