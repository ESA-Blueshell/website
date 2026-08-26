<script lang="ts" setup>
import EsportsGamePage from "@/domains/esports/components/EsportsGamePage.vue"

defineOptions({name: "Cs2Page"})
</script>

<template>
  <esports-game-page
    game="CS2"
    title="Counter-Strike 2"
  >
    <template #intro>
      <p class="text-body-1">
        With shooters' prevalence in the global esports scene, Blueshell Esports's CS2 teams are
        trying to climb up the charts with those sweet headshots! Below you can find our
        competitive CS2 team(s).
      </p>
    </template>
  </esports-game-page>
</template>
