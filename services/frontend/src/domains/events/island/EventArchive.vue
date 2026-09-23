<script lang="ts" setup>
/**
 * Every event that has happened, as posters grouped by month and newest first, narrowed by
 * academic year and by title. An event nobody made a poster for gets a date plate with its
 * name on it. Older ones come a page at a time.
 */
import {computed} from "vue"
import {DateTime} from "luxon"
import CutButton from "@/components/island/CutButton.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import PosterArt from "@/components/island/PosterArt.vue"
import SegmentedChoice from "@/components/island/SegmentedChoice.vue"
import TextInput from "@/components/island/TextInput.vue"
import {srcsetOf} from "@/components/island/pictures"
import type {EventResponse} from ".."
import {plateOf, posterOf} from "./eventFacts"
import MonthHead from "./MonthHead.vue"
import {ALL_YEARS, usePastEvents} from "./usePastEvents"

defineOptions({name: "EventArchive"})

const {years, year, search, events, total, answered, months, hasOlder, showOlder, committeeOf} = usePastEvents()

const choices = computed(() => [
  {key: ALL_YEARS, label: "All years"},
  ...years.value.map(one => ({key: one.key, label: one.label})),
])

const dayOf = (event: EventResponse): string => DateTime.fromISO(event.startTime).toFormat("ccc d LLL")

const tileOf = (event: EventResponse) => {
  const poster = posterOf(event)
  return {
    banner: poster?.url,
    srcset: poster ? srcsetOf(poster) : undefined,
    width: poster?.width,
    height: poster?.height,
  }
}
</script>

<template>
  <div
    class="archive"
    data-testid="event-archive"
  >
    <div class="archive__bar">
      <div class="archive__bar-in">
        <segmented-choice
          v-model="year"
          aria-label="Academic year"
          class="archive__years"
          :options="choices"
          testid-prefix="event-archive-year"
        />
        <label class="archive__search">
          <svg
            aria-hidden="true"
            class="archive__glass"
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="1.8"
            viewBox="0 0 24 24"
          >
            <circle
              cx="11"
              cy="11"
              r="6.5"
            />
            <path d="m16 16 4.5 4.5" />
          </svg>
          <text-input
            v-model="search"
            aria-label="Search past events"
            placeholder="Search past events"
            testid="event-archive-search"
            type="text"
          />
        </label>
      </div>
    </div>

    <lead-band accent="var(--color-brand)">
      <p
        v-if="answered && events.length === 0"
        class="archive__none"
        data-testid="event-archive-none"
      >
        <template v-if="search.trim() !== ''">
          No past event is called anything like “{{ search.trim() }}”.
        </template>
        <template v-else-if="year !== ALL_YEARS">
          Nothing happened in that year.
        </template>
        <template v-else>
          No past events yet.
        </template>
      </p>

      <template
        v-for="month in months"
        :key="month.key"
      >
        <month-head
          :count="month.events.length"
          :name="month.name"
        />
        <ul class="archive__grid">
          <li
            v-for="event in month.events"
            :key="event.id"
          >
            <router-link
              class="archive__tile"
              :data-testid="`event-archive-tile-${event.id}`"
              :to="{name: 'event', params: {id: event.id}}"
            >
              <poster-art
                :alt="event.title"
                :banner="tileOf(event).banner"
                class="archive__art"
                :height="tileOf(event).height"
                sizes="(max-width: 767px) 50vw, 12rem"
                :srcset="tileOf(event).srcset"
                :title="event.title"
                v-bind="plateOf(event)"
                :where="event.location ?? undefined"
                :width="tileOf(event).width"
              />
              <span class="archive__day">{{ dayOf(event) }}</span>
              <span class="archive__by">{{ committeeOf(event) }}</span>
            </router-link>
          </li>
        </ul>
      </template>

      <div
        v-if="events.length > 0"
        class="archive__more"
      >
        <span data-testid="event-archive-shown">Showing {{ events.length }} of {{ total }}</span>
        <cut-button
          v-if="hasOlder"
          testid="event-archive-older"
          @click="showOlder"
        >
          Show older
        </cut-button>
      </div>
    </lead-band>
  </div>
</template>

<style scoped>
.archive__bar {
  border-block: 1px solid var(--color-hairline);
}

.archive__bar-in {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem 2rem;
  max-width: 72rem;
  margin: 0 auto;
  padding: 1.1rem 2rem;
}

/* A row of years rather than a dialog's two halves: no rule under it, and each as wide as it says. */
.archive__years {
  padding-bottom: 0;
  margin-bottom: 0;
  border-bottom: 0;
}

.archive__years :deep(.choice__cut) {
  flex: 0 1 auto;
  min-width: 7.5rem;
}

.archive__search {
  position: relative;
  flex: 0 1 22rem;
  min-width: min(100%, 16rem);
}

.archive__glass {
  position: absolute;
  top: 50%;
  left: 0.8rem;
  z-index: 1;
  width: 15px;
  height: 15px;
  color: var(--color-ash);
  translate: 0 -50%;
  pointer-events: none;
}

.archive__search :deep(.island-input) {
  padding-left: 2.3rem;
}

.archive__none {
  padding: 1rem 0;
  color: var(--color-ash);
}

.archive__grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 1.25rem 0.6rem;
  padding: 0;
  list-style: none;
}

.archive__tile {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  color: inherit;
  text-decoration: none;
}

.archive__art {
  margin-bottom: 0.45rem;
  transition: translate 0.2s var(--ease-out-quint);
}

.archive__tile:hover .archive__art,
.archive__tile:focus-visible .archive__art {
  translate: 0 -3px;
}

.archive__tile:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 3px;
}

.archive__day {
  font-family: var(--font-bitmap);
  font-size: 0.66rem;
  letter-spacing: 0.08em;
  color: var(--color-chalk);
  text-transform: uppercase;
}

.archive__by {
  font-size: 0.75rem;
  color: var(--color-ash);
}

.archive__more {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 1rem 1.5rem;
  padding: 2.5rem 0 1rem;
  font-size: 0.85rem;
  color: var(--color-ash);
}

@media (max-width: 1023px) {
  .archive__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 639px) {
  .archive__bar-in {
    padding-inline: 1.25rem;
  }
}

@media (max-width: 767px) {
  .archive__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .archive__search {
    flex-basis: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .archive__art {
    transition: none;
  }
}
</style>
