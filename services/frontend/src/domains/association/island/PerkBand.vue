<script lang="ts">
/** One thing a membership gets somebody, said plainly and once. */
export interface Perk {
  id: string
  title: string
  body: string
}
</script>

<script lang="ts" setup>
/**
 * What membership gets you, as four claims rather than a bulleted list.
 *
 * On the page's own ground rather than a band ground, so it reads as the page talking between
 * the two bands that carry figures. Each claim is marked with the same lean the buttons are
 * cut on: the mark says where a claim starts, and nothing here is a sequence, so nothing is
 * numbered.
 */
defineOptions({name: "PerkBand"})

withDefaults(defineProps<{
  heading: string
  perks: Perk[]
  testid?: string
}>(), {testid: "membership-perks"})
</script>

<template>
  <section
    class="w-full"
    :data-testid="testid"
  >
    <div class="mx-auto w-full max-w-6xl px-5 py-9 sm:px-8 md:py-12">
      <h2 class="perk-band__heading">
        {{ heading }}
      </h2>

      <ul class="mt-7 grid gap-x-10 gap-y-7 md:grid-cols-2">
        <li
          v-for="perk in perks"
          :key="perk.id"
          class="perk-band__perk"
          :data-testid="`${testid}-${perk.id}`"
        >
          <h3 class="perk-band__title">
            {{ perk.title }}
          </h3>
          <p class="perk-band__body">
            {{ perk.body }}
          </p>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.perk-band__heading {
  max-width: 44rem;
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 3.4vw, 2.1rem);
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
}

/* The mark is a leaning bar in the association's blue, on the slant the buttons are cut at. */
.perk-band__perk {
  position: relative;
  padding-left: 1.25rem;
}

.perk-band__perk::before {
  content: "";
  position: absolute;
  top: 0.3rem;
  bottom: 0.3rem;
  left: 0;
  width: 3px;
  background-color: var(--color-brand);
  transform: skewX(-12deg);
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
