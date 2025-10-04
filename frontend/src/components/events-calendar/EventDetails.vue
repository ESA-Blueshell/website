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
        :duration="10"
        :repeat="3"
      >
        <v-toolbar-title
          :text="eventTitle"
          class="mr-5"
        />
      </marquee-text>

      <v-spacer />
      <v-tooltip
        location="bottom"
        text="Find location"
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
        location="bottom"
        text="Add to calendar"
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
      alt="promo image for the event"
      style="width: 100%; object-fit: contain"
    >

    <v-card-text>
      <!-- Description of the event -->
      <p v-if="description">
        <!-- eslint-disable-next-line vue/no-v-html -->
        <span
          v-html="expand || !longDescription ? markdownToHtml(description) : markdownToHtml(firstHundredWords)+'...'"
        />
        <br v-if="!expand && longDescription">
        <a
          v-if="!expand && longDescription"
          @click="expandWords"
        >
          <b style="cursor: pointer">read more</b>
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

<script lang="ts" setup>
import {computed, ref} from "vue"
import MarqueeText from "vue-marquee-text-component"
import {$goto} from "@/plugins/goto.ts"
import markdownToHtml from "@/plugins/markdownToHtml.ts"
import type {Event, File} from "@/lib"

// Props: accept object coming from calendar, normalize to Event shape for usage.
// We keep the prop flexible to avoid breaking callers, but all internal types are aligned to generated types.
const props = defineProps<{
  modelValue: Event
}>()

// Local state
const expand = ref(false)

// Normalization helpers
const event = computed(() => props.modelValue as Record<string, unknown>)

// Title: prefer Event.title, fallback to legacy "name"
const eventTitle = computed(() => {
  const title = (event.value.title as string | undefined) ?? (event.value.name as string | undefined)
  return title ?? ""
})

// Color is not part of Event; allow passthrough if preeventnt
const toolbarColor = computed(() => (event.value.color as string | undefined) ?? "")

// Description: Event.description, fallback to legacy "details"
const description = computed(() => {
  return (event.value.description as string | undefined) ?? (event.value.details as string | undefined) ?? ""
})

// Banner url: Event.banner is File with url field, or string in some backends
const bannerUrl = computed(() => {
  const banner = event.value.banner as File | string | undefined
  if (!banner) return ""
  if (typeof banner === "string") return banner
  return banner.url ?? ""
})

// Dates: accept either Date (calendar local) or ISO strings (Event)
function toDate(d: unknown): Date | null {
  if (!d) return null
  if (d instanceof Date) return d
  if (typeof d === "string") {
    const dt = new Date(d)
    return isNaN(dt.getTime()) ? null : dt
  }
  return null
}

const startDate = computed<Date | null>(() => {
  return toDate((event.value.start as unknown) ?? (event.value.startTime as unknown))
})

const endDate = computed<Date | null>(() => {
  return toDate((event.value.end as unknown) ?? (event.value.endTime as unknown))
})

// Location
const location = computed(() => (event.value.location as string | undefined) ?? "")

// Prices (numbers in generated types)
const memberPrice = computed<number | null>(() => {
  const mp = event.value.memberPrice as number | string | undefined
  if (mp === undefined || mp === null || mp === "") return null
  return typeof mp === "string" ? Number(mp) : mp
})
const publicPrice = computed<number | null>(() => {
  const pp = event.value.publicPrice as number | string | undefined
  if (pp === undefined || pp === null || pp === "") return null
  return typeof pp === "string" ? Number(pp) : pp
})

// Formatting
const formattedDate = computed(() => {
  const start = startDate.value
  const end = endDate.value
  if (!start) return ""
  const dateFmt = new Intl.DateTimeFormat("en-US", {
    weekday: "long",
    month: "long",
    day: "numeric",
  }).format(start)

  if (!end) return dateFmt

  const startTime = new Intl.DateTimeFormat("en-US", {
    weekday: "long",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(start).replace(",", "")

  const endTime = new Intl.DateTimeFormat("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(end)

  return `${startTime} - ${endTime}`
})

const longDescription = computed(() => (description.value?.split(/\s+/).length ?? 0) > 100)
const firstHundredWords = computed(() => description.value.split(/\s+/).slice(0, 100).join(" "))

// Methods
function addToCal() {
  const googleId = event.value.googleId as string | undefined
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
  if (lower.includes("discord")) {
    $goto(encodeURI("https://discord.gg/23YMFQy"))
  } else if (lower.includes("pel")) {
    $goto(encodeURI("https://www.google.com/maps/search/?api=1&query=Predator Esports Lounge"))
  } else {
    $goto(encodeURI(`https://www.google.com/maps/search/?api=1&query=${loc}`))
  }
}
</script>
