<script lang="ts" setup>
import CountBadge from "@/components/island/CountBadge.vue"

/**
 * The head of a band: the eyebrow, the heading, what the heading counts and the band's one way
 * on. The button stays beside the heading at every width and the heading wraps instead, so a
 * phone keeps the same head as desktop rather than stacking a full-width button under it.
 */
defineOptions({name: "BandHead"})

const {eyebrow = "", count = undefined, countSaid = "", testid = undefined} = defineProps<{
  eyebrow?: string
  heading: string
  /** How many of the thing the heading names there are, where the band counts something. */
  count?: number
  /** What the count counts, said to a reader who cannot see the badge sitting on the heading. */
  countSaid?: string
  testid?: string
}>()
</script>

<template>
  <div
    class="band-head"
    :data-testid="testid"
  >
    <div class="band-head__words">
      <p
        v-if="eyebrow"
        class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase"
      >
        {{ eyebrow }}
      </p>
      <h2 class="mt-2.5 font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
        <slot name="heading">
          {{ heading }}
        </slot><count-badge
          v-if="count !== undefined"
          :count="count"
          :said="countSaid"
          :testid="testid ? `${testid}-count` : undefined"
        />
      </h2>
    </div>
    <div
      v-if="$slots.default"
      class="band-head__way"
    >
      <slot />
    </div>
  </div>
</template>

<style scoped>
.band-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.25rem;
}

.band-head__words {
  flex: 1 1 auto;
  min-width: 0;
}

.band-head__way {
  flex: none;
}
</style>
