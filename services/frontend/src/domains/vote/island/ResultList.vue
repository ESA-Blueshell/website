<script lang="ts" setup>
import {ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import type {Placing} from "../adapters/vote"
import PickRow from "./PickRow.vue"

defineOptions({name: "VoteResultList"})

defineProps<{placings: Placing[]}>()

/** The one row whose player is open; a second player would talk over the first. */
const playing = ref<number | null>(null)

const toggle = (candidateId: number) => {
  playing.value = playing.value === candidateId ? null : candidateId
}

const SERVICE = {YOUTUBE: "YouTube", SPOTIFY: "Spotify"} as const
</script>

<template>
  <p
    v-if="placings.length === 0"
    class="results__empty"
    data-testid="vote-results-empty"
  >
    No ballots were cast, so there is no top this year.
  </p>
  <template v-else>
    <p
      v-if="placings.some(placing => placing.track)"
      class="results__said"
      data-testid="vote-results-players"
    >
      Nothing loads from YouTube or Spotify until you press Play on a track.
    </p>
    <ol
      class="results"
      data-testid="vote-results"
    >
      <li
        v-for="placing in placings"
        :key="placing.candidate.id"
        data-testid="vote-result"
      >
        <pick-row
          :cover="placing.candidate.cover ?? null"
          :game="placing.candidate.name"
          lazy
          :place="placing.place"
          :podium="placing.place <= 3"
          :points="placing.points"
          :track="placing.track?.title"
          :track-href="placing.track?.url"
        >
          <template #end>
            <cut-button
              v-if="placing.track"
              small
              :testid="`vote-play-${placing.candidate.id}`"
              tone="quiet"
              @click="toggle(placing.candidate.id)"
            >
              {{ playing === placing.candidate.id ? "Close" : `Play on ${SERVICE[placing.track.source]}` }}
            </cut-button>
          </template>
        </pick-row>
        <iframe
          v-if="placing.track && playing === placing.candidate.id"
          allow="encrypted-media; picture-in-picture"
          class="result__player"
          :class="{'result__player--spotify': placing.track.source === 'SPOTIFY'}"
          data-testid="vote-player"
          loading="lazy"
          :src="placing.track.embedUrl"
          :title="`${placing.track.title}, from ${placing.candidate.name}`"
        />
      </li>
    </ol>
  </template>
</template>

<style scoped>
.results__empty,
.results__said {
  padding: 0.5rem 0 1rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.results {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.result__player {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  margin-top: 0.4rem;
  border: 0;
}

.result__player--spotify {
  aspect-ratio: auto;
  height: 152px;
}

@media (max-width: 600px) {
  .results :deep(.pick-row__points) {
    display: none;
  }
}
</style>
