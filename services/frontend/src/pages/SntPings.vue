<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import Island from "@/components/island/Island.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import BandHead from "@/components/island/BandHead.vue"
import Leaderboard from "@/components/pinger/Leaderboard.vue"
import FastestBoard from "@/components/pinger/FastestBoard.vue"
import RecordPlate from "@/components/pinger/RecordPlate.vue"
import {
  EMPTY_LEADERBOARD,
  loadLeaderboard,
  ownStanding,
  type Leaderboard as LeaderboardSnapshot,
} from "@/domains/pinger"
import store from "@/plugins/store"

defineOptions({name: "SntPingsPage"})

const snapshot = ref<LeaderboardSnapshot>(EMPTY_LEADERBOARD)

const ranked = computed(() => [...snapshot.value.members].sort((a, b) => a.rank - b.rank))

const house = computed(() => snapshot.value.house ?? null)

const fastest = computed(() => [...snapshot.value.fastest].sort((a, b) => a.rank - b.rank))

/** The signed-in member's own row, where the board carries one for them. */
const mineId = computed<number | null>(() =>
  ownStanding(snapshot.value, store.getters.getLogin?.username)?.memberId ?? null)

/** Every ping SiteCie and the members sent over the event. */
const totalSent = computed<number>(() =>
  (house.value?.totalSent ?? 0) + ranked.value.reduce((sum, row) => sum + row.totalSent, 0))

onMounted(async () => {
  snapshot.value = await loadLeaderboard()
})
</script>

<template>
  <v-main>
    <island testid="sntpings-island">
      <header-band
        body="SNTPings is over. These are the final standings: who sent the most pings onto the canvas and who sent them the fastest."
        eyebrow="SNTPings · October 2026"
        heading="The canvas is painted,"
        heading-tail="the board is final"
      />

      <lead-band
        accent="var(--color-acid)"
        testid="snt-leaderboard"
      >
        <band-head
          :count="ranked.length"
          count-said="members on the board"
          eyebrow="Who sent the most and the fastest"
          heading="Leaderboard"
          testid="snt-leaderboard-head"
        />
        <record-plate
          class="record-wrap"
          :record="snapshot.record"
          :total-sent="totalSent"
        />
        <div class="boards">
          <section
            class="boards__one"
            data-testid="snt-board-total"
          >
            <h3 class="boards__title">
              Most sent
            </h3>
            <leaderboard
              :house="house"
              :mine-id="mineId"
              :rows="ranked"
            />
          </section>
          <section
            class="boards__one"
            data-testid="snt-board-fastest"
          >
            <h3 class="boards__title">
              Fastest
            </h3>
            <fastest-board
              :house="house"
              :mine-id="mineId"
              :rows="fastest"
            />
          </section>
        </div>
      </lead-band>
    </island>
  </v-main>
</template>

<style scoped>
/* This page runs a notch wider than the default island column: the header and the band share the
   one width so their edges still line up. Only the cap grows, so a phone is unchanged and nothing
   scrolls sideways. */
:deep(.island-header .max-w-6xl),
:deep(.lead-band__inner) {
  max-width: 82rem;
}

.record-wrap {
  margin-top: 1.75rem;
}

/* The total board and the fastest board side by side on a wide screen, stacked on a narrow one. */
.boards {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: 2rem 2.5rem;
  margin-top: 1.5rem;
}

.boards__one {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  min-width: 0;
}

.boards__title {
  font-family: var(--font-display);
  font-size: 1.15rem;
  text-transform: uppercase;
}

@media (max-width: 1199px) {
  .boards {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
