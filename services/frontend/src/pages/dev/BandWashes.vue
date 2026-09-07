<script setup lang="ts">
import BandWash, {type Shape, type Tone, type Width} from "@/components/island/BandWash.vue"

/**
 * Thirty grounds a band could sit on, numbered, so one can be chosen by number.
 *
 * A scratch page, not a page of the site: it is here to be looked at and argued with, and to
 * be deleted once the choices are made. Every example is the real [BandWash] with real props,
 * so choosing number seventeen is choosing a tone, a shape and a width that already work.
 *
 * Half are drawn on the light half of the theme and half on the dark, alternating, because a
 * wash that reads on one can disappear on the other. Each example says what it is made of
 * under its own words, and each is told which half to be rather than taking the document's.
 */
interface Sample {
  tone: Tone
  shape: Shape
  width: Width
}

/** The whole grid of it: every shape against every tone, in a fixed order. */
const SHAPES: Shape[] = ["topleft", "bottomright", "corners", "diagonal", "glow"]
const TONES: Tone[] = ["sky", "mint", "lime", "lemon", "coral", "lilac"]

const SAMPLES: Sample[] = SHAPES.flatMap((shape, row) =>
  TONES.map((tone, column) => ({
    shape,
    tone,
    // Alternating, so neither width is only ever seen in one shape or one tone.
    width: (row + column) % 2 === 0 ? "full" : "inset" as Width,
  })),
)

const themeOf = (index: number): "dark" | "light" => (index % 2 === 0 ? "dark" : "light")

const numberOf = (index: number): string => String(index + 1).padStart(2, "0")
</script>

<template>
  <v-main>
    <div class="washes">
      <div
        v-for="(sample, index) in SAMPLES"
        :key="numberOf(index)"
        class="washes__slot"
      >
        <!-- Told which half to be, rather than nesting `data-theme`: both theme blocks match a
             nested island and source order picks the winner, so every example took the
             document's half. -->
        <div :class="`island island--${themeOf(index)}`">
          <band-wash
            :shape="sample.shape"
            :testid="`wash-${numberOf(index)}`"
            :tone="sample.tone"
            :width="sample.width"
          >
            <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
              Example {{ numberOf(index) }} — {{ themeOf(index) }}
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
            <p class="mt-3 font-body text-xs text-ash">
              tone <strong>{{ sample.tone }}</strong> · shape
              <strong>{{ sample.shape }}</strong> · width <strong>{{ sample.width }}</strong>
            </p>
          </band-wash>
        </div>
      </div>
    </div>
  </v-main>
</template>

<style scoped>
/* Each example is its own island, so each can be the half of the theme it is meant to show. */
.washes__slot :deep(.island) {
  min-height: 0;
}
</style>
