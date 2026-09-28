<script lang="ts" setup>
import {useRouter} from "vue-router"
import ArtCells, {type ArtCell} from "./ArtCells.vue"
import BandHead from "./BandHead.vue"
import CutButton from "./CutButton.vue"
import DriftRow, {type DriftItem} from "./DriftRow.vue"
import FlickReel, {type ReelItem} from "./FlickReel.vue"
import HeaderBand from "./HeaderBand.vue"
import Island from "./Island.vue"
import LeadBand from "./LeadBand.vue"
import {DISCORD_INVITE} from "./socialGlyphs"

/**
 * An index of one kind of record: the ones that run on the reel, the archived ones drifting past
 * under it, and then every one as a cell. The casual games and the committees are both drawn
 * with it; each page supplies its words and its records, and handles what the cells' buttons ask.
 */
defineOptions({name: "RecordIndex"})

defineProps<{
  /** The prefix of every test id on the page, `casual` or `committees`. */
  testid: string
  heading: string
  eyebrow: string
  body: string
  reel: ReelItem[]
  reelEyebrow: string
  reelHeading: string
  /** What the reel's arrows say, one record at a time: `game`, `committee`. */
  each: string
  olden: DriftItem[]
  oldenHeading: string
  oldenLine: string
  every: ArtCell[]
  everyHeading: string
  /** Whether the cells offer archiving and bringing back. */
  mayEdit: boolean
  /** Whether an archived cell also offers removal. */
  removable?: boolean
}>()

const emit = defineEmits<{
  (event: "archive", id: ArtCell["id"]): void
  (event: "remove", id: ArtCell["id"]): void
}>()

const router = useRouter()
const go = (to: {href: string}) => void router.push(to.href)
</script>

<template>
  <island :testid="`${testid}-island`">
    <header-band
      :body="body"
      :eyebrow="eyebrow"
      :heading="heading"
    >
      <template #acts>
        <slot name="acts" />
      </template>
    </header-band>

    <lead-band
      v-if="reel.length > 0"
      accent="var(--color-acid)"
      :testid="`${testid}-reel-band`"
    >
      <band-head
        :eyebrow="reelEyebrow"
        :heading="reelHeading"
      />
      <template #bleed>
        <flick-reel
          class="island-dark"
          :items="reel"
          :pan-back-label="`Previous ${each}`"
          :pan-on-label="`Next ${each}`"
          :testid-prefix="testid"
          @go="go"
        />
      </template>
    </lead-band>

    <section
      v-if="olden.length > 0"
      class="index__olden island-dark"
      :data-testid="`${testid}-olden`"
    >
      <div class="index__olden-head">
        <band-head
          eyebrow="Archived"
          :heading="oldenHeading"
        >
          <cut-button
            away
            :href="DISCORD_INVITE"
            :testid="`${testid}-olden-ask`"
          >
            Ask on Discord
          </cut-button>
        </band-head>
        <p class="index__olden-line">
          {{ oldenLine }}
        </p>
      </div>
      <drift-row
        :items="olden"
        :testid-prefix="`${testid}-olden`"
        @go="go"
      />
    </section>

    <lead-band
      v-if="every.length > 0"
      :testid="`${testid}-every`"
    >
      <band-head
        eyebrow="All of them"
        :heading="everyHeading"
      />
      <art-cells
        class="index__every"
        :cells="every"
        :testid-prefix="`${testid}-every`"
        @go="go"
      >
        <template
          v-if="mayEdit"
          #action="{cell}"
        >
          <button
            class="index__act"
            :data-testid="`${testid}-every-archive-${cell.id}`"
            type="button"
            @click="emit('archive', cell.id)"
          >
            {{ cell.archived ? "Bring back" : "Archive" }}
          </button>
          <button
            v-if="removable && cell.archived"
            class="index__act"
            :data-testid="`${testid}-every-remove-${cell.id}`"
            type="button"
            @click="emit('remove', cell.id)"
          >
            Remove
          </button>
        </template>
      </art-cells>
    </lead-band>

    <slot />
  </island>
</template>

<style scoped>

.index__olden {
  padding: 2.5rem 0 2.75rem;
  background-color: var(--color-ground);
}

.index__olden-head {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto 1.6rem;
  padding: 0 2rem;
}

.index__olden-line {
  max-width: 36rem;
  margin-top: 0.8rem;
  font-size: 0.98rem;
  line-height: 1.55;
  color: var(--color-ash);
}

.index__every {
  margin-top: 1.4rem;
}

.index__act {
  margin-top: 0.5rem;
  padding: 0.25rem 0.6rem;
  font-family: var(--font-display);
  font-size: 0.66rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-ash);
  cursor: pointer;
  background: color-mix(in oklab, var(--color-chalk) 5%, transparent);
  border: 0;
}

.index__act + .index__act {
  margin-left: 0.4rem;
}

.index__act:hover {
  color: var(--color-chalk);
}

@media (max-width: 639px) {
  .index__olden {
    padding: 1.75rem 0 2rem;
  }

  .index__olden-head {
    padding: 0 1.25rem;
  }
}
</style>
