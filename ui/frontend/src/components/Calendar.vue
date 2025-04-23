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
    :activator="selectedElement"
    location="start"
  >
    <EventDetails :selected-event="selectedEvent" />
  </v-menu>
</template>

<script lang="ts">
import { EventService } from '@/services';
import {VCalendar} from 'vuetify/labs/VCalendar'
import EventDetails from "@/components/events/EventDetails.vue"
import {useDisplay, useLocale} from 'vuetify'
import {computed, onMounted, ref, watch} from 'vue'
import {DateTime} from "luxon";

export default {
  name: "Calendar",
  components: {EventDetails, VCalendar},
  setup() {
    const focus = ref([new Date()])
    const selectedEvent = ref(null)
    const selectedElement = ref(null)
    const selectedOpen = ref(false)
    const events = ref([])
    const collectedMonths = ref([])
    const monthsLoading = ref(0)
    const eventService = new EventService();

    // Localization
    const {current: localeCurrent} = useLocale()
    localeCurrent.value = 'en'

    // Responsive display
    const display = useDisplay()
    const isXs = computed(() => display.xs.value)
    const hideWeekNumber = computed(() => isXs.value)
    const weekdays = computed(() =>
      isXs.value ? [1, 2, 3, 4, 5] : [1, 2, 3, 4, 5, 6, 0]
    )

    // EventModel fetching
    const getEvents = (month) => {
      // Set date range for API call
      const from: string = DateTime.fromJSDate(month).startOf("month").toISO();
      const to: string = DateTime.fromJSDate(month).endOf("month").toISO();

      if (collectedMonths.value.includes(from)) return;

      monthsLoading.value++
      eventService.getEvents(from, to)
        .then((data) => {
          events.value = [
            ...events.value,
            ...data.map(e => ({
              title: e.title,
              details: e.description,
              date: e.startTime.substring(0, 10),
              start: new Date(e.startTime),
              end: e.endTime ? new Date(e.endTime) : undefined,
              location: e.location,
              memberPrice: e.memberPrice,
              publicPrice: e.publicPrice,
              googleId: e.googleId,
              timed: !!e.endTime,
              banner: e.banner
            }))
          ];
          collectedMonths.value.push(from);
        })
        .finally(() => monthsLoading.value--)
    }

    // Watchers
    watch(focus, ([newMonth]) => {
      const adjusted = new Date(newMonth)
      adjusted.setDate(1)
      getEvents(adjusted)
    }, {deep: true})

    // Initial load
    onMounted(() => {
      const initialFocus = new Date(focus.value[0])
      initialFocus.setDate(1)
      getEvents(initialFocus)
    })

    // EventModel handling
    const showEvent = ({nativeEvent, event}) => {
      const toggle = () => {
        selectedEvent.value = event
        selectedElement.value = nativeEvent.target
        selectedOpen.value = !selectedOpen.value
      }

      selectedOpen.value ? setTimeout(toggle, 10) : toggle()
      nativeEvent.stopPropagation()
    }

    return {
      focus,
      events,
      selectedEvent,
      selectedElement,
      selectedOpen,
      showEvent,
      hideWeekNumber,
      weekdays
    }
  }
}
</script>

<style lang="scss">
@use '@/styles/settings' as settings;
@use "sass:map";


.v-calendar {
  @media #{map.get(settings.$display-breakpoints, 'xs')} {
    .v-calendar-header__title {
      font-size: 6vw;
    }

    .v-calendar-header__today {
      margin-inline-end: 6px;
    }

    .v-calendar-header__title {
      margin-inline-start: 6px;
    }
  }

  .v-calendar-weekly__day-alldayevents-container {
    min-height: 0;
  }

  .v-calendar-month__day {
    min-height: 84.5px;

    .v-calendar-weekly__day-events-container {
      padding: 0 4px;
    }

    .v-chip {
      background-color: rgb(var(--v-theme-primary));
      color: rgb(var(--v-theme-on-primary));
      padding: 0 5px;

      .v-badge {
        display: none;
      }
    }
  }

  // Add some extra lines for readability
  .v-calendar-weekly__head-weekday, .v-calendar-weekly__head-weekday-with-weeknumber {
    border-bottom: thin solid #e0e0e0;
  }

  .v-calendar-month__weeknumber {
    border-right: thin solid #e0e0e0;
  }

  .v-calendar-weekly__head-weeknumber {
    border-right: thin solid #e0e0e0;
    border-bottom: thin solid #e0e0e0;
  }

  // Rounded corners :)
  .v-calendar__container {
    border-radius: settings.$border-radius-root;

    .v-calendar-weekly__head-weeknumber {
      border-top-left-radius: settings.$border-radius-root;
    }

    :nth-last-child(1 of .v-calendar-month__weeknumber) {
      border-bottom-left-radius: settings.$border-radius-root;
    }

    @media #{map.get(settings.$display-breakpoints, 'xs')} {
      :nth-last-child(5 of .v-calendar-month__day) {
        border-bottom-left-radius: settings.$border-radius-root;
      }
    }

    :nth-last-child(1 of .v-calendar-month__day) {
      border-bottom-right-radius: settings.$border-radius-root;
    }
  }

}
</style>
