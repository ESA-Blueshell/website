<template>
  <v-card max-width="350px">
    <v-toolbar
      color="primary"
      dark
    >
      <v-toolbar-title v-if="eventTitle.length < 15">
        {{ eventTitle }}
      </v-toolbar-title>

      <marquee-text
        v-else
        :key="eventTitle"
        :duration="10"
        :repeat="3"
      >
        <v-toolbar-title class="mr-5">
          {{ eventTitle }}
        </v-toolbar-title>
      </marquee-text>

      <v-spacer />
      <v-tooltip
        location="bottom"
        text="Find location"
      >
        <template #activator="{ props: locationProps }">
          <v-btn
            icon="mdi-google-maps"
            v-bind="locationProps"
            @click="findLocation"
          />
        </template>
      </v-tooltip>
      <v-tooltip
        location="bottom"
        text="Add to calendar"
      >
        <template #activator="{ props: calProps }">
          <v-btn
            icon="mdi-calendar"
            v-bind="calProps"
            @click="addToCal"
          />
        </template>
      </v-tooltip>
    </v-toolbar>

    <img
      v-if="bannerUrl"
      :key="bannerUrl"
      :src="bannerUrl"
      alt="promo image for the event"
      style="width: 100%; object-fit: contain"
    >

    <v-card-text>
      <p v-if="description">
        <span
          v-html="markdownToHtml(description)"
        />
      </p>
      <p v-else>
        No description...
      </p>
      <v-divider class="my-2" />
      <p>
        <b>When</b>
        <br>
        {{ formattedDate }}
      </p>
      <v-divider
        v-if="location"
        class="my-2"
      />
      <p v-if="location">
        <b>Where</b>
        <br>
        {{ location }}
      </p>
      <v-divider
        v-if="!!memberPrice || !!publicPrice"
      />
      <p
        v-if="!!memberPrice || !!publicPrice"
        class="mt-4"
      >
        <b>Price</b><br>
        <span>Members: €{{ memberPrice }}</span><br>
        <span v-if="!!publicPrice">Non-members: €{{ publicPrice }}</span>
      </p>
    </v-card-text>
  </v-card>
</template>

<script lang="ts" setup>
import {computed, onBeforeUnmount, ref, toRef, watch} from "vue"
import MarqueeText from "vue-marquee-text-component"
import {$goto} from "@/plugins/goto.ts"
import markdownToHtml from "@/plugins/markdownToHtml.ts"
import {downloadEventBanner, type Event} from "@/lib"
import {DateTime} from "luxon"

const props = defineProps<{ modelValue: Event }>()

const event = toRef(props, "modelValue")

const expand = ref(false)

const eventTitle = computed(() => (event.value?.title as string | undefined) ?? "")
const description = computed(() => (event.value?.description as string) ?? "")

const bannerUrl = ref<string | null>(null)
let lastBannerId: number | null = null

async function loadBanner() {
  const id = event.value?.banner?.id ?? null

  if (!id) {
    if (bannerUrl.value) URL.revokeObjectURL(bannerUrl.value)
    bannerUrl.value = null
    lastBannerId = null
    return
  }

  lastBannerId = id
  try {
    const resp = await downloadEventBanner({
      path: {bannerId: id},
      throwOnError: true,
      responseType: "blob",
    })
    if (lastBannerId !== id) return

    const blob = resp?.data as Blob
    if (bannerUrl.value) URL.revokeObjectURL(bannerUrl.value)
    bannerUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    console.error("Failed to download event banner:", e)
  }
}

watch(() => event.value?.banner?.id, loadBanner, {immediate: true})

onBeforeUnmount(() => {
  if (bannerUrl.value) URL.revokeObjectURL(bannerUrl.value)
})

const startDate = computed<Date | null>(() => {
  return event.value?.startTime ? DateTime.fromISO(event.value.startTime).toJSDate() : null
})

const endDate = computed<Date | null>(() => {
  return event.value?.endTime ? DateTime.fromISO(event.value.endTime).toJSDate() : null
})

const location = computed(() => (event.value.location as string | undefined) ?? "")

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

function addToCal() {
  const googleId = event.value.googleId as string | undefined
  if (!googleId) return
  $goto(encodeURI(`https://calendar.google.com/event?action=TEMPLATE&tmeid=${googleId}&tmsrc=blueshellesports@gmail.com`))
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
