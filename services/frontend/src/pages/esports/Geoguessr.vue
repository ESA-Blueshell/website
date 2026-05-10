<template>
  <v-main>
    <top-banner title="Geoguessr" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          Geoguessr is one of Blueshell Esports’ newest competitive titles. Our players test their
          geographical knowledge and quick decision-making in community events and tournaments, proudly
          representing Blueshell esports.
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
    name: "",
    bg: $require("@/assets/geoguessr1.jpg"),
    players: [
      {name: "", ign: "Job 404"},
      {name: "", ign: "FetaBass"},
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
