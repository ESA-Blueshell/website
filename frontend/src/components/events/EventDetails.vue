<template>
  <v-card max-width="350px">
    <!-- Start of the toolbar in the selected event menu -->
    <!-- Includes the event's title and the location and add to calendar buttons -->
    <v-toolbar
      :color="toolbarColor || undefined"
      dark
    >
      <!-- Name of the event -->
      <v-toolbar-title
        v-if="eventTitle.length < 15"
        :text="eventTitle"
      />
      <marquee-text
        v-else
        :repeat="3"
        :duration="10"
      >
        <v-toolbar-title
          class="mr-5"
          :text="eventTitle"
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
      v-if="bannerUrl"
      :src="bannerUrl"
      style="width: 100%; object-fit: contain"
      alt="promo image for the event"
    >

    <v-card-text>
      <!-- Description of the event -->
      <p v-if="description">
        <!-- eslint-disable-next-line vue/no-v-html -->
        <span v-html="expand || !longDescription ? markdownToHtml(description) : markdownToHtml(firstHundredWords)+'...'" />
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
      <!-- Only show this part if there is a location for this event -->
      <v-divider
        v-if="location"
        class="my-2"
      />
      <p v-if="location">
        <b>Where</b>
        <br>
        {{ location }}
      </p>
      <!-- Only show this part if there is a price for this event -->
      <v-divider
        v-if="memberPrice !== undefined && memberPrice !== null"
      />
      <p
        v-if="memberPrice !== undefined && memberPrice !== null"
        class="mt-4"
      >
        <b>Price</b><br>
        Members: €{{ memberPrice }} <br>
        Non-members: €{{ publicPrice }}
      </p>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import MarqueeText from 'vue-marquee-text-component'
import {$goto} from '@/plugins/goto'
import markdownToHtml from '@/plugins/markdownToHtml.ts'
import type {EventDto, FileDto} from '@/lib'

// Props: accept object coming from calendar, normalize to EventDto shape for usage.
// We keep the prop flexible to avoid breaking callers, but all internal types are aligned to generated types.
const props = defineProps<{
  selectedEvent: EventDto
}>()

// Local state
const expand = ref(false)

// Normalization helpers
const se = computed(() => props.selectedEvent as Record<string, unknown>)

// Title: prefer EventDto.title, fallback to legacy "name"
const eventTitle = computed(() => {
  const title = (se.value.title as string | undefined) ?? (se.value.name as string | undefined)
  return title ?? ''
})

// Color is not part of EventDto; allow passthrough if present
const toolbarColor = computed(() => (se.value.color as string | undefined) ?? '')

// Description: EventDto.description, fallback to legacy "details"
const description = computed(() => {
  return (se.value.description as string | undefined) ?? (se.value.details as string | undefined) ?? ''
})

// Banner url: EventDto.banner is FileDto with url field, or string in some backends
const bannerUrl = computed(() => {
  const banner = se.value.banner as FileDto | string | undefined
  if (!banner) return ''
  if (typeof banner === 'string') return banner
  return banner.url ?? ''
})

// Dates: accept either Date (calendar local) or ISO strings (EventDto)
function toDate(d: unknown): Date | null {
  if (!d) return null
  if (d instanceof Date) return d
  if (typeof d === 'string') {
    const dt = new Date(d)
    return isNaN(dt.getTime()) ? null : dt
  }
  return null
}

const startDate = computed<Date | null>(() => {
  return toDate((se.value.start as unknown) ?? (se.value.startTime as unknown))
})

const endDate = computed<Date | null>(() => {
  return toDate((se.value.end as unknown) ?? (se.value.endTime as unknown))
})

// Location
const location = computed(() => (se.value.location as string | undefined) ?? '')

// Prices (numbers in generated types)
const memberPrice = computed<number | null>(() => {
  const mp = se.value.memberPrice as number | string | undefined
  if (mp === undefined || mp === null || mp === '') return null
  return typeof mp === 'string' ? Number(mp) : mp
})
const publicPrice = computed<number | null>(() => {
  const pp = se.value.publicPrice as number | string | undefined
  if (pp === undefined || pp === null || pp === '') return null
  return typeof pp === 'string' ? Number(pp) : pp
})

// Formatting
const formattedDate = computed(() => {
  const start = startDate.value
  const end = endDate.value
  if (!start) return ''
  const dateFmt = new Intl.DateTimeFormat('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric'
  }).format(start)

  if (!end) return dateFmt

  const startTime = new Intl.DateTimeFormat('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(start).replace(',', '')

  const endTime = new Intl.DateTimeFormat('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(end)

  return `${startTime} - ${endTime}`
})

const longDescription = computed(() => (description.value?.split(/\s+/).length ?? 0) > 100)
const firstHundredWords = computed(() => description.value.split(/\s+/).slice(0, 100).join(' '))

// Methods
function addToCal() {
  const googleId = se.value.googleId as string | undefined
  if (!googleId) return
  $goto(encodeURI(`https://calendar.google.com/event?action=TEMPLATE&tmeid=${googleId}&tmsrc=blueshellesports@gmail.com`))
}

function expandWords() {
  expand.value = true
}

function findLocation() {
  const loc = location.value
  if (!loc) return
  const lower = loc.toLowerCase()
  if (lower.includes('discord')) {
    $goto(encodeURI('https://discord.gg/23YMFQy'));
  } else if (lower.includes('pel')) {
    $goto(encodeURI('https://www.google.com/maps/search/?api=1&query=Predator Esports Lounge'));
  } else {
    $goto(encodeURI(`https://www.google.com/maps/search/?api=1&query=${loc}`));
  }
}
</script>
