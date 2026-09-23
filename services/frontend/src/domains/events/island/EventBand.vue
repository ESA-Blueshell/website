<script lang="ts" setup>
import {computed} from "vue"
import PosterArt from "@/components/island/PosterArt.vue"
import {srcsetOf} from "@/components/island/pictures"
import $markdownToHtml from "@/plugins/markdownToHtml"
import type {EventResponse} from ".."
import {deadlineOf, directionsOf, placesOf, posterOf, priceOf, soonOf, whenOf} from "./eventFacts"

/**
 * An event as a square band: its poster on the left, uncut, then a label, the title and the
 * facts, with room for what can be done and for what opens under it. The events page leads
 * with the next event in it, and an event's own page is headed by it.
 */
defineOptions({name: "EventBand"})

const {event, eyebrow, heading = "h2", full = false, blurb = false} = defineProps<{
  event: EventResponse
  eyebrow: string
  /** The title is the page's heading on the event's own page, and a band's on the list. */
  heading?: "h1" | "h2"
  /** With the price and the sign-up deadline, as the event's own page says them. */
  full?: boolean
  /** With the description cut short under the facts, where the band stands in for the page. */
  blurb?: boolean
}>()

const poster = computed(() => posterOf(event))
const when = computed(() => whenOf(event))
const soon = computed(() => soonOf(event))
const places = computed(() => placesOf(event))
const price = computed(() => priceOf(event))
const deadline = computed(() => deadlineOf(event))

/* The description as its words: the band says what it is, the event's page says it all. */
const words = computed<string>(() => {
  const holder = document.createElement("div")
  holder.innerHTML = $markdownToHtml(event.description ?? "")
  // A detached element's text is a string, never null.
  return (holder.textContent as string).replace(/\s+/gu, " ").trim()
})
</script>

<template>
  <section
    :id="String(event.id)"
    class="band"
    :data-testid="`event-card-${event.id}`"
  >
    <poster-art
      :alt="event.title"
      :banner="poster?.url"
      class="band__art"
      :height="poster?.height"
      sizes="(max-width: 1023px) 100vw, 36rem"
      :srcset="poster ? srcsetOf(poster) : undefined"
      :title="event.title"
      :when="`${when.day} - ${when.hours}`"
      :where="event.location ?? undefined"
      :width="poster?.width"
    />

    <div class="band__body">
      <div class="band__label">
        <p class="band__eyebrow">
          {{ eyebrow }}
        </p>
        <span
          v-if="soon"
          class="band__tag"
          data-testid="event-band-soon"
        >{{ soon }}</span>
        <span
          v-if="event.membersOnly"
          class="band__tag band__tag--quiet"
        >Members only</span>
      </div>

      <component
        :is="heading"
        class="band__title"
      >
        {{ event.title }}
      </component>

      <div
        class="band__facts"
        :class="{'band__facts--four': full}"
      >
        <div class="band__fact">
          <p class="band__fact-label">
            When
          </p>
          <p class="band__fact-value">
            {{ when.day }}
          </p>
          <p class="band__fact-sub">
            {{ when.hours }}
          </p>
        </div>
        <div
          v-if="event.location"
          class="band__fact"
        >
          <p class="band__fact-label">
            Where
          </p>
          <p class="band__fact-value">
            {{ event.location }}
          </p>
          <a
            class="band__link"
            :href="directionsOf(event.location)"
            rel="noopener"
            target="_blank"
          >Directions</a>
        </div>
        <div class="band__fact">
          <p class="band__fact-label">
            Places
          </p>
          <p
            class="band__fact-value"
            data-testid="event-band-places"
          >
            {{ places.said }}
          </p>
          <div
            v-if="places.taken !== undefined"
            aria-hidden="true"
            class="band__meter"
          >
            <span :style="{width: `${places.taken * 100}%`}" />
          </div>
          <p
            v-if="full && deadline"
            class="band__fact-sub"
          >
            {{ deadline }}
          </p>
        </div>
        <div
          v-if="full"
          class="band__fact"
        >
          <p class="band__fact-label">
            Price
          </p>
          <p
            class="band__fact-value"
            data-testid="event-band-price"
          >
            {{ price.said }}
          </p>
          <p class="band__fact-sub">
            {{ price.sub }}
          </p>
        </div>
      </div>

      <p
        v-if="blurb && words"
        class="band__blurb"
      >
        {{ words }}
      </p>

      <div class="band__actions">
        <slot name="actions" />
      </div>

      <slot />
    </div>
  </section>
</template>

<style scoped>
.band {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: 36rem minmax(0, 1fr);
  background-color: var(--band-ground);
}

.band::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background: radial-gradient(70% 120% at 100% 0, color-mix(in oklab, var(--color-brand) 9%, transparent) 0%, transparent 62%);
}

.band__art {
  align-self: start;
}

.band__body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1.35rem;
  min-width: 0;
  padding: 2.5rem 4rem 2.5rem 3rem;
}

.band__label {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
}

.band__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.band__tag {
  display: inline-flex;
  align-items: center;
  padding: 0.26rem 0.55rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
  white-space: nowrap;
  border: 1px solid currentColor;
}

.band__tag--quiet {
  color: var(--color-ash);
}

.band__title {
  font-family: var(--font-display);
  font-size: 3.6rem;
  line-height: 0.98;
  text-transform: uppercase;
  overflow-wrap: break-word;
}

.band__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  row-gap: 1.2rem;
}

.band__facts--four {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.band__fact {
  position: relative;
  min-width: 0;
  padding-inline: 1.25rem;
}

/* A hairline on the house lean between facts, rather than a box around each. */
.band__fact::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.band__fact:first-child,
.band__facts--four .band__fact:nth-child(odd) {
  padding-inline-start: 0;
}

.band__fact:first-child::before,
.band__facts--four .band__fact:nth-child(odd)::before {
  display: none;
}

.band__fact-label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.band__fact-value {
  margin-top: 0.45rem;
  font-family: var(--font-display);
  font-size: 1.05rem;
  line-height: 1.2;
  text-transform: uppercase;
}

.band__fact-sub {
  margin-top: 0.25rem;
  font-size: 0.9rem;
  line-height: 1.4;
  color: var(--color-ash);
}

.band__link {
  display: inline-flex;
  margin-top: 0.3rem;
  font-size: 0.85rem;
  color: var(--color-brand);
}

.band__link:hover,
.band__link:focus-visible {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.band__meter {
  position: relative;
  height: 4px;
  margin-top: 0.6rem;
  overflow: hidden;
  background: color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.band__meter > span {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-brand);
}

.band__blurb {
  display: -webkit-box;
  max-width: 40rem;
  overflow: hidden;
  font-size: 1rem;
  line-height: 1.6;
  color: var(--color-ash);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.band__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1rem;
}

@media (max-width: 1023px) {
  .band {
    grid-template-columns: 1fr;
  }

  .band__body {
    padding: 1.25rem 1.25rem 1.75rem;
    gap: 1.1rem;
  }

  .band__title {
    font-size: 2.2rem;
  }

  .band__facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .band__fact:nth-child(odd) {
    padding-inline-start: 0;
  }

  .band__fact:nth-child(odd)::before {
    display: none;
  }
}
</style>
