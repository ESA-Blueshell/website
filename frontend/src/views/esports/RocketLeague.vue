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
      v-for="team in teams"
      :key="team.name"
    >
      <team-details
        :team="team"
        :name-right="true"
      />
    </template>
  </v-main>
</template>

<script setup lang="ts">
import TopBanner from "../../components/banners/TopBanner.vue";
import TeamDetails from "@/components/TeamDetails.vue";
import type Team from "@/types/Team.ts"

import {$require} from "@/plugins/require.js";
import {ref} from 'vue';

const teams = ref<Team[]>([
  {
    name: 'BS Oogway',
    bg: $require('@/assets/rocketleagueesports.jpg'),
    players: [
      {
        name: 'Allard van der Hooft',
        ign: 'Vdhooft_'
      },
      {
        name: 'Max Liebe',
        ign: 'Amalox'
      },
      {
        name: 'Job de Ruijter',
        ign: 'Job 404'
      },
      {
        name: 'Julian van den Nieuwenhuizen',
        ign: 'Bak en Braad'
      },
    ]
  },
  {
    name: "BS Squirtles",
    bg: $require('@/assets/rocketleagueesports2.jpg'),
    players: [
      {
        name: "Thomas Dekker",
        ign: "ItIsIThomas"
      },
      {
        name: "Joy Verheijen",
        ign: "Bluegy"
      },
      {
        name: "Huub Hammink",
        ign: "bosvruchtenthee"
      },
    ]
  },
  {
    name: "BS Tadpole",
    bg: $require('@/assets/rocketleagueesports2.jpg'),
    players: [
      {
        name: "Stijn Dollenkamp",
        ign: "SJAWID"
      },
      {
        name: "Thijs Krosman",
        ign: "JustDqnt"
      },
      {
        name: "Henry Slegers",
        ign: "poopiejoe"
      },
      {
        name: "Thijs Willems",
        ign: "Darkneoteric"
      },
    ]
  }
]);
</script>

<style lang="scss" scoped>

</style>
