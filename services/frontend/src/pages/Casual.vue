<script lang="ts" setup>
import {computed} from "vue"
import {useRouter} from "vue-router"
import ArtCells, {type ArtCell} from "@/components/island/ArtCells.vue"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import DriftRow, {type DriftItem} from "@/components/island/DriftRow.vue"
import FlickReel, {type ReelItem} from "@/components/island/FlickReel.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import Island from "@/components/island/Island.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import {cellOf, driftItemOf, reelItemOf, useCasualGames} from "@/domains/games"

defineOptions({name: "CasualPage"})

/**
 * The games index: what members play together now on the reel, the games we used to play
 * drifting past under it, and then every game, archived ones included.
 */
const router = useRouter()
const {live, archived} = useCasualGames()

const reel = computed<ReelItem[]>(() => live.value.map(reelItemOf))
const olden = computed<DriftItem[]>(() => archived.value.map(driftItemOf))
// The played games first, then the archived ones, each in their own order.
const every = computed<ArtCell[]>(() => [...live.value, ...archived.value].map(cellOf))

const go = (to: {href: string}) => void router.push(to.href)
</script>

<template>
  <v-main>
    <island testid="casual-island">
      <header-band
        body="The games members play together outside competition. Every game has its channel on the Blueshell Discord: join the server, open the channel and there is usually somebody up for a round."
        eyebrow="Casual gaming"
        heading="Casual"
      >
        <div class="casual__actions">
          <cut-button
            away
            :href="DISCORD_INVITE"
            testid="casual-join"
            tone="solid"
          >
            Join the Discord
          </cut-button>
        </div>
      </header-band>

      <lead-band
        v-if="reel.length > 0"
        accent="var(--color-acid)"
        testid="casual-reel-band"
      >
        <band-head
          eyebrow="Pick a game"
          heading="The games we play"
        />
        <template #bleed>
          <flick-reel
            class="island-dark"
            :items="reel"
            pan-back-label="Previous game"
            pan-on-label="Next game"
            testid-prefix="casual"
            @go="go"
          />
        </template>
      </lead-band>

      <section
        v-if="olden.length > 0"
        class="casual__olden island-dark"
        data-testid="casual-olden"
      >
        <div class="casual__olden-head">
          <band-head
            eyebrow="Archived"
            heading="The games we used to play"
          >
            <cut-button
              away
              :href="DISCORD_INVITE"
              testid="casual-olden-ask"
            >
              Ask on Discord
            </cut-button>
          </band-head>
          <p class="casual__olden-line">
            Want one of these back, or a game that is not here at all? Ask on Discord. If people want to play it, we bring it back or open a channel for it.
          </p>
        </div>
        <drift-row
          :items="olden"
          testid-prefix="casual-olden"
          @go="go"
        />
      </section>

      <lead-band
        v-if="every.length > 0"
        testid="casual-every"
      >
        <band-head
          eyebrow="All of them"
          heading="Every game"
        />
        <art-cells
          class="casual__every"
          :cells="every"
          testid-prefix="casual-every"
          @go="go"
        />
      </lead-band>
    </island>
  </v-main>
</template>

<style scoped>
.casual__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 1.4rem;
}

.casual__olden {
  padding: 2.5rem 0 2.75rem;
  background-color: var(--color-ground);
}

.casual__olden-head {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto 1.6rem;
  padding: 0 2rem;
}

.casual__olden-line {
  max-width: 36rem;
  margin-top: 0.8rem;
  font-size: 0.98rem;
  line-height: 1.55;
  color: var(--color-ash);
}

.casual__every {
  margin-top: 1.4rem;
}

@media (max-width: 639px) {
  .casual__olden {
    padding: 1.75rem 0 2rem;
  }

  .casual__olden-head {
    padding: 0 1.25rem;
  }
}
</style>
