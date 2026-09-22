<script setup lang="ts">
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import PosterStrip from "@/components/island/PosterStrip.vue"
import EventPosterDialog from "./EventPosterDialog.vue"
import {loadEventOnShow} from "@/domains/association/adapters/association"
import type {EventOnShow} from "@/domains/association/adapters/association"
import {useEventsOnShow} from "./useEventsOnShow"

/**
 * The events the association ran lately, as proof rather than as a claim.
 *
 * The band is absent rather than short, and absent while the read is in flight: a page that
 * grows a heading promising what goes on here and then empties it reads worse than one that
 * never promised. The composable decides how few is too few; this only draws what it is given.
 *
 * Each page names it in its own words, because "lately" means something different on a page
 * asking somebody to join than on one asking a company to sponsor.
 */
const props = defineProps<{
  eyebrow: string
  heading: string
  testid: string
}>()

const {posters, more, held} = useEventsOnShow()

const route = useRoute()
const router = useRouter()

const opened = ref<EventOnShow | undefined>(undefined)
const open = computed({
  get: () => opened.value !== undefined,
  set: (down: boolean) => {
    if (!down) close()
  },
})

/* The id travels in the address, so the link somebody copies opens the same event. */
const show = (id: number | string) => {
  opened.value = held.value.find(one => one.id === Number(id))
  void router.replace({query: {...route.query, event: String(id)}})
}

const close = () => {
  opened.value = undefined
  const {event: _asked, ...rest} = route.query
  void router.replace({query: rest})
}

/*
 * The event the address names is read by its own id, the strip having read nothing yet when
 * this runs: the events page pages through what is upcoming, so an older event falls on a page
 * nobody can name in a link.
 */
const openFromAddress = async () => {
  const asked = route.query.event
  const id = Number(Array.isArray(asked) ? asked[0] : asked)
  if (!Number.isFinite(id) || id <= 0) return
  opened.value = await loadEventOnShow(id)
}

onMounted(openFromAddress)
watch(() => route.query.event, () => {
  if (route.query.event === undefined) opened.value = undefined
})
</script>

<template>
  <section
    v-if="posters.length > 0"
    class="w-full"
    :data-testid="props.testid"
  >
    <div class="mx-auto w-full max-w-6xl px-5 pt-10 pb-6 sm:px-8">
      <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
        {{ props.eyebrow }}
      </p>
      <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
        {{ props.heading }}
      </h2>
    </div>
    <!-- Full width, the way the slice band it replaces ran: the art is the point. -->
    <poster-strip
      class="pb-10"
      :items="posters"
      pan-back-label="Earlier events"
      pan-on-label="Later events"
      :testid-prefix="`${props.testid}-strip`"
      @needs-more="more"
      @open="show"
    />

    <event-poster-dialog
      v-model:open="open"
      :event="opened"
      :testid="`${props.testid}-dialog`"
    />
  </section>
</template>
