<script setup lang="ts">
import {ref} from "vue"
import BandWash, {
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
 * deleted once the choices are made.
 *
 * The pattern is laid down once, on the page, and the bands sit over it — which is the point of
 * the page as much as the washes are. Nothing here restarts it, so the shells run unbroken from
 * the first band to the last however the light changes over them.
 */
interface Sample {
  tone: Tone
  shape: Shape
  toneAlt?: Tone
  half: Half
  veil?: Veil
}

/** What the panel offers for the veil: the four named steps, or a share set by hand. */
const ITS_HALF = "by hand"
type Chosen = Omit<Sample, "veil"> & {
  toneAlt: Tone
  veil: Veil | typeof ITS_HALF
  veilColour: VeilColour | typeof ITS_GROUND
}

const TONES: Tone[] = ["plain", "brand", "green"]
const SHAPES: Shape[] = ["plain", "topleft", "pair"]
const HALVES: Half[] = ["dark", "light", "blue"]

/** Which half the page itself is in, which is what decides the ink under every band. */
const PAGE_HALVES = ["dark", "light"] as const
const VEILS: Veil[] = ["none", "sheer", "soft", "firm", "solid"]
const VEIL_COLOURS: VeilColour[] = ["grey", "ink"]
/** What the panel offers where the veil is to be the half's own ground. */
const ITS_GROUND = "half's ground"

/** What the panel is set to. Its band is drawn first, so a change is seen without scrolling. */
const chosen = ref<Chosen>({
  tone: "brand",
  toneAlt: "green",
  shape: "pair",
  half: "dark",
  veil: ITS_HALF,
  veilColour: ITS_GROUND,
})

/** Only `pair` has a far corner to colour, so the second tone is inert on the other shapes. */

/** Whether the pattern is drawn at all, and how strongly it is drawn when it is. */
const patternOn = ref<boolean>(true)
const patternStrength = ref<number>(16)

/** How much ground the band lays down, as a share, when the panel is set to a figure of its own. */
const veilShare = ref<number>(66)

/** Whether the haze is drawn at all, and how much of it there is when it is. */
const hazeOn = ref<boolean>(true)
const haze = ref<number>(24)

/** Which half the page is in. The bands' grounds are theirs; the ink is the page's. */
const pageHalf = ref<(typeof PAGE_HALVES)[number]>("dark")


const FIXED: Sample[] = HALVES.flatMap(half => [
  {shape: "topleft", tone: "brand", half},
  {shape: "pair", tone: "brand", toneAlt: "green", half},
  {shape: "pair", tone: "green", toneAlt: "brand", half},
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
          </label>

          <label
            v-for="field in [
              {name: 'half', options: HALVES},
              {name: 'shape', options: SHAPES},
              {name: 'tone', options: TONES},
              {name: 'toneAlt', options: TONES},
              {name: 'veil', options: [ITS_HALF, ...VEILS]},
              {name: 'veilColour', options: [ITS_GROUND, ...VEIL_COLOURS]},
            ]"
            :key="field.name"
            class="panel__field font-body"
          >
            <span class="panel__label">{{ field.name
            }}<template v-if="field.name === 'toneAlt' && chosen.shape !== 'pair'"> · pair only</template></span>
            <select
              v-model="chosen[field.name as 'tone']"
              class="panel__input"
              :data-testid="`panel-${field.name}`"
              :disabled="field.name === 'toneAlt' && chosen.shape !== 'pair'"
            >
              <option
                v-for="option in field.options"
                :key="option"
                :value="option"
              >{{ option }}</option>
            </select>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">veil {{ chosen.veil === ITS_HALF ? `${veilShare}%` : chosen.veil }}</span>
            <input
              v-model.number="veilShare"
              class="panel__input"
              data-testid="panel-veil-share"
              :disabled="chosen.veil !== ITS_HALF"
              max="100"
              min="0"
              type="range"
            >
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
          </label>
        </div>
      </div>

      <band-wash
        :half="chosen.half"
        :shape="chosen.shape"
        :style="chosen.veil === ITS_HALF ? {'--band-veil': `${veilShare}%`} : undefined"
        testid="wash-chosen"
        :tone="chosen.tone"
        :tone-alt="chosen.toneAlt"
        :veil="chosen.veil === ITS_HALF ? undefined : chosen.veil"
        :veil-colour="chosen.veilColour === ITS_GROUND ? undefined : chosen.veilColour"
      >
        <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
          Yours — {{ chosen.half }} · {{ chosen.shape }} · {{ chosen.tone
          }}<template v-if="chosen.shape === 'pair'">
            to {{ chosen.toneAlt }}
          </template> ·
          veil {{ chosen.veil === ITS_HALF ? `${veilShare}%` : chosen.veil }}
          {{ chosen.veilColour }}
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
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem 1.4rem;
  align-items: end;
  max-width: 72rem;
  margin-inline: auto;
  padding: 0.9rem 1.25rem;
}

.panel__field {
  display: grid;
  gap: 0.2rem;
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
  min-width: 8rem;
  padding: 0.25rem 0.4rem;
  font: inherit;
  font-size: 0.85rem;
  color: var(--color-chalk);
  background: var(--color-surface);
  border: 1px solid var(--color-hairline);
  border-radius: 0.35rem;
}
</style>
