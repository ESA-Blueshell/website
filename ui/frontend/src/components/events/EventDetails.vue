<template>
  <v-card max-width="350px">
    <!-- Start of the toolbar in the selected event menu -->
    <!-- Includes the event's title and the location and add to calendar buttons -->
    <v-toolbar
      :color="selectedEvent.color"
      dark
    >
      <!-- Name of the event -->
      <v-toolbar-title
        v-if="selectedEvent.name.length < 15"
        :text="selectedEvent.name"
      />
      <marquee-text
        v-else
        :repeat="3"
        :duration="10"
      >
        <v-toolbar-title
          class="mr-5"
          :text="selectedEvent.name"
        />
      </marquee-text>

      <v-spacer />
      <v-tooltip
        text="Find location"
        location="bottom"
      >
        <template #activator="{ props }">
          <v-btn
            icon="mdi-google-maps"
            v-bind="props"
            @click="findLocation"
          />
        </template>
      </v-tooltip>
      <v-tooltip
        text="Add to calendar"
        location="bottom"
      >
        <template #activator="{ props }">
          <v-btn
            icon="mdi-calendar"
            v-bind="props"
            @click="addToCal"
          />
        </template>
      </v-tooltip>
    </v-toolbar>

    <!-- Promo image -->
    <img
      v-if="selectedEvent.banner.url"
      :src="selectedEvent.banner.url"
      style="width: 100%; object-fit: contain"
      alt="promo image for the event"
    >

    <v-card-text>
      <!-- Description of the event -->
      <p v-if="selectedEvent.details">
        <!-- In the span is the actual text of the event -->
        <!-- If the expand variable is true show the fill message, otherwise only show the first 100 words -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <span v-html="expand || !longDescription ? $markdownToHtml(selectedEvent.details) : $markdownToHtml(firstHundredWords)+'...'" />
        <!-- Only show the "read more" if the message is long -->
        <!-- If it's clicked expand will be set to true and the full message will be shown -->
        <br v-if="!expand && longDescription">
        <a
          v-if="!expand && longDescription"
          @click="expandWords"
        >
          <b>read more</b>
        </a>
      </p>
      <p v-else>
        No description...
      </p>
      <!-- Starting time of the event -->
      <v-divider class="my-2" />
      <p>
        <b>When</b>
        <br>
        {{ formattedDate }}
      </p>
      <!-- Only show this part if there is a location for this event (should always be true tho) -->
      <v-divider
        v-if="selectedEvent.location"
        class="my-2"
      />
      <p v-if="selectedEvent.location">
        <b>Where</b>
        <br>
        {{ selectedEvent.location }}
      </p>
      <!-- Only show this part if there is a price for this event -->
      <!-- I want to die -->
      <v-divider
        v-if="selectedEvent.memberPrice"
      />
      <p
        v-if="selectedEvent.memberPrice"
        class="mt-4"
      >
        <b>Price</b><br>
        Members: €{{ selectedEvent.memberPrice }} <br>
        Non-members: €{{ selectedEvent.publicPrice }}
      </p>
    </v-card-text>
  </v-card>
</template>
<script>
import MarqueeText from 'vue-marquee-text-component'
import {$goto} from "@/plugins/goto.js";
import $markdownToHtml from "@/plugins/markdownToHtml.ts";

export default {
  name: 'EventDetails',
  components: {MarqueeText},
  props: {
    selectedEvent: null,
  },
  data: () => ({
    expand: false,
    linkRegex: /^(https?:\/\/)?(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)/i,
    htmlRegex: /<\/?[a-z][\s\S]*>/i
  }),
  computed: {
    formattedDate() {
      const start = this.selectedEvent.start;
      const end = this.selectedEvent.end;

      let str;
      if (end) {
        str = new Intl.DateTimeFormat('en-US', {
          weekday: 'long',
          month: 'long',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          hour12: false
        }).format(start).replace(',', '');
        str += " - ";
        str += new Intl.DateTimeFormat('en-US', {
          hour: '2-digit',
          minute: '2-digit',
          hour12: false
        }).format(end);
      } else {
        str = new Intl.DateTimeFormat('en-US', {
          weekday: 'long',
          month: 'long',
          day: 'numeric'
        }).format(start)
      }
      return str;
    },
    longDescription() {
      return this.selectedEvent.details?.split(/\s+/).length > 100
    },
    firstHundredWords() {
      return this.selectedEvent.details.split(" ").slice(0, 100).join(" ");
    }
  },
  methods: {
    $markdownToHtml,
    // Triggers when the add to calendar button is clicked on an event.
    // Opens google calendar with the id of the event so all data is instantly filled in
    addToCal() {
      $goto(encodeURI('https://calendar.google.com/event?action=TEMPLATE&tmeid=' + this.selectedEvent.googleId + '&tmsrc=blueshellesports@gmail.com'))
    },
    expandWords() {
      this.expand = true
    },
    // Triggers when the location button is clicked on an event.
    // Opens a search on google maps with the location if the location isn't discord
    findLocation() {
      if (this.selectedEvent.location.toLowerCase().includes("discord")) {
        $goto(encodeURI('https://discord.gg/23YMFQy'));
      } else if (this.selectedEvent.location.toLowerCase().includes("pel")) {
        $goto(encodeURI('https://www.google.com/maps/search/?api=1&query=Predator Esports Lounge'));
      } else {
        $goto(encodeURI('https://www.google.com/maps/search/?api=1&query=' + this.selectedEvent.location));
      }
    },
  },
}
</script>
