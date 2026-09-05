<script lang="ts" setup>
import SliceBand from "@/components/island/SliceBand.vue"
import type {EventSlice} from "../events"

/**
 * What the association has actually been running, in its own promo art.
 *
 * The island's slice band rather than anything new: the art, the rendition ladder, the diagonal
 * cut, the hover on a desktop and the scroll-centre on a phone are all already settled there.
 * The band draws nothing at all where there are no slices, so a caller hands it what qualifies
 * and the page keeps its argument without a short row on it.
 */
defineOptions({name: "EventBand"})

const props = withDefaults(defineProps<{
  heading: string
  slices: EventSlice[]
  testid?: string
}>(), {testid: "membership-events"})

/** Where an event happened, by id: the band hands its slot the band's own item, not this one. */
const whereOf = (id: number | string) =>
  props.slices.find(slice => slice.id === id)?.where ?? ""
</script>

<template>
  <section
    v-if="slices.length > 0"
    class="w-full"
    :data-testid="testid"
  >
    <div class="mx-auto w-full max-w-6xl px-5 pt-9 pb-5 sm:px-8 md:pt-12">
      <h2
        class="event-band__heading"
        :data-testid="`${testid}-heading`"
      >
        {{ heading }}
      </h2>
    </div>

    <slice-band
      accent="var(--color-brand)"
      :items="slices"
      :testid-prefix="testid"
    >
      <template #details="{item}">
        <p
          v-if="whereOf(item.id)"
          class="event-band__where"
          :data-testid="`${testid}-where-${item.id}`"
        >
          {{ whereOf(item.id) }}
        </p>
      </template>
    </slice-band>
  </section>
</template>

<style scoped>
.event-band__heading {
  max-width: 44rem;
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 3.4vw, 2.1rem);
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.event-band__where {
  font-family: var(--font-body);
  font-size: 0.875rem;
  line-height: 1.5;
  color: var(--color-chalk);
}
</style>
