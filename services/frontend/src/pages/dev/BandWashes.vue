<script setup lang="ts">
import BandWash, {type Half, type Shape, type Tone} from "@/components/island/BandWash.vue"

/**
 * Every wash a band can take, drawn on every ground it can take, numbered.
 *
 * A scratch page, not a page of the site: it is here to be looked at and argued with, and to be
 * deleted once the choices are made. There are no knobs on it. Each example is a combination
 * that is already known to work somewhere on the site, so choosing number eleven is choosing
 * something whole rather than a set of figures that happen to sit well together.
 *
 * The pattern is laid down by each band and lines up across them all, so a run of bands reads
 * as one page with the light changing down it.
 */
interface Sample {
  shape: Shape
  tone: Tone
  half: Half
}

const SHAPES: Shape[] = ["corner", "corner-far", "sweep", "glass", "quiet"]
const TONES: Tone[] = ["brand", "green"]
const HALVES: Half[] = ["page", "light", "dark", "blue"]

/** What each wash is, and where on the site it already does its work. */
const NOTES: Record<Shape, string> = {
  corner: "One radial far larger than the band, anchored at its top left, with a little of the colour along the top. From the board panels.",
  "corner-far": "The same, entering from the bottom right, for a band following one lit from the near corner.",
  sweep: "Lit along the whole top edge rather than from one end of it, for a band carrying a heading a reader starts at. From the esports strip.",
  glass: "Two wide ellipses anchored off the band, one below and one above, so only their tails fall inside and there is no origin to see. From the call band the esports pages end on.",
  quiet: "The band's ground and the pattern behind it, and no colour at all.",
}

const SAMPLES: Sample[] = HALVES.flatMap(half =>
  SHAPES.flatMap(shape => TONES.map(tone => ({shape, tone, half}))),
)

const numberOf = (index: number): string => String(index + 1).padStart(2, "0")
</script>

<template>
  <v-main>
    <div class="island island--dark washes">
      <band-wash
        v-for="(sample, index) in SAMPLES"
        :key="numberOf(index)"
        :half="sample.half"
        :shape="sample.shape"
        :testid="`wash-${numberOf(index)}`"
        :tone="sample.tone"
      >
        <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
          {{ numberOf(index) }} — {{ sample.shape }} · {{ sample.tone }} · on {{ sample.half }}
        </p>
        <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-3xl">
          Your logo on our posters
        </h2>
        <p class="mt-3 max-w-2xl font-body text-sm leading-relaxed text-ash sm:text-base">
          {{ NOTES[sample.shape] }}
        </p>
      </band-wash>
    </div>
  </v-main>
</template>

<style scoped>
/* Each band draws the pattern; the island drawing its own would be a second one under it. */
.washes {
  background-image: none;
}
</style>
