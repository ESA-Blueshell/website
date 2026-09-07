<script setup lang="ts">
import BandWash, {type Shape, type Tone} from "@/components/island/BandWash.vue"

/**
 * The grounds a band can sit on, numbered, so one can be chosen by number.
 *
 * A scratch page, not a page of the site: it is here to be looked at and argued with, and to be
 * deleted once the choices are made. Every example is the real [BandWash] with real props.
 *
 * The pattern is laid down once, on the page, and the bands sit over it — which is the point of
 * the page as much as the washes are. Nothing here restarts it, so the shells run unbroken from
 * the first band to the last however the light changes over them.
 *
 * Each example is drawn twice, dark above light, because what a wash does to a near-black band
 * and what it does to a white one are different questions.
 */
interface Sample {
  tone: Tone
  shape: Shape
  /** The far corner's colour, where the two corners are meant to differ. */
  toneAlt?: Tone
}

const SHAPES: Shape[] = ["topleft"]
const TONES: Tone[] = ["brand"]

/** Two corners, two colours, which with two colours to choose from is one pair either way up. */
const PAIRS: Sample[] = [
  {shape: "pair", tone: "brand", toneAlt: "green"},
  {shape: "pair", tone: "green", toneAlt: "brand"},
]

/** The ground and the pattern, and no colour at all. */
const PLAIN: Sample[] = [{shape: "plain", tone: "plain"}]

const SAMPLES: Sample[] = [
  ...SHAPES.flatMap(shape => TONES.map(tone => ({shape, tone}))),
  ...PAIRS,
  ...PLAIN,
]

const HALVES = ["dark", "light"] as const

const numberOf = (index: number): string => String(index + 1).padStart(2, "0")
</script>

<template>
  <v-main>
    <!-- One island, one pattern, laid down here rather than on each band. -->
    <div class="island island--dark motif washes">
      <template
        v-for="(sample, index) in SAMPLES"
        :key="numberOf(index)"
      >
        <band-wash
          v-for="half in HALVES"
          :key="half"
          :class="`band--${half}`"
          :shape="sample.shape"
          :testid="`wash-${numberOf(index)}-${half}`"
          :tone="sample.tone"
          :tone-alt="sample.toneAlt"
        >
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            Example {{ numberOf(index) }} — {{ half }} — {{ sample.tone
            }}<template v-if="sample.toneAlt">
              to {{ sample.toneAlt }}
            </template> ·
            {{ sample.shape }}
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
      </template>
    </div>
  </v-main>
</template>

<style scoped>
/* The pattern is drawn behind the bands, so the island itself draws no tile of its own. */
.washes {
  background-image: none;
}
</style>
