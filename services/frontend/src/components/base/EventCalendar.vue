<template>
  <v-toolbar
    class="px-0"
    color="transparent"
  >
    <v-toolbar-title
      class="text-h5 ml-2 toolbar-title"
      data-testid="event-calendar-month-title"
    >
      {{ monthTitle }}
    </v-toolbar-title>

    <div class="ms-auto d-flex align-center">
      <v-btn
        data-testid="event-calendar-prev-month-btn"
        icon
        variant="text"
        @click="goPrevMonth"
      >
        <v-icon>mdi-chevron-left</v-icon>
      </v-btn>
      <v-btn
        data-testid="event-calendar-next-month-btn"
        icon
        variant="text"
        @click="goNextMonth"
      >
        <v-icon>mdi-chevron-right</v-icon>
      </v-btn>
      <v-btn
        class="ml-2"
        data-testid="event-calendar-today-btn"
        size="small"
        variant="text"
        @click="goToCurrentMonth"
      >
        Today
      </v-btn>
      <v-btn
        v-if="!display.xs.value"
        :href="GOOGLE_CALENDAR_SUBSCRIBE_URL"
        aria-label="Add the Blueshell events to my Google Calendar"
        class="ml-2"
        data-testid="event-calendar-subscribe-btn"
        rel="noopener"
        size="small"
        target="_blank"
        variant="outlined"
      >
        Subscribe to calendar
        <v-icon end>
          mdi-open-in-new
        </v-icon>
      </v-btn>
    </div>
  </v-toolbar>


  <v-calendar
    v-model="displayedMonth"
    :events="calendarEvents"
    :show-adjacent-months="true"
    :first-day-of-week="1"
    type="month"
    @click:event="showEvent"
  />

  <v-menu
    v-if="selectedEvent"
    v-model="selectedOpen"
    :activator="selectedElement"
    location="start"
  >
    <event-details :model-value="selectedEvent" />
  </v-menu>
</template>

<script lang="ts" setup>
import {useDisplay, useLocale} from "vuetify"
import {computed, onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import {type EventResponse, findEvents} from "@/services/api"
import EventDetails from "@/components/base/EventDetails.vue"

type CalendarEvent = {
  name: string
  start: Date
  end: Date
  color?: string
  category?: string
}

const GOOGLE_CALENDAR_SUBSCRIBE_URL =
  "https://calendar.google.com/calendar/u/1/r?cid=87r5v7ep7k9ronlrg8n2q9033s@group.calendar.google.com"

const displayedMonth = ref<string>(DateTime.now().toISODate()!)
const selectedEvent = ref<EventResponse | null>(null)
const selectedElement = ref<HTMLElement>()
const selectedOpen = ref(false)
const events = ref<EventResponse[]>([])
const calendarEvents = ref<CalendarEvent[]>([])
const collectedMonths = ref<string[]>([])

type CalendarEventEx = CalendarEvent & { raw: EventResponse }

const {current: localeCurrent} = useLocale()
localeCurrent.value = "en"

const display = useDisplay()

const monthTitle = computed(() =>
  DateTime.fromISO(displayedMonth.value)
    .setLocale(localeCurrent.value).toFormat("LLLL yyyy"),
)

const setMonth = (d: DateTime) => {
  displayedMonth.value = d.toISODate()!
}
const goPrevMonth = () => setMonth(DateTime.fromISO(displayedMonth.value).minus({months: 1}))
const goNextMonth = () => setMonth(DateTime.fromISO(displayedMonth.value).plus({months: 1}))
const goToCurrentMonth = () => setMonth(DateTime.now())

const loadEventsForMonth = async (month: DateTime) => {
  const from: string = month.startOf("month").startOf("week").startOf("day").toISO()!
  const to: string = month.endOf("month").endOf("week").endOf("day").toISO()!

  if (collectedMonths.value.includes(from)) return
  collectedMonths.value.push(from)

  const {data} = await findEvents({query: {from, to}})
  const page = (data ?? {})

  if (page.content) {
    const newEvents = page.content.filter((e) => !events.value.some((e2) => e2.id === e.id))
    events.value = [...events.value, ...newEvents]
  }
}


function deleteEvent(id: number) {
  events.value = events.value?.filter((e: EventResponse) => e.id !== id) ?? []
}

function updateEvent(event: EventResponse): void {
  const list = events.value
  const idx = list.findIndex((e) => e.id === event.id)
  if (idx >= 0) {
    events.value = [
      ...list.slice(0, idx),
      event,
      ...list.slice(idx + 1),
    ]
  } else {
    events.value = [...list, event]
  }
}

watch(displayedMonth, (d: string) => {
  const first = DateTime.fromISO(d)
  loadEventsForMonth(first)
})

watch(events, (list: EventResponse[]) => {
  calendarEvents.value = list
    .map((e): CalendarEventEx => {
      const start = DateTime.fromISO(e.startTime).toJSDate()!
      const end = DateTime.fromISO(e.endTime ?? e.startTime).toJSDate()!
      return {
        name: e.title,
        start,
        end,
        color: e.approved ? "primary" : "orange",
        category: e.title,
        raw: e,
      }
    })
})

onMounted(() => {
  loadEventsForMonth(DateTime.now().startOf("month")!)
})


const showEvent = (nativeEvent: Event, {event}: { event: CalendarEventEx }) => {
  nativeEvent.stopPropagation()
  const toggle = () => {
    selectedEvent.value = event.raw
    selectedElement.value = (nativeEvent.target as HTMLElement | null) ?? undefined
    selectedOpen.value = !selectedOpen.value
  }
  if (selectedOpen.value) {
    setTimeout(toggle, 10)
  } else toggle()
}

defineExpose({
  deleteEvent,
  updateEvent,
})
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

// Vuetify 4 renders the day number as a circular icon button; the old
// `height: 20px` rule squashed today's marker into a lop-sided 40x20 oval.
// Mark today with a clean filled primary circle instead.
.v-calendar-weekly__day.v-present .v-icon-btn {
  width: 28px !important;
  height: 28px !important;
  min-width: 28px !important;
  margin: 4px 0;
  border: none !important;
  border-radius: 50% !important;
  background-color: rgb(var(--v-theme-primary)) !important;
  color: #fff !important;
}

.v-calendar-weekly__day-label {
  margin-top: 0;
}

.v-calendar-weekly__day {
  min-height: 60px;
}
</style>
