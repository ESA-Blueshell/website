<script lang="ts" setup>
import {ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import {storedUrl, type Placing} from "../adapters/vote"
import {embedUrl} from "../ballot"

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
        class="result"
        :class="{'result--podium': placing.place <= 3}"
        data-testid="vote-result"
      >
        <div class="result__row">
          <span class="result__place">{{ placing.place }}</span>
          <img
            v-if="storedUrl(placing.candidate.coverPath)"
            alt=""
            class="result__cover"
            :src="storedUrl(placing.candidate.coverPath)!"
          >
          <span
            v-else
            class="result__cover result__cover--none"
          />
          <span class="result__words">
            <span class="result__game">{{ placing.candidate.name }}</span>
            <a
              v-if="placing.track"
              class="result__track"
              :href="placing.track.url"
              rel="noopener"
              target="_blank"
            >{{ placing.track.title }}</a>
          </span>
          <span class="result__points">{{ placing.points }}<span class="result__unit">pts</span></span>
          <cut-button
            v-if="placing.track"
            small
            :testid="`vote-play-${placing.candidate.id}`"
            tone="quiet"
            @click="toggle(placing.candidate.id)"
          >
            {{ playing === placing.candidate.id ? "Close" : `Play on ${SERVICE[placing.track.source]}` }}
          </cut-button>
        </div>
        <iframe
          v-if="placing.track && playing === placing.candidate.id"
          allow="encrypted-media; picture-in-picture"
          class="result__player"
          :class="{'result__player--spotify': placing.track.source === 'SPOTIFY'}"
          data-testid="vote-player"
          loading="lazy"
          :src="embedUrl(placing.track)"
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

.result__row {
  display: grid;
  grid-template-columns: 2.6rem 3rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0 1rem;
  padding: 0.6rem 1.5rem;
  clip-path: polygon(0.6rem 0, 100% 0, calc(100% - 0.6rem) 100%, 0 100%);
  background-color: var(--band-ground, var(--color-raised));
  color: var(--color-chalk);
}

.result__place {
  font-family: var(--font-display);
  font-size: 1.5rem;
  text-align: center;
  color: var(--color-ash);
}

.result--podium .result__place {
  color: var(--color-acid);
}

.result__cover {
  width: 3rem;
  aspect-ratio: 3 / 4;
  object-fit: cover;
}

.result__cover--none {
  background-color: var(--color-surface);
}

.result__words {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.result__game {
  font-weight: 700;
}

.result__track {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.result__points {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.result__unit {
  margin-left: 0.2rem;
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--color-ash);
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
  .result__row {
    grid-template-columns: 1.8rem 2.5rem minmax(0, 1fr) auto;
    padding: 0.5rem 1rem;
  }

  .result__points {
    display: none;
  }
}
</style>
