<script lang="ts" setup>
import EsportsGamePage from "@/domains/esports/components/EsportsGamePage.vue"

defineOptions({name: "TrackmaniaPage"})
</script>

<template>
  <esports-game-page
    game="TRACKMANIA"
    title="Trackmania"
  >
    <template #intro>
      <p class="text-body-1">
        We have two players playing under the Blueshell banner for multiple Trackmania tournaments, most notably
        in the regionals of the Trackmania World Tour circuit. Aside from that we have multiple players sporting our
        club tag, competing in whatever tournament happens to be active. Be sure to say hi if you see a [ESABS] player
        driving somewhere!
      </p>
    </template>
  </esports-game-page>
</template>
