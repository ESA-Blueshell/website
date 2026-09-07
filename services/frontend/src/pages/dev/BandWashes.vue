<script setup lang="ts">
import {ref} from "vue"
import BandWash, {
  type Fade,
  type FadeInk,
  type Half,
  type Shape,
  type Tone,
  type Veil,
  type VeilColour,
} from "@/components/island/BandWash.vue"
import MotifGround from "@/components/island/MotifGround.vue"

/**
 * The grounds a band can sit on, with a panel at the top for trying combinations by hand.
 *
 * A scratch page, not a page of the site: it is here to be looked at and argued with, and to be
 * deleted once the choices are made. Every control says what it does under its own name, since
 * the point of the page is deciding, and a slider nobody can name is a slider nobody can choose
 * from.
 *
 * The pattern is laid down once, on the page, and the bands sit over it. Nothing here restarts
 * it, so the shells run unbroken from the first band to the last however the light changes.
 */
const OFF = "none"

const TONES: Tone[] = ["plain", "brand", "green"]
const SHAPES: Shape[] = ["plain", "topleft", "pair"]
const HALVES: Half[] = ["dark", "light", "blue"]
const VEILS: Veil[] = ["none", "sheer", "soft", "firm", "solid"]
const VEIL_COLOURS: VeilColour[] = ["grey", "ink"]
const FADES: Fade[] = ["head", "foot", "both"]
const FADE_INKS: FadeInk[] = ["dark", "light"]
const PAGE_HALVES = ["dark", "light"] as const

/** Where the veil is to be the half's own, rather than one of the two colours of its own. */
const ITS_GROUND = "half's ground"
/** Where the veil's share is set by hand rather than taken from one of the named steps. */
const BY_HAND = "by hand"

const chosen = ref({
  half: "dark" as Half,
  shape: "pair" as Shape,
  tone: "brand" as Tone,
  toneAlt: "green" as Tone,
  veil: BY_HAND as Veil | typeof BY_HAND,
  veilColour: ITS_GROUND as VeilColour | typeof ITS_GROUND,
  fade: OFF as Fade | typeof OFF,
  fadeInk: "dark" as FadeInk,
})

/** What each control does, in the words a person choosing between them would want. */
const HELP: Record<string, string> = {
  page: "Which half the page is in. This is what sets the ink under every band; a band's own ground is separate.",
  half: "The band's ground: near-white, near-black, or the association's blue. The ink does not follow it.",
  shape: "Where the band's colour comes in. Top left is where reading starts; pair takes both far corners; plain has no colour at all.",
  tone: "The colour of the wash, and of the top-left corner where the shape is pair.",
  toneAlt: "The bottom-right corner's colour. Only pair has a second corner, so it does nothing on the others.",
  veil: "How much of its ground the band lays over the pattern, as one of the named steps. None leaves the pattern bare.",
  veilColour: "What that ground is made of, where the band's own is not wanted: a light grey or an ink, to stand against its neighbours.",
  fade: "Which edge of the band fades out, for blending it into the band beyond it. Head is the top, foot the bottom.",
  fadeInk: "What that fade is made of: it darkens toward the edge, or lightens toward it.",
}

const veilShare = ref<number>(66)
const washReach = ref<number>(58)
const washStrength = ref<number>(30)
const fadeReach = ref<number>(30)
const fadeStrength = ref<number>(70)
const patternOn = ref<boolean>(true)
const patternStrength = ref<number>(16)
const hazeOn = ref<boolean>(true)
const haze = ref<number>(24)
const pageHalf = ref<(typeof PAGE_HALVES)[number]>("dark")

interface Sample {
  tone: Tone
  shape: Shape
  toneAlt?: Tone
  half: Half
}

const FIXED: Sample[] = (["dark", "light", "blue"] as Half[]).flatMap(half => [
  {shape: "topleft", tone: "brand", half},
  {shape: "pair", tone: "brand", toneAlt: "green", half},
  {shape: "plain", tone: "plain", half},
])

const numberOf = (index: number): string => String(index + 1).padStart(2, "0")
</script>

