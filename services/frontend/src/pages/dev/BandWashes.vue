<script setup lang="ts">
import {ref} from "vue"
import BandWash, {type Half, type Shape, type Tone, type Veil} from "@/components/island/BandWash.vue"

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

/** What the panel offers for the veil: the four steps, and letting the half decide. */
const ITS_HALF = "half's own"
type Chosen = Omit<Sample, "veil"> & {toneAlt: Tone; veil: Veil | typeof ITS_HALF}

const TONES: Tone[] = ["plain", "brand", "green"]
const SHAPES: Shape[] = ["plain", "topleft", "pair"]
const HALVES: Half[] = ["dark", "light"]
const VEILS: Veil[] = ["sheer", "soft", "firm", "solid"]

/** What the panel is set to. Its band is drawn first, so a change is seen without scrolling. */
const chosen = ref<Chosen>({
  tone: "brand",
  toneAlt: "green",
  shape: "pair",
  half: "dark",
  veil: ITS_HALF,
})

/** How strongly the pattern is drawn, which belongs to the page rather than to a band. */
const patternStrength = ref<number>(16)

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
      class="island island--dark motif washes"
      :style="{'--motif-colour': `rgb(126 138 152 / ${patternStrength}%)`}"
    >
      <div class="panel island--dark">
        <div class="panel__row">
          <label
            v-for="field in [
              {name: 'half', options: HALVES},
              {name: 'shape', options: SHAPES},
              {name: 'tone', options: TONES},
              {name: 'toneAlt', options: TONES},
              {name: 'veil', options: [ITS_HALF, ...VEILS]},
            ]"
            :key="field.name"
            class="panel__field font-body"
          >
            <span class="panel__label">{{ field.name }}</span>
            <select
              v-model="chosen[field.name as 'tone']"
              class="panel__input"
              :data-testid="`panel-${field.name}`"
            >
              <option
                v-for="option in field.options"
                :key="option"
                :value="option"
              >{{ option }}</option>
            </select>
          </label>

          <label class="panel__field font-body">
            <span class="panel__label">pattern {{ patternStrength }}%</span>
            <input
              v-model.number="patternStrength"
              class="panel__input"
              data-testid="panel-pattern"
              max="40"
              min="0"
              type="range"
            >
          </label>
        </div>
      </div>

      <band-wash
        :half="chosen.half"
        :shape="chosen.shape"
        testid="wash-chosen"
        :tone="chosen.tone"
        :tone-alt="chosen.toneAlt"
        :veil="chosen.veil === ITS_HALF ? undefined : chosen.veil"
      >
        <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
          Yours — {{ chosen.half }} · {{ chosen.shape }} · {{ chosen.tone }} to
          {{ chosen.toneAlt }} · {{ chosen.veil }}
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
