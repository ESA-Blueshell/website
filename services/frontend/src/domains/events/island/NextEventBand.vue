<script lang="ts" setup>
import {computed, ref} from "vue"
import PosterArt from "@/components/island/PosterArt.vue"
import {srcsetOf} from "@/components/island/pictures"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"
import store from "@/plugins/store"
import $markdownToHtml from "@/plugins/markdownToHtml"
import type {EventResponse, EventSignUpResponse} from ".."
import EventActions from "./EventActions.vue"
import {directionsOf, placesOf, posterOf, soonOf, whenOf} from "./eventFacts"

/**
 * The next event, as the page's focal band: its poster square and uncut on the left, then when,
 * where and how full it is, what it is and the way to sign up. The sign-up form opens under it.
 */
defineOptions({name: "NextEventBand"})

type CommitteeOption = {id: number, name: string}

const {event, signUps = [], committees = []} = defineProps<{
  event: EventResponse
  signUps?: EventSignUpResponse[]
  committees?: CommitteeOption[]
}>()

const emit = defineEmits<{
  "update:event": [event: EventResponse]
  "delete:event": [id: number]
  "update:signUp": [signUp: EventSignUpResponse]
  "delete:signUp": [id: number]
}>()

const signing = ref(false)
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)

const poster = computed(() => posterOf(event))
const when = computed(() => whenOf(event))
const soon = computed(() => soonOf(event))
const places = computed(() => placesOf(event))
const signUp = computed(() => signUps.find(one => one.eventId === event.id))
const committee = computed(() => committees.find(one => one.id === event.committeeId)?.name ?? "")

/* The description as its words, clamped: the band says what it is, the event page says it all. */
const blurb = computed<string>(() => {
  const holder = document.createElement("div")
  holder.innerHTML = $markdownToHtml(event.description ?? "")
  // A detached element's text is a string, never null.
  return (holder.textContent as string).replace(/\s+/gu, " ").trim()
})

function signedUp(saved: EventSignUpResponse) {
  signing.value = false
  emit("update:signUp", saved)
}

function signedOut(id: number) {
  signing.value = false
  emit("delete:signUp", id)
}
</script>

<template>
  <section
    :id="String(event.id)"
    class="next"
    :data-testid="`event-card-${event.id}`"
  >
    <poster-art
      :alt="event.title"
      :banner="poster?.url"
      class="next__art"
      :height="poster?.height"
      sizes="(max-width: 767px) 100vw, 36rem"
      :srcset="poster ? srcsetOf(poster) : undefined"
      :title="event.title"
      :when="`${when.day} - ${when.hours}`"
      :where="event.location ?? undefined"
      :width="poster?.width"
    />

    <div class="next__body">
      <div class="next__label">
        <p class="next__eyebrow">
          Next up
        </p>
        <span
          v-if="soon"
          class="next__tag"
          data-testid="next-event-soon"
        >{{ soon }}</span>
        <span
          v-if="event.membersOnly"
          class="next__tag next__tag--quiet"
        >Members only</span>
      </div>

      <h2 class="next__title">
        {{ event.title }}
      </h2>

      <div class="next__facts">
        <div class="next__fact">
          <p class="next__fact-label">
            When
          </p>
          <p class="next__fact-value">
            {{ when.day }}
          </p>
          <p class="next__fact-sub">
            {{ when.hours }}
          </p>
        </div>
        <div
          v-if="event.location"
          class="next__fact"
        >
          <p class="next__fact-label">
            Where
          </p>
          <p class="next__fact-value">
            {{ event.location }}
          </p>
          <a
            class="next__link"
            :href="directionsOf(event.location)"
            rel="noopener"
            target="_blank"
          >Directions</a>
        </div>
        <div class="next__fact">
          <p class="next__fact-label">
            Places
          </p>
          <p
            class="next__fact-value"
            data-testid="next-event-places"
          >
            {{ places.said }}
          </p>
          <div
            v-if="places.taken !== undefined"
            aria-hidden="true"
            class="next__meter"
          >
            <span :style="{width: `${places.taken * 100}%`}" />
          </div>
        </div>
      </div>

      <p
        v-if="blurb"
        class="next__blurb"
      >
        {{ blurb }}
      </p>

      <div class="next__actions">
        <event-actions
          v-model:signing="signing"
          :committees="committees"
          :event="event"
          :sign-ups="signUps"
          @delete:event="emit('delete:event', $event)"
          @delete:sign-up="signedOut"
          @update:event="emit('update:event', $event)"
        />
        <span
          v-if="committee"
          class="next__by"
        >By {{ committee }}</span>
      </div>

      <event-sign-up-form
        v-if="signing"
        class="next__form"
        :event="event"
        :initial-sign-up="signUp"
        :show-guest-form="!isLoggedIn"
        @delete:sign-up="signedOut"
        @update:sign-up="signedUp"
      />
    </div>
  </section>
</template>

<style scoped>
.next {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: 36rem minmax(0, 1fr);
  background-color: var(--band-ground);
}

.next::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background: radial-gradient(70% 120% at 100% 0, color-mix(in oklab, var(--color-brand) 9%, transparent) 0%, transparent 62%);
}

.next__art {
  align-self: start;
}

.next__body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1.35rem;
  min-width: 0;
  padding: 2.5rem 4rem 2.5rem 3rem;
}

.next__label {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
}

.next__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.next__tag {
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

.next__tag--quiet {
  color: var(--color-ash);
}

.next__title {
  font-family: var(--font-display);
  font-size: 3.6rem;
  line-height: 0.98;
  text-transform: uppercase;
  overflow-wrap: break-word;
}

.next__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  row-gap: 1.2rem;
}

.next__fact {
  position: relative;
  min-width: 0;
  padding-inline: 1.25rem;
}

/* A hairline on the house lean between facts, rather than a box around each. */
.next__fact::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.next__fact:first-child {
  padding-inline-start: 0;
}

.next__fact:first-child::before {
  display: none;
}

.next__fact-label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.next__fact-value {
  margin-top: 0.45rem;
  font-family: var(--font-display);
  font-size: 1.05rem;
  line-height: 1.2;
  text-transform: uppercase;
}

.next__fact-sub {
  margin-top: 0.25rem;
  font-size: 0.9rem;
  line-height: 1.4;
  color: var(--color-ash);
}

.next__link {
  display: inline-flex;
  margin-top: 0.3rem;
  font-size: 0.85rem;
  color: var(--color-brand);
}

.next__link:hover,
.next__link:focus-visible {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.next__meter {
  position: relative;
  height: 4px;
  margin-top: 0.6rem;
  overflow: hidden;
  background: color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.next__meter > span {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-brand);
}

.next__blurb {
  display: -webkit-box;
  max-width: 40rem;
  overflow: hidden;
  font-size: 1rem;
  line-height: 1.6;
  color: var(--color-ash);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.next__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1rem;
}

.next__by {
  font-size: 0.82rem;
  color: var(--color-ash);
}

@media (max-width: 1023px) {
  .next {
    grid-template-columns: 1fr;
  }

  .next__body {
    padding: 1.25rem 1.25rem 1.75rem;
    gap: 1.1rem;
  }

  .next__title {
    font-size: 2.2rem;
  }

  .next__facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .next__fact:nth-child(odd) {
    padding-inline-start: 0;
  }

  .next__fact:nth-child(odd)::before {
    display: none;
  }
}
</style>
