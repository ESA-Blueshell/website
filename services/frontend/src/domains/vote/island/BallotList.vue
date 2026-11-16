<script lang="ts" setup>
import IconButton from "@/components/island/IconButton.vue"
import {storedUrl} from "../adapters/vote"
import {pointsAt, type BallotPick} from "../ballot"

defineOptions({name: "VoteBallotList"})

defineProps<{
  picks: BallotPick[]
  /** What each position is worth, first to last. */
  points: number[]
}>()

defineEmits<{
  move: [index: number, step: -1 | 1]
  remove: [index: number]
}>()
</script>

<template>
  <ol
    class="ballot"
    data-testid="vote-ballot"
  >
    <li
      v-for="(pick, index) in picks"
      :key="pick.candidate.id"
      class="pick"
      data-testid="vote-pick"
    >
      <span class="pick__rank">{{ index + 1 }}</span>
      <img
        v-if="storedUrl(pick.candidate.coverPath)"
        alt=""
        class="pick__cover"
        :src="storedUrl(pick.candidate.coverPath)!"
      >
      <span
        v-else
        class="pick__cover pick__cover--none"
      />
      <span class="pick__words">
        <span class="pick__game">{{ pick.candidate.name }}</span>
        <span
          v-if="pick.track"
          class="pick__track"
        >{{ pick.track.title }}</span>
      </span>
      <span class="pick__points">{{ pointsAt(points, index) }}<span class="pick__unit">pts</span></span>
      <span class="pick__acts">
        <icon-button
          :disabled="index === 0"
          :label="`Move ${pick.candidate.name} up`"
          :testid="`vote-pick-up-${index}`"
          @click="$emit('move', index, -1)"
        >↑</icon-button>
        <icon-button
          :disabled="index === picks.length - 1"
          :label="`Move ${pick.candidate.name} down`"
          :testid="`vote-pick-down-${index}`"
          @click="$emit('move', index, 1)"
        >↓</icon-button>
        <icon-button
          danger
          :label="`Take ${pick.candidate.name} off your ballot`"
          :testid="`vote-pick-remove-${index}`"
          @click="$emit('remove', index)"
        >✕</icon-button>
      </span>
    </li>
  </ol>
</template>

<style scoped>
.ballot {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.pick {
  display: grid;
  grid-template-columns: 2rem 2.75rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0 0.9rem;
  padding: 0.55rem 1.5rem;
  clip-path: polygon(0.6rem 0, 100% 0, calc(100% - 0.6rem) 100%, 0 100%);
  background-color: var(--band-ground, var(--color-raised));
  color: var(--color-chalk);
}

.pick__rank {
  font-family: var(--font-display);
  font-size: 1.4rem;
  text-align: center;
  color: var(--color-ash);
}

.pick__cover {
  width: 2.75rem;
  aspect-ratio: 3 / 4;
  object-fit: cover;
}

.pick__cover--none {
  background-color: var(--color-surface);
}

.pick__words {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.pick__game {
  font-weight: 700;
}

.pick__track {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.pick__points {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.pick__unit {
  margin-left: 0.2rem;
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--color-ash);
}

.pick__acts {
  display: flex;
  gap: 0.25rem;
}

@media (max-width: 600px) {
  .pick {
    grid-template-columns: 1.5rem 2.25rem minmax(0, 1fr) auto;
    padding: 0.5rem 1rem;
  }

  .pick__acts {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}
</style>
