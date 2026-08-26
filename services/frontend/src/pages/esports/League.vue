<script lang="ts" setup>
import EsportsGamePage from "@/domains/esports/components/EsportsGamePage.vue"

defineOptions({name: "LeaguePage"})
</script>

<template>
  <esports-game-page
    game="LEAGUE_OF_LEGENDS"
    title="League of Legends"
  >
    <template #intro>
      <p class="text-body-1">
        As it is around the globe, League of Legends as a competitive ground holds a special
        place in Blueshell Esports. Below you can find our competitive League of Legends
        team(s).
      </p>
    </template>
  </esports-game-page>
</template>
