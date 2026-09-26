<script lang="ts" setup>
import type {TeamRoster} from "../adapters/esports"

/**
 * The teams a game fielded in a season as its open slice reveals them: the season, then each team
 * by name with how many are on its roster. Drawn by the esports index and the home page alike.
 */
defineOptions({name: "SeasonTeams"})

defineProps<{season: string; teams: TeamRoster[]}>()
</script>

<template>
  <span class="slice__group">
    <span class="slice__group-label">{{ season }}</span>
    <span class="slice__entries">
      <span
        v-for="team in teams"
        :key="team.id"
        class="slice__entry"
      >
        <span class="slice__entry-handle">{{ team.name }}</span>
        <span class="slice__entry-name">{{ team.members.length }} on the roster</span>
      </span>
    </span>
  </span>
</template>

<style scoped src="./sliceEntries.css"></style>
