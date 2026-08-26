<script lang="ts" setup>
import EsportsGamePage from "@/domains/esports/components/EsportsGamePage.vue"

defineOptions({name: "RocketLeaguePage"})
</script>

<template>
  <esports-game-page
    game="ROCKET_LEAGUE"
    title="Rocket League"
  >
    <template #intro>
      <p class="text-body-1">
        This year we have another new game Blueshell will compete in. A new rocket league team has been created! They
        will be joining tournaments representing Blueshell and compete in DSL.
      </p>
    </template>
  </esports-game-page>
</template>
