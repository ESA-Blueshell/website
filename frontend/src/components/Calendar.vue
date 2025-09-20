<template>
  <v-calendar
    v-model="focus"
    :events="calendarEvents"
    :show-adjacent-months="true"
    type="month"
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
import EventDetails from "@/components/events/EventDetails.vue"
import { useDisplay, useLocale } from 'vuetify'
import { computed, onMounted, ref, watch } from 'vue'
import { DateTime } from "luxon";
import { findEvents } from "@/lib";
import type { Event, PageEvent } from "@/lib"
import type { CalendarEvent } from "vuetify/lib/labs/VCalendar/types"
import type {CalendarWeekdays} from "vuetify/lib/composables/calendar";
import { VCalendar } from "vuetify/labs/VCalendar"

// State
const focus = ref<Date[]>([new Date()])
const selectedEvent = ref<Event | null>(null)
const selectedElement = ref<HTMLElement | null>(null)
const selectedOpen = ref(false)
const events = ref<Event[]>([])
const calendarEvents = ref<CalendarEvent[]>([])
const collectedMonths = ref<string[]>([])
const monthsLoading = ref(0)

type CalendarEventEx = CalendarEvent & { raw: Event } // keep original event handy

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
  const from: string = DateTime.fromJSDate(month).startOf("month").startOf("week").startOf("day").toISO()!
  const to: string = DateTime.fromJSDate(month).endOf("month").endOf("week").endOf("day").toISO()!
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

watch(
  events,
  (list: Event[]) => {
    calendarEvents.value = list.map((e): CalendarEventEx => {
      // ensure ISO strings (Vuetify accepts Date, number, or string)
      const start = DateTime.fromISO(e.startTime).toJSDate()!
      const end   = DateTime.fromISO(e.endTime ?? e.startTime).toJSDate()!
      console.log(e.startTime, e.endTime, start, end)

      return {
        name: e.title,
        start,
        end,

        // nice-to-haves
        color: "primary",
        category: e.committee?.name,     // useful if you switch to category view
        // stash the original event so click handlers can use your own type
        raw: e,
      }
    })
  }
)


// Initial load
onMounted(() => {
  const initialFocus = new Date(focus.value[0])
  initialFocus.setDate(1)
  loadEventsForMonth(initialFocus)
})

// Event handling
const showEvent = ({ nativeEvent, event }: { nativeEvent: MouseEvent; event: CalendarEventEx }) => {
  const toggle = () => {
    selectedEvent.value = event.raw           // <- your domain Event
    selectedElement.value = nativeEvent.target as HTMLElement
    selectedOpen.value = !selectedOpen.value
  }
  if (selectedOpen.value) {
    setTimeout(toggle, 10)
  } else {
    toggle()
  }
  nativeEvent.stopPropagation()
}

</script>
<style lang="scss">

</style>
