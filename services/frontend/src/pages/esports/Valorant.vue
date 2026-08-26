<script lang="ts" setup>
import EsportsGamePage from "@/domains/esports/components/EsportsGamePage.vue"

defineOptions({name: "ValorantPage"})
</script>

<template>
  <esports-game-page
    game="VALORANT"
    title="Valorant"
  >
    <template #intro>
      <p class="text-body-1">
        With shooters’ prevalence in the global esports scene, not only do we have a CS team, but also multiple
        Valorant teams. They'll be battling it out within the dutch Valorant esports scene.
        Below you can find our competitive Valorant teams.
      </p>
    </template>
  </esports-game-page>
</template>
