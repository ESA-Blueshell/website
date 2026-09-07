<script setup lang="ts">
import BandWash, {type Shape, type Tone} from "@/components/island/BandWash.vue"

/**
 * Thirty grounds a band could sit on, numbered, so one can be chosen by number.
 *
 * A scratch page, not a page of the site: it is here to be looked at and argued with, and to
 * be deleted once the choices are made. Every example is the real [BandWash] with real props,
 * so choosing number seventeen is choosing a tone and a shape that already work.
 *
 * Each is drawn twice, dark above light, with the same tone and the same shape in both: what a
 * wash does to a near-black page and what it does to a white one are different questions, and
 * the answer to one does not carry to the other.
 */
interface Sample {
  tone: Tone
  shape: Shape
}

const SHAPES: Shape[] = ["topleft", "bottomright", "corners", "diagonal", "glow"]
const TONES: Tone[] = ["sky", "mint", "lime", "lemon", "coral", "lilac"]

const SAMPLES: Sample[] = SHAPES.flatMap(shape => TONES.map(tone => ({shape, tone})))

const HALVES = ["dark", "light"] as const

const numberOf = (index: number): string => String(index + 1).padStart(2, "0")
</script>

<template>
  <v-main>
    <div class="washes">
      <div
        v-for="(sample, index) in SAMPLES"
        :key="numberOf(index)"
      >
        <!-- Each half is told which it is, rather than nesting `data-theme`: both theme blocks
             match a nested island and source order picks the winner, so every example took the
             document's own half. -->
        <div
          v-for="half in HALVES"
          :key="half"
          :class="`island island--${half}`"
        >
          <band-wash
            :shape="sample.shape"
            :testid="`wash-${numberOf(index)}-${half}`"
            :tone="sample.tone"
          >
            <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
              Example {{ numberOf(index) }} — {{ half }} — {{ sample.tone }} · {{ sample.shape }}
            </p>
            <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-3xl">
              Your logo on our posters
            </h2>
            <p class="mt-3 max-w-2xl font-body text-sm leading-relaxed text-ash sm:text-base">
              Blueshell is the gaming association of the University of Twente: a few hundred
              people from every study, playing together online and in person since 2017. This is
              the length a band's words usually run to, so the wash is judged against the amount
              of text it will actually carry.
            </p>
          </band-wash>
        </div>
      </div>
    </div>
  </v-main>
</template>

<style scoped>
/* Each half is its own island, so each can be the side of the theme it is meant to show. */
.washes :deep(.island) {
  min-height: 0;
}
</style>
