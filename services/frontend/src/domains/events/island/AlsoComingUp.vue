<script lang="ts" setup>
import BandHead from "@/components/island/BandHead.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import PosterArt from "@/components/island/PosterArt.vue"
import {srcsetOf} from "@/components/island/pictures"
import type {EventResponse} from ".."
import {posterOf, whenOf} from "./eventFacts"

/**
 * What else is coming, under an event's own page: a few posters and the way to all of them.
 * The posters are pinned dark, like every band of event art.
 */
defineOptions({name: "AlsoComingUp"})

const {events, total} = defineProps<{
  /** The few to show. */
  events: EventResponse[]
  /** How many else are coming, which the count says. */
  total: number
}>()
</script>

<template>
  <lead-band
    v-if="events.length > 0"
    accent="var(--color-brand)"
    testid="event-also"
  >
    <band-head
      :count="total"
      count-said="other upcoming events"
      heading="Also coming up"
    />

    <template #bleed>
      <div class="also island-dark">
        <router-link
          v-for="event in events"
          :key="event.id"
          class="also__poster"
          :data-testid="`event-also-${event.id}`"
          :to="`/events/${event.id}`"
        >
          <poster-art
            :alt="event.title"
            :banner="posterOf(event)?.url"
            sizes="(max-width: 767px) 50vw, 25vw"
            :srcset="posterOf(event) ? srcsetOf(posterOf(event)!) : undefined"
            :title="event.title"
            :when="`${whenOf(event).day} - ${whenOf(event).hours}`"
            :where="event.location ?? undefined"
          />
          <span class="also__foot">
            <span class="also__when">{{ whenOf(event).day }}</span>
            <span class="also__where">{{ event.location }}</span>
          </span>
        </router-link>
        <router-link
          class="also__more"
          data-testid="event-also-all"
          to="/events"
        >
          <span class="also__more-name">All upcoming events</span>
          <svg
            aria-hidden="true"
            fill="none"
            height="17"
            viewBox="0 0 20 12"
            width="28"
          ><path
            d="M0 6h17M13 1.5L18.5 6L13 10.5"
            stroke="currentColor"
            stroke-width="1.4"
          /></svg>
        </router-link>
      </div>
    </template>
  </lead-band>
</template>

<style scoped>
.also {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 2px;
  background-color: var(--color-ground);
}

.also__poster {
  display: flex;
  flex-direction: column;
  color: inherit;
}

.also__foot {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.85rem 1rem 1.1rem;
  background-color: var(--band-ground);
}

.also__when {
  font-family: var(--font-display);
  font-size: 0.95rem;
  text-transform: uppercase;
}

.also__where {
  font-size: 0.82rem;
  color: var(--color-ash);
}

.also__more {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 0.5rem;
  min-height: 100%;
  padding: 1.4rem;
  color: var(--color-chalk);
  background-color: var(--band-ground);
  transition: background-color 220ms ease;
}

.also__more:hover,
.also__more:focus-visible {
  background-color: color-mix(in oklab, var(--color-brand) 18%, var(--band-ground));
}

.also__more-name {
  font-family: var(--font-display);
  font-size: 1.5rem;
  line-height: 1.05;
  text-transform: uppercase;
}

@media (prefers-reduced-motion: reduce) {
  .also__more {
    transition: none;
  }
}

@media (max-width: 767px) {
  .also {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .also__more {
    min-height: 12rem;
  }
}
</style>
