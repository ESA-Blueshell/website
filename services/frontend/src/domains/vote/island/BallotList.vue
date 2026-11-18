<script lang="ts" setup>
import IconButton from "@/components/island/IconButton.vue"
import {pointsAt, type BallotPick} from "../ballot"
import PickRow from "./PickRow.vue"

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
      data-testid="vote-pick"
    >
      <pick-row
        :cover="pick.candidate.cover ?? null"
        :game="pick.candidate.name"
        :place="index + 1"
        :points="pointsAt(points, index)"
        :track="pick.track?.title"
      >
        <template #end>
          <icon-button
            :disabled="index === 0"
            :label="`Move ${pick.candidate.name} up`"
            :testid="`vote-pick-up-${index}`"
            @click="$emit('move', index, -1)"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M12 19V5M6 11l6-6 6 6" /></svg>
          </icon-button>
          <icon-button
            :disabled="index === picks.length - 1"
            :label="`Move ${pick.candidate.name} down`"
            :testid="`vote-pick-down-${index}`"
            @click="$emit('move', index, 1)"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M12 5v14M6 13l6 6 6-6" /></svg>
          </icon-button>
          <icon-button
            danger
            :label="`Take ${pick.candidate.name} off your ballot`"
            :testid="`vote-pick-remove-${index}`"
            @click="$emit('remove', index)"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M6 6l12 12M18 6 6 18" /></svg>
          </icon-button>
        </template>
      </pick-row>
    </li>
  </ol>
</template>

<style scoped>
.ballot {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

@media (max-width: 600px) {
  /* The buttons wrap under the game, so the row and its cut grow by their height. */
  .ballot :deep(.pick-row) {
    --row-h: calc(2.5rem * 4 / 3 + 1rem + 2.25rem);
  }

  .ballot :deep(.pick-row__end) {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}
</style>
