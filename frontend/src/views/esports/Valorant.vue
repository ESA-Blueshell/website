<template>
  <v-main>
    <top-banner title="Valorant" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          With shooters’ prevalence in the global esports scene, not only do we have a CS team, but also multiple
          Valorant teams. They'll be battling it out within the dutch Valorant esports scene.
          Below you can find our competitive Valorant teams.
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
import TopBanner from "@/components/banners/TopBanner.vue"
import TeamDetails from "@/components/TeamDetails.vue"
import {$require} from "@/plugins/require.js"
import {ref} from "vue"
import type Team from "@/types/Team.ts"

const teams = ref<Team[]>([
  {
    name: "BS Waterboarders",
    bg: $require("@/assets/valorantesports1.jpg"),
    players: [
      {name: "Viktor Petrov", ign: "AriosFury"},
      {name: "Raghavendran Srinivasan", ign: "BS Syched"},
      {name: "Reini Strating", ign: "Loafine"},
      {name: "Benyamin Unsurorlu", ign: "The Toilet Mage"},
      {name: "Liam Beld", ign: "lyx"},
    ],
    substitutes: [
      {name: "Harm Dreteler", ign: "BangForYourBucky"},
      {name: "Mauro Foddis", ign: "Blackout"},
      {name: "Ivo Heitlager", ign: "BS King Cookie"},
      {name: "Joris Jonkers", ign: "DJundertaker25"},
    ],
  },
  {
    name: "BS SpicyWater",
    bg: $require("@/assets/valorantesports2.jpg"),
    players: [
      {name: "Shay Karim", ign: "Sony"},
      {name: "Yoshua Ligt", ign: "JoshSyx"},
      {name: "Stefan Borachev", ign: "Stefo"},
      {name: "Thorranin Vudhidhanaseth", ign: "drSL"},
      {name: "Thomas Dekker", ign: "ItIsIThomas"},
    ],
    substitutes: [
      {name: "Jesse van Gameren", ign: "Machom36"},
      {name: "René Hammink", ign: "MrPancake"},
      {name: "Michal Rokita", ign: "udeyy"},
      {name: "Mitchell van Poecke", ign: "Tennissky"},
    ],
  },
  {
    name: "BS G.G.C. Yaptown",
    bg: $require("@/assets/valorantesports1.jpg"),
    players: [
      {name: "Dóra Csiszár", ign: "Puffy71"},
      {name: "Sanne van Kooten", ign: "EternalScreams"},
      {name: "Sylwia Siekman", ign: "SylWorld"},
      {name: "Yannick Sloot", ign: "Yank"},
      {name: "Sacha Brienesse", ign: "cawa"},
    ],
    substitutes: [
      {name: "Kim Wolf", ign: "kimic"},
      {name: "Rosalin de Vos", ign: "Lyncxe"},
      {name: "Veronika Sebaha", ign: "ronicats"},
    ],
    coaches: [
      {name: "Reinier Algra", ign: "BSP Reign"},
    ],
  },
])
</script>

<style lang="scss" scoped>

</style>
