<template>
  <v-main>
    <top-banner title="Trackmania" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          We have two players playing under the Blueshell banner for multiple Trackmania tournaments, most notably
          in the regionals of the Trackmania World Tour circuit. Aside from that we have multiple players sporting our
          club tag, competing in whatever tournament happens to be active. Be sure to say hi if you see a [ESABS] player
          driving somewhere!
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

// IMPORTANT: This component is deprecated, it must not be reachable, until Trackmania is played again in
// The dutch esports scene

const teams = ref<Team[]>([
  {
    name: "[ESABS]",
    bg: $require("@/assets/trackmaniabg.jpg"),
    players: [
      {name: "", ign: "Poepboer"},
      {name: "", ign: "Swordie"},
    ],
  },
])
</script>
