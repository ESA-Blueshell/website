<script lang="ts" setup>
import CutButton from "@/components/island/CutButton.vue"
import Island from "@/components/island/Island.vue"
import EventArchive from "@/domains/events/island/EventArchive.vue"
import EventsHead from "@/domains/events/island/EventsHead.vue"
</script>

<template>
  <v-main>
    <island
      class="past-page"
      testid="past-events-island"
    >
      <events-head
        eyebrow="The archive"
        heading="Past events"
      >
        Everything the association has run, newest first. Search by name or narrow it to one
        academic year.
        <template #actions>
          <cut-button
            href="/events"
            testid="past-events-upcoming"
          >
            Upcoming events
          </cut-button>
        </template>
      </events-head>

      <event-archive />
    </island>
  </v-main>
</template>

<style scoped>
/* The island root fills a page; the Vuetify main around it already does. */
.past-page {
  min-height: 0;
}
</style>
