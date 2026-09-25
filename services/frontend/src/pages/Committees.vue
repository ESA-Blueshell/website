<script lang="ts" setup>
import {computed, ref} from "vue"
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
import {cellOf, driftItemOf, reelItemOf, useCommitteeRights, useCommittees, type Committee} from "@/domains/committees"
import ArchiveCommitteeDialog from "@/domains/committees/island/ArchiveCommitteeDialog.vue"
import {useCasualGames} from "@/domains/games"

defineOptions({name: "CommitteesPage"})

/**
 * The committees index, built like the casual one: the committees that run on the reel, the ones
 * we used to have drifting past under it, and then every listed committee. Unlisted committees
 * appear nowhere here.
 */
const router = useRouter()
const {committees, live, archived, refresh} = useCommittees()
const {games} = useCasualGames()
const {isBoard} = useCommitteeRights()

/** The games a committee organises events for, by name, drawn as its chips. */
const gameNames = (codes: string[]) => codes
  .map(code => games.value.find(game => game.code === code)?.name)
  .filter(name => name !== undefined)

const reel = computed<ReelItem[]>(() => live.value.map(committee => reelItemOf(committee, gameNames)))
const olden = computed<DriftItem[]>(() => archived.value.map(driftItemOf))
// The committees that run first, then the archived ones, each in their own order.
const every = computed<ArtCell[]>(() => [...live.value, ...archived.value].map(committee => cellOf(committee, gameNames)))

const go = (to: {href: string}) => void router.push(to.href)


const archiving = ref<Committee | null>(null)
const archiveOf = (id: string | number) => committees.value.find(committee => committee.id === id) ?? null
</script>

<template>
  <v-main>
    <island testid="committees-island">
      <header-band
        body="Committees are groups of members who organise the events and run the things the association does, and have a good time doing it. Want to join one, or start something new? Ask the board on Discord."
        eyebrow="Run by members"
        heading="Committees"
      >
        <div class="committees__actions">
          <cut-button
            away
            :href="DISCORD_INVITE"
            testid="committees-ask"
            tone="solid"
          >
            Ask on Discord
          </cut-button>
          <cut-button
            v-if="isBoard"
            href="/committees/new"
            testid="committees-add"
          >
            Add a committee
          </cut-button>
        </div>
      </header-band>

      <lead-band
        v-if="reel.length > 0"
        accent="var(--color-acid)"
        testid="committees-reel-band"
      >
        <band-head
          eyebrow="Pick a committee"
          heading="Our committees"
        />
        <template #bleed>
          <flick-reel
            class="island-dark"
            :items="reel"
            pan-back-label="Previous committee"
            pan-on-label="Next committee"
            testid-prefix="committees"
            @go="go"
          />
        </template>
      </lead-band>

      <section
        v-if="olden.length > 0"
        class="committees__olden island-dark"
        data-testid="committees-olden"
      >
        <div class="committees__olden-head">
          <band-head
            eyebrow="Archived"
            heading="The committees we used to have"
          >
            <cut-button
              away
              :href="DISCORD_INVITE"
              testid="committees-olden-ask"
            >
              Ask on Discord
            </cut-button>
          </band-head>
          <p class="committees__olden-line">
            Want to bring one back, or start something new? Ask the board on Discord. A few members with a plan is all a committee needs, and any member can run a one-off event as a member's initiative.
          </p>
        </div>
        <drift-row
          :items="olden"
          testid-prefix="committees-olden"
          @go="go"
        />
      </section>

      <lead-band
        v-if="every.length > 0"
        testid="committees-every"
      >
        <band-head
          eyebrow="All of them"
          heading="Every committee"
        />
        <art-cells
          class="committees__every"
          :cells="every"
          testid-prefix="committees-every"
          @go="go"
        >
          <template
            v-if="isBoard"
            #action="{cell}"
          >
            <button
              class="committees__archive"
              :data-testid="`committees-every-archive-${cell.id}`"
              type="button"
              @click="archiving = archiveOf(cell.id)"
            >
              {{ cell.archived ? "Bring back" : "Archive" }}
            </button>
          </template>
        </art-cells>
      </lead-band>

      <template v-if="isBoard">
        <archive-committee-dialog
          v-if="archiving"
          :committee="archiving"
          open
          @saved="refresh"
          @update:open="archiving = null"
        />
      </template>
    </island>
  </v-main>
</template>

<style scoped>
.committees__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 1.4rem;
}

.committees__olden {
  padding: 2.5rem 0 2.75rem;
  background-color: var(--color-ground);
}

.committees__olden-head {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto 1.6rem;
  padding: 0 2rem;
}

.committees__olden-line {
  max-width: 36rem;
  margin-top: 0.8rem;
  font-size: 0.98rem;
  line-height: 1.55;
  color: var(--color-ash);
}

.committees__every {
  margin-top: 1.4rem;
}

.committees__archive {
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

.committees__archive:hover {
  color: var(--color-chalk);
}

@media (max-width: 639px) {
  .committees__olden {
    padding: 1.75rem 0 2rem;
  }

  .committees__olden-head {
    padding: 0 1.25rem;
  }
}
</style>
