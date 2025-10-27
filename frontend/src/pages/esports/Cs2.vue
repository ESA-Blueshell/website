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
    name: "BS PepeDS",
    bg: $require("@/assets/csgoesports1.jpg"),
    players: [
      {name: "Jelle", ign: "TheJellyMan"},
      {name: "Bob", ign: "Bobbuuz"},
      {name: "Thomas Dekker", ign: "ItIsIThomas"},
      {name: "Jesse van Gameren", ign: "Budget_Machom36"},
      {name: "Taha Aydin", ign: "Rijksoverheid"},
    ],
    substitutes: [
      {name: "Rosalin de Vos", ign: "Lyncxe"},
      {name: "Reini Strating", ign: "Loafine"},
      {name: "Mitchell van Poecke", ign: "Tennissky"},
    ],
  },
  {
    name: "BS HyperS",
    bg: $require("@/assets/csgoesports2.jpg"),
    players: [
      {name: "Ivo Heitlager", ign: "BSKingCookie"},
      {name: "Joran Hagen", ign: "MacVanish_"},
      {name: "Marijn de Boer", ign: "Marijn02"},
      {name: "Boris Kusters", ign: "JakobDutch"},
      {name: "Max Veldman", ign: "Veldman"},
    ],
    substitutes: [
      {name: "Reinier Algra", ign: "Reinier"},
      {name: "Mario van der Wal Agraz", ign: "TheCheeser"},
      {name: "Chris Wong", ign: "FetaBass"},
      {name: "Yannick Sloot", ign: "Yannickryan"},
      {name: "Hielke Kleijsen", ign: "Klutch_"},
    ],
  },
])
</script>

<style lang="scss" scoped>

</style>
