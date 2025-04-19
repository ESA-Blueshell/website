<template>
  <v-main>
    <top-banner title="Events" />
    <div class="mx-3">
      <div
        class="mx-auto"
        style="max-width: 1000px"
      >
        <calendar />
      </div>
      <div
        class="mx-auto mt-5"
        style="max-width: 800px"
      >
        <p class="text-h4 font-weight-light">
          Upcoming events
        </p>
        <upcoming-events-list />
      </div>
      <div
        class="mx-auto my-5"
        style="max-width: 800px"
      >
        <p class="text-h4 font-weight-light">
          Add the events to your own calendar!
        </p>
        <p class="text-body-1">
          You can add all our events to your personal Google Calendar agenda by clicking <a
            href="https://calendar.google.com/calendar/u/1/r?cid=87r5v7ep7k9ronlrg8n2q9033s@group.calendar.google.com"
            target="_blank"
            class="text-decoration-none"
          >here</a>! If you want to add only a single
          event, you can click on at and click on the "add to calendar" button. Beware that when you
          copy an event, it isn't synchronized with the Blueshell calendar anymore.
        </p>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import Calendar from "@/components/Calendar.vue"
import EventList from "@/components/events/EventList.vue";

export default {
  components: {UpcomingEventsList: EventList, TopBanner: TopBanner, Calendar: Calendar}
}
</script>
