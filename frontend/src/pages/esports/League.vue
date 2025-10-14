<template>
  <v-main>
    <top-banner title="League of Legends" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          As it is around the globe, League of Legends as a competitive ground holds a special
          place in Blueshell Esports. Below you can find our competitive League of Legends
          team(s).
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
    name: "BS Roestige Ridders",
    bg: $require("@/assets/leagueesportsbg1.jpg"),
    players: [
      {name: "Max Jansdam", ign: "DwalendeDrol"},
      {name: "", ign: "fdghjkghsdfg"},
      {name: "", ign: "Sword Dance"},
      {name: "Arend Heijn", ign: "banaan9999"},
      {name: "Marcus Caeyers", ign: "craezed"},
    ],
  },
  {
    name: "BS Pandora",
    bg: $require("@/assets/leagueesportsbg2.jpg"),
    players: [
      {name: "Nicolás Pita", ign: "Rule64FatWillump"},
      {name: "Taha Aydin", ign: "Talpa"},
      {name: "Alen Mozes", ign: "Zalie"},
      {name: "Liam Beld", ign: "lyx"},
      {name: "Emma Dokter", ign: "Gibblegab"},
    ],
    substitutes: [
      {name: "Mark Otter", ign: "markdeotter"},
      {name: "joran Riemer", ign: "Hoodyboy"},
      {name: "Razvan Morariu", ign: "Mora"},
    ],
  },
])
</script>

<style lang="scss" scoped>
.v-parallax__content {
  padding-left: 0 !important;
  padding-right: 0 !important;
}
</style>
