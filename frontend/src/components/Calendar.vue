<template>
  <v-toolbar
    class="px-0"
    color="transparent"
  >
    <v-toolbar-title class="text-h5 ml-2">
      {{ monthTitle }}
    </v-toolbar-title>
    <v-spacer />
    <v-btn
      icon
      variant="text"
      @click="goPrevMonth"
    >
      <v-icon>mdi-chevron-left</v-icon>
    </v-btn>
    <v-btn
      icon
      variant="text"
      @click="goNextMonth"
    >
      <v-icon>mdi-chevron-right</v-icon>
    </v-btn>
    <v-btn
      class="ml-2"
      size="small"
      variant="text"
      @click="goToCurrentMonth"
    >
      Today
    </v-btn>
  </v-toolbar>

  <v-calendar
    v-model="displayedMonth"
    :events="calendarEvents"
    :show-adjacent-months="true"
    :weekdays="weekdays"
    type="month"
    @click:event="showEvent"
  />

  <v-menu
    v-if="selectedEvent"
    v-model="selectedOpen"
    :activator="selectedElement"
    :close-on-content-click="false"
    location="start"
  >
    <event-details :model-value="selectedEvent" />
  </v-menu>
</template>

<script lang="ts" setup>
import {useDisplay, useLocale} from "vuetify"
import {computed, onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import type {Event, PageEvent} from "@/lib"
import {findEvents} from "@/lib"
import type {CalendarEvent} from "vuetify/lib/labs/VCalendar/types"
import type {CalendarWeekdays} from "vuetify/lib/composables/calendar"
import {VCalendar} from "vuetify/labs/VCalendar"
import EventDetails from "@/components/events/EventDetails.vue"

// State
const displayedMonth = ref<string>(DateTime.now().toISODate()!)
const selectedEvent = ref<Event | null>(null)
const selectedElement = ref<HTMLElement | null>(null)
const selectedOpen = ref(false)
const events = ref<Event[]>([])
const calendarEvents = ref<CalendarEvent[]>([])
const collectedMonths = ref<string[]>([])

type CalendarEventEx = CalendarEvent & { raw: Event }

// Localization
const {current: localeCurrent} = useLocale()
localeCurrent.value = "en"

// Responsive display
const display = useDisplay()
const isXs = computed(() => display.xs.value)
const weekdays = computed(() => (isXs.value ? [1, 2, 3, 4, 5] : [1, 2, 3, 4, 5, 6, 0] as CalendarWeekdays[]))

// Header title reads from displayedMonth so it updates on internal nav too
const monthTitle = computed(() =>
  DateTime.fromISO(displayedMonth.value)
    .setLocale(localeCurrent.value).toFormat("LLLL yyyy"),
)

// Nav helpers operate on displayedMonth, and also align focus (so your click handlers & watchers keep working)
const setMonth = (d: DateTime) => {
  displayedMonth.value = d.toISODate()!
}
const goPrevMonth = () => setMonth(DateTime.fromISO(displayedMonth.value).minus({months: 1}))
const goNextMonth = () => setMonth(DateTime.fromISO(displayedMonth.value).plus({months: 1}))
const goToCurrentMonth = () => setMonth(DateTime.now())

// Fetch events for the month (with 1-week padding at both ends)
const loadEventsForMonth = async (month: DateTime) => {
  const from: string = month.startOf("month").startOf("week").startOf("day").toISO()!
  const to: string = month.endOf("month").endOf("week").endOf("day").toISO()!

  if (collectedMonths.value.includes(from)) return
  const {data} = await findEvents({query: {from, to}})
  const page = (data ?? {}) as PageEvent

  if (page.content) {
    const newEvents = page.content.filter(e => !events.value.some(e2 => e2.id === e.id))
    events.value = [
      ...events.value,
      ...newEvents,
    ]
  }
}

// When the *visible* month changes (buttons or the calendar’s own arrows/swipes), fetch data & update
watch(displayedMonth, (d: string) => {
  const first = DateTime.fromISO(d)
  loadEventsForMonth(first)
})

// (Keep your existing mapping from domain events to Vuetify events)
watch(events, (list: Event[]) => {
  calendarEvents.value = list
    .map((e): CalendarEventEx => {
      const start = DateTime.fromISO(e.startTime).toJSDate()!
      const end = DateTime.fromISO(e.endTime ?? e.startTime).toJSDate()!
      return {name: e.title, start, end, color: "primary", category: e.committee?.name, raw: e}
    })
})

// Initial load
onMounted(() => {
  const first = DateTime.now().startOf("month")!
  loadEventsForMonth(first)
})

// Event handling unchanged…
const showEvent = (nativeEvent: any, {event}: { event: CalendarEventEx }) => {
  console.log("nativeEvent", typeof nativeEvent)
  const toggle = () => {
    selectedEvent.value = event.raw
    selectedElement.value = nativeEvent.target as HTMLElement
    selectedOpen.value = !selectedOpen.value
  }
  if (selectedOpen.value) {
    setTimeout(toggle, 10)
  } else toggle()
  nativeEvent.stopPropagation()
}
</script>
<style lang="scss">
.v-calendar .v-event {
  margin-left: 4px !important;
}

.v-calendar .v-event-summary {
  height: 28px;
}


.v-calendar.v-calendar-events .v-calendar-weekly__head-weekday {
  border-bottom: 0.5px solid #e0e0e0;
}

.v-icon-btn {
  height: 20px;
  margin-top: 5px;
  margin-bottom: 5px;
}

.v-calendar-weekly__day-label {
  margin-top: 0px;
}

.v-calendar-weekly__day {
  min-height: 60px;
}

</style>
