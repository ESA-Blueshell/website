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
import TopBanner from "@/components/common/banners/TopBanner.vue"
import TeamDetails from "@/components/base/TeamDetails.vue"
import type Team from "@/types/Team.ts"

import {$require} from "@/plugins/require.js"
import {ref} from "vue"

const teams = ref<Team[]>([
  {
    name: "BS Squirtles",
    bg: $require("@/assets/rocketleagueesports.jpg"),
    players: [
      {name: "", ign: "ItIsIThomas"},
      {name: "", ign: "bluegy"},
      {name: "", ign: "boscvruchten thee"},
    ],
    substitutes: [
      {name: "", ign: "Mr. Pancake^-^"},
    ],
  },
  {
    name: "BS Turtles",
    bg: $require("@/assets/rocketleagueesports2.jpg"),
    players: [
      {name: "", ign: "robertP19"},
      {name: "", ign: "Diagonize"},
      {name: "", ign: "Bak en Braad"},
    ],
  },
])
</script>