<template>
  <v-main>
    <!-- One island, one pattern, laid down here rather than on each band. -->
    <div
      :class="`island island--${pageHalf} motif washes`"
      :style="{
        '--motif-colour': `rgb(126 138 152 / ${patternStrength}%)`,
        '--motif-haze': `${haze}%`,
      }"
    >
      <motif-ground
        :haze="hazeOn"
        :pattern="patternOn"
      />

      <div :class="`panel island--${pageHalf}`">
        <div class="panel__row">
          <label class="panel__field font-body">
            <span class="panel__label">page</span>
            <select
              v-model="pageHalf"
              class="panel__input"
              data-testid="panel-page"
            >
              <option
                v-for="option in PAGE_HALVES"
                :key="option"
                :value="option"
              >{{ option }}</option>
            </select>
            <span class="panel__help">{{ HELP.page }}</span>
          </label>

          <label
            v-for="field in [
              {name: 'half', options: HALVES},
              {name: 'shape', options: SHAPES},
              {name: 'tone', options: TONES},
              {name: 'toneAlt', options: TONES},
              {name: 'veil', options: [BY_HAND, ...VEILS]},
              {name: 'veilColour', options: [ITS_GROUND, ...VEIL_COLOURS]},
              {name: 'fade', options: [OFF, ...FADES]},
              {name: 'fadeInk', options: FADE_INKS},
            ]"
            :key="field.name"
            class="panel__field font-body"
          >
            <span class="panel__label">{{ field.name }}</span>
            <select
              v-model="chosen[field.name as 'tone']"
              class="panel__input"
              :data-testid="`panel-${field.name}`"
              :disabled="(field.name === 'toneAlt' && chosen.shape !== 'pair')
                || (field.name === 'fadeInk' && chosen.fade === OFF)"
            >
              <option
                v-for="option in field.options"
                :key="option"
                :value="option"
              >{{ option }}</option>
            </select>
            <span class="panel__help">{{ HELP[field.name] }}</span>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">veil share {{ chosen.veil === BY_HAND ? `${veilShare}%` : chosen.veil }}</span>
            <input
              v-model.number="veilShare"
              class="panel__input"
              data-testid="panel-veil-share"
              :disabled="chosen.veil !== BY_HAND"
              max="100"
              min="0"
              type="range"
            >
            <span class="panel__help">
              How much of the ground is laid down, where the veil is set by hand rather than to
              a named step. At nothing the pattern is bare; at everything it is hidden.
            </span>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">colour reach {{ washReach }}rem</span>
            <input
              v-model.number="washReach"
              class="panel__input"
              data-testid="panel-wash-reach"
              :disabled="chosen.shape === 'plain'"
              max="120"
              min="8"
              type="range"
            >
            <span class="panel__help">
              How far the corner colours carry into the band. One figure for both corners, so
              the top left and the bottom right are always of a strength.
            </span>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">colour strength {{ washStrength }}%</span>
            <input
              v-model.number="washStrength"
              class="panel__input"
              data-testid="panel-wash-strength"
              :disabled="chosen.shape === 'plain'"
              max="100"
              min="0"
              type="range"
            >
            <span class="panel__help">
              How much of the tone goes into the colour at its corner. Both corners take the
              same share, whichever two colours they are.
            </span>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">fade reach {{ fadeReach }}%</span>
            <input
              v-model.number="fadeReach"
              class="panel__input"
              data-testid="panel-fade-reach"
              :disabled="chosen.fade === OFF"
              max="100"
              min="0"
              type="range"
            >
            <span class="panel__help">
              How far up or down the band the fade carries, as a share of the band's own height.
            </span>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">fade strength {{ fadeStrength }}%</span>
            <input
              v-model.number="fadeStrength"
              class="panel__input"
              data-testid="panel-fade-strength"
              :disabled="chosen.fade === OFF"
              max="100"
              min="0"
              type="range"
            >
            <span class="panel__help">
              How solid the fade is at the very edge. At everything the band's edge is that
              colour outright; short of it the band shows through.
            </span>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">pattern {{ patternOn ? `${patternStrength}%` : "off" }}</span>
            <span class="panel__pair">
              <input
                v-model="patternOn"
                data-testid="panel-pattern-on"
                type="checkbox"
              >
              <input
                v-model.number="patternStrength"
                class="panel__input"
                data-testid="panel-pattern"
                :disabled="!patternOn"
                max="40"
                min="0"
                type="range"
              >
            </span>
            <span class="panel__help">
              Whether the repeating shells are drawn behind everything, and how strongly. It is
              the page's, not a band's: it runs unbroken under all of them.
            </span>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">haze {{ hazeOn ? `${haze}%` : "off" }}</span>
            <span class="panel__pair">
              <input
                v-model="hazeOn"
                data-testid="panel-haze-on"
                type="checkbox"
              >
              <input
                v-model.number="haze"
                class="panel__input"
                data-testid="panel-haze"
                :disabled="!hazeOn"
                max="70"
                min="0"
                type="range"
              >
            </span>
            <span class="panel__help">
              Soft grey light thrown across the whole page under everything else, in four blobs
              far larger than any band. It reads as the light changing down the page.
            </span>
          </label>
        </div>
      </div>

      <band-wash
        :fade="chosen.fade === OFF ? undefined : chosen.fade"
        :fade-ink="chosen.fade === OFF ? undefined : chosen.fadeInk"
        :half="chosen.half"
        :shape="chosen.shape"
        :style="{
          '--wash-reach': `${washReach}rem`,
          '--wash-strength': `${washStrength}%`,
          '--fade-reach': `${fadeReach}%`,
          '--fade-strength': `${fadeStrength}%`,
          ...(chosen.veil === BY_HAND ? {'--band-veil': `${veilShare}%`} : {}),
        }"
        testid="wash-chosen"
        :tone="chosen.tone"
        :tone-alt="chosen.toneAlt"
        :veil="chosen.veil === BY_HAND ? undefined : chosen.veil"
        :veil-colour="chosen.veilColour === ITS_GROUND ? undefined : chosen.veilColour"
      >
        <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
          Yours — {{ chosen.half }} · {{ chosen.shape }} · {{ chosen.tone
          }}<template v-if="chosen.shape === 'pair'">
            to {{ chosen.toneAlt }}
          </template> ·
          veil {{ chosen.veil === BY_HAND ? `${veilShare}%` : chosen.veil }}
          {{ chosen.veilColour }} · fade {{ chosen.fade }}
        </p>
        <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-3xl">
          Your logo on our posters
        </h2>
        <p class="mt-3 max-w-2xl font-body text-sm leading-relaxed text-ash sm:text-base">
          Blueshell is the gaming association of the University of Twente: a few hundred people
          from every study, playing together online and in person since 2017.
        </p>
      </band-wash>

      <band-wash
        v-for="(sample, index) in FIXED"
        :key="numberOf(index)"
        :half="sample.half"
        :shape="sample.shape"
        :testid="`wash-${numberOf(index)}`"
        :tone="sample.tone"
        :tone-alt="sample.toneAlt"
      >
        <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
          {{ numberOf(index) }} — {{ sample.half }} · {{ sample.shape }} · {{ sample.tone
          }}<template v-if="sample.toneAlt">
            to {{ sample.toneAlt }}
          </template>
        </p>
        <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-3xl">
          Your logo on our posters
        </h2>
        <p class="mt-3 max-w-2xl font-body text-sm leading-relaxed text-ash sm:text-base">
          Blueshell is the gaming association of the University of Twente: a few hundred people
          from every study, playing together online and in person since 2017. This is the length
          a band's words usually run to, so the wash is judged against the amount of text it
          will actually carry.
        </p>
      </band-wash>
    </div>
  </v-main>
</template>

<style scoped>
/* The pattern is drawn behind the bands, so the island itself draws no tile of its own. */
.washes {
  background-image: none;
}

.panel {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--color-ground);
  border-bottom: 1px solid var(--color-hairline);
}

.panel__row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 1rem 1.4rem;
  align-items: start;
  max-width: 84rem;
  margin-inline: auto;
  padding: 1rem 1.25rem;
}

.panel__field {
  display: grid;
  gap: 0.25rem;
  align-content: start;
}

.panel__pair {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.panel__label {
  font-size: 10px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.panel__input {
  width: 100%;
  padding: 0.25rem 0.4rem;
  font: inherit;
  font-size: 0.85rem;
  color: var(--color-chalk);
  background: var(--color-surface);
  border: 1px solid var(--color-hairline);
  border-radius: 0.35rem;
}

.panel__help {
  font-size: 0.72rem;
  line-height: 1.35;
  color: var(--color-ash);
}
</style>
