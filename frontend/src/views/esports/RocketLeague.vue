<template>
  <v-main>
    <top-banner title="Rocket League" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          This year we have another new game Blueshell will compete in. A new rocket league team has been created! They
          will be joining tournaments representing Blueshell and compete in DSL.
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
import TopBanner from "../../components/banners/TopBanner.vue"
import TeamDetails from "@/components/TeamDetails.vue"
import type Team from "@/types/Team.ts"

import {$require} from "@/plugins/require.js"
import {ref} from "vue"

const teams = ref<Team[]>([
  {
    name: "BS Squirtles",
    bg: $require("@/assets/rocketleagueesports.jpg"),
    players: [
      {name: "Thomas Dekker", ign: "ItIsIThomas"},
      {name: "Joy Verheijen", ign: "bluegy"},
      {name: "Huub Hammink", ign: "boscvruchten thee"},
    ],
    substitutes: [
      {name: "René Hammink", ign: "Mr. Pancake^-^"},
    ],
  },
  {
    name: "BS Turtles",
    bg: $require("@/assets/rocketleagueesports2.jpg"),
    players: [
      {name: "Robert Pantilie", ign: "robertP19"},
      {name: "Finn Prinsenberg", ign: "Diagonize"},
      {name: "Julian van den Nieuwenhuizen", ign: "Bak en Braad"},
    ],
  },
])
</script>
