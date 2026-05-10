<template>
  <v-main>
    <top-banner title="Counter-Strike 2" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          With shooters' prevalence in the global esports scene, Blueshell Esports's CS2 teams are
          trying to climb up the charts with those sweet headshots! Below you can find our
          competitive CS2 team(s).
        </p>
      </div>
    </div>
    <template
      v-for="(team, i) in teams"
      :key="team.name"
    >
      <team-details
        :name-right="i % 2 != 0"
        :team="team"
      />
    </template>
  </v-main>
</template>

<script lang="ts" setup>
import TopBanner from "@/components/common/banners/TopBanner.vue"
import TeamDetails from "@/components/base/TeamDetails.vue"
import {$require} from "@/plugins/require.js"
import {ref} from "vue"
import type Team from "@/types/Team.ts"

const teams = ref<Team[]>([
  {
    name: "BS HyperS",
    bg: $require("@/assets/csgoesports2.jpg"),
    players: [
      {name: "", ign: "BSKingCookie"},
      {name: "", ign: "MacVanish_"},
      {name: "", ign: "Marijn02"},
      {name: "", ign: "JakobDutch"},
      {name: "", ign: "Veldman"},
    ],
    substitutes: [
      {name: "", ign: "Reinier"},
      {name: "", ign: "TheCheeser"},
      {name: "", ign: "FetaBass"},
      {name: "", ign: "Yannickryan"},
      {name: "", ign: "Klutch_"},
    ],
  },
  {
    name: "BS PepeDS",
    bg: $require("@/assets/csgoesports1.jpg"),
    players: [
      {name: "", ign: "TheJellyMan"},
      {name: "", ign: "Bobbuuz"},
      {name: "", ign: "ItIsIThomas"},
      {name: "", ign: "Budget_Machom36"},
      {name: "", ign: "Rijksoverheid"},
    ],
    substitutes: [
      {name: "", ign: "Lyncxe"},
      {name: "", ign: "Loafine"},
      {name: "", ign: "Tennissky"},
    ],
  },
])
</script>

<style lang="scss" scoped>

</style>
