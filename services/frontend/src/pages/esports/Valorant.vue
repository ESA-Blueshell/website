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
import TopBanner from "@/components/common/banners/TopBanner.vue"
import TeamDetails from "@/components/base/TeamDetails.vue"
import {$require} from "@/plugins/require.js"
import {ref} from "vue"
import type Team from "@/types/Team.ts"

const teams = ref<Team[]>([
  {
    name: "BS Waterboarders",
    bg: $require("@/assets/valorantesports1.jpg"),
    players: [
      {name: "", ign: "AriosFury"},
      {name: "", ign: "BS Syched"},
      {name: "", ign: "Loafine"},
      {name: "", ign: "The Toilet Mage"},
      {name: "", ign: "lyx"},
    ],
    substitutes: [
      {name: "", ign: "BangForYourBucky"},
      {name: "", ign: "Blackout"},
      {name: "", ign: "BS King Cookie"},
      {name: "", ign: "DJundertaker25"},
    ],
  },
  {
    name: "BS SpicyWater",
    bg: $require("@/assets/valorantesports2.jpg"),
    players: [
      {name: "", ign: "Sony"},
      {name: "", ign: "JoshSyx"},
      {name: "", ign: "Stefo"},
      {name: "", ign: "drSL"},
      {name: "", ign: "ItIsIThomas"},
    ],
    substitutes: [
      {name: "", ign: "Machom36"},
      {name: "", ign: "MrPancake"},
      {name: "", ign: "udeyy"},
      {name: "", ign: "Tennissky"},
    ],
  },
  {
    name: "BS G.G.C. Yaptown",
    bg: $require("@/assets/valorantesports1.jpg"),
    players: [
      {name: "", ign: "Puffy71"},
      {name: "", ign: "EternalScreams"},
      {name: "", ign: "SylWorld"},
      {name: "", ign: "Yank"},
      {name: "", ign: "cawa"},
    ],
    substitutes: [
      {name: "", ign: "kimic"},
      {name: "", ign: "Lyncxe"},
      {name: "", ign: "ronicats"},
    ],
    coaches: [
      {name: "", ign: "BSP Reign"},
    ],
  },
])
</script>
