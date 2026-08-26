<script lang="ts" setup>
import EsportsGamePage from "@/domains/esports/components/EsportsGamePage.vue"

defineOptions({name: "GeoguessrPage"})
</script>

<template>
  <esports-game-page
    game="GEOGUESSR"
    title="Geoguessr"
  >
    <template #intro>
      <p class="text-body-1">
        Geoguessr is one of Blueshell Esports’ newest competitive titles. Our players test their
        geographical knowledge and quick decision-making in community events and tournaments, proudly
        representing Blueshell esports.
      </p>
    </template>
  </esports-game-page>
</template>
