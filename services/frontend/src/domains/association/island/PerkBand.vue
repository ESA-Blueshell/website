<script lang="ts">
/** One thing a membership gets somebody, said plainly and once. */
export interface Perk {
  id: string
  title: string
  body: string
}
</script>

<script lang="ts" setup>
import {computed} from "vue"
import BandHead from "@/components/island/BandHead.vue"
import LeadBand from "@/components/island/LeadBand.vue"

/**
 * What membership gets you, as four claims rather than a bulleted list.
 *
 * On the page's own ground rather than a band ground, so it reads as the page talking between
 * the two bands that carry figures. Each claim is marked with the same lean the buttons are
 * cut on: the mark says where a claim starts, and nothing here is a sequence, so nothing is
 * numbered.
 */
defineOptions({name: "PerkBand"})

const props = withDefaults(defineProps<{
  heading: string
  perks: Perk[]
  eyebrow?: string
  /**
   * How a claim is marked: the lean the buttons are cut on, or an acid tick where the band is
   * a list of what somebody gets rather than an argument.
   */
  mark?: "lean" | "tick"
  /** How many claims share a row on a wide screen. A phone always reads them in one column. */
  columns?: 2 | 3
  /** A colour to wash the band in from the top left, as a lead band is; none keeps the page ground. */
  accent?: string
  testid?: string
}>(), {eyebrow: "", mark: "lean", columns: 2, accent: undefined, testid: "membership-perks"})

/* Washed, the band is a lead band, which brings the column and its padding with it. */
const root = computed(() => (props.accent
  ? {accent: props.accent, testid: props.testid}
  : {class: "w-full", "data-testid": props.testid}))
</script>

<template>
  <component
    :is="accent ? LeadBand : 'section'"
    v-bind="root"
  >
    <div :class="{'mx-auto w-full max-w-6xl px-5 py-9 sm:px-8 md:py-12': !accent}">
      <band-head
        :eyebrow="eyebrow"
        :heading="heading"
      >
        <slot />
      </band-head>

      <ul
        class="mt-7 grid gap-x-10 gap-y-7 md:grid-cols-2"
        :class="{'lg:grid-cols-3': columns === 3}"
      >
        <li
          v-for="perk in perks"
          :key="perk.id"
          class="perk-band__perk"
          :class="`perk-band__perk--${mark}`"
          :data-testid="`${testid}-${perk.id}`"
        >
          <svg
            v-if="mark === 'tick'"
            aria-hidden="true"
            class="perk-band__tick"
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2.2"
            viewBox="0 0 24 24"
          >
            <path d="M5 12.5 10 17.5 19 7" />
          </svg>
          <!-- The description sits under the title and is indented to it, at every width. -->
          <span class="perk-band__words">
            <h3 class="perk-band__title">
              {{ perk.title }}
            </h3>
            <p class="perk-band__body">
              {{ perk.body }}
            </p>
          </span>
        </li>
      </ul>
    </div>
  </component>
</template>

<style scoped>
/* The mark is a leaning bar in the association's blue, on the slant the buttons are cut at. */
.perk-band__perk--lean {
  position: relative;
  padding-left: 1.25rem;
}

.perk-band__perk--lean::before {
  content: "";
  position: absolute;
  top: 0.3rem;
  bottom: 0.3rem;
  left: 0;
  width: 3px;
  background-color: var(--color-brand);
  transform: skewX(-12deg);
}

.perk-band__perk--tick {
  display: flex;
  gap: 0.7rem;
  align-items: flex-start;
}

.perk-band__tick {
  flex: none;
  width: 1.15rem;
  height: 1.15rem;
  margin-top: 0.15rem;
  color: var(--color-eyebrow);
}

.perk-band__words {
  display: block;
  min-width: 0;
}

.perk-band__title {
  font-family: var(--font-display);
  font-size: 1rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.perk-band__body {
  margin-top: 0.45rem;
  max-width: 34rem;
  font-family: var(--font-body);
  font-size: 0.92rem;
  line-height: 1.55;
  color: var(--color-ash);
}
</style>
