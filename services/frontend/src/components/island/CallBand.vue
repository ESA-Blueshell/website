<script lang="ts">
import type {CutTone} from "./CutButton.vue"

/**
 * One way on from the band: what it says, where it goes and how much it insists.
 *
 * A path leads somewhere on the site and is followed by the router; anything else — a Discord
 * invite, a mail address — is a plain link. [away] is what opens a new tab, which is only ever
 * right for somewhere that is not the site.
 */
export interface CallAction {
  label: string
  href: string
  tone?: CutTone
  away?: boolean
  testid?: string
}

/** What a band says and where it leads, which is the whole of one call. */
export interface Call {
  eyebrow?: string
  headline: string
  body: string
  actions: CallAction[]
  testid?: string
}
</script>

<script lang="ts" setup>
import CutButton from "./CutButton.vue"

defineOptions({name: "CallBand"})

withDefaults(defineProps<{
  headline: string
  body: string
  actions: CallAction[]
  /** The smaller line above the headline, where the band has one. */
  eyebrow?: string
  testid?: string
}>(), {eyebrow: "", testid: "island-call"})
</script>

<template>
  <section
    class="call-band"
    :data-testid="testid"
  >
    <span
      aria-hidden="true"
      class="call-band__wash"
    />

    <div class="call-band__inner">
      <div class="call-band__say">
        <p
          v-if="eyebrow"
          class="call-band__eyebrow"
        >
          {{ eyebrow }}
        </p>
        <h2 class="call-band__title">
          {{ headline }}
        </h2>
        <p class="call-band__line">
          {{ body }}
        </p>
      </div>

      <div class="call-band__actions">
        <cut-button
          v-for="action in actions"
          :key="action.href"
          :away="action.away"
          :href="action.href"
          :testid="action.testid"
          :tone="action.tone"
        >
          {{ action.label }}
        </cut-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
/*
 * Full width and shallow, like the strip above it: this is the last band of the page rather
 * than a card sitting on it.
 */
/* A band, on the shared band ground: see island.css. */
.call-band {
  background-color: var(--band-ground);
  position: relative;
  isolation: isolate;
  width: 100%;
  overflow: hidden;
}

.call-band__wash {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(60rem 18rem at 12% 120%, color-mix(in oklab, var(--color-brand) 26%, transparent), transparent 70%),
    radial-gradient(40rem 14rem at 88% -30%, color-mix(in oklab, var(--color-acid) 10%, transparent), transparent 70%);
  pointer-events: none;
}

.call-band__inner {
  position: relative;
  display: flex;
  width: 100%;
  max-width: 88rem;
  margin: 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem 2.5rem;
  padding: 1.35rem 1.5rem;
  flex-wrap: wrap;
}

.call-band__eyebrow {
  font-family: var(--font-body);
  font-size: 0.68rem;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.call-band__title {
  font-family: var(--font-display);
  font-size: 1.15rem;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.call-band__line {
  margin-top: 0.3rem;
  max-width: 34rem;
  font-size: 0.8rem;
  line-height: 1.45;
  color: var(--color-ash);
}

.call-band__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

@media (max-width: 767px) {
  .call-band__inner {
    padding: 1.15rem 1.15rem 1.35rem;
  }
}
</style>
