<template>
  <v-main>
    <top-banner title="League of Legends"/>
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
      v-for="team in teams"
      :key="team.name"
    >
      <team-details :team="team" />
    </template>
  </v-main>
</template>

<script setup lang="ts">
import TopBanner from "@/components/banners/TopBanner.vue";
import TeamDetails from "@/components/TeamDetails.vue";
import type Team from "@/types/Team.ts"

import {$require} from "@/plugins/require.js";
import {ref} from 'vue';

const teams = ref<Team[]>([
  {
    name: 'BS Scaling',
    bg: $require('@/assets/leagueesportsbg1.jpg'),
    players: [
      {
        name: 'Wilco Strijker',
        ign: 'Fill Main'
      },
      {
        name: 'Thomas van den Brink',
        ign: 'ThompieB'
      },
      {
        name: 'Bob Even',
        ign: 'Bobbus'
      },
      {
        name: 'Mark Ganzevles',
        ign: 'Magaera'
      },
      {
        name: 'Ivo Heitlager',
        ign: 'BS King Cookie'
      }
    ],
    coaches: [
      {
        name: 'Hielke Kleijsen',
        ign: 'OG Klutch'
      }
    ]
  },
  {
    name: 'BS Housecats',
    bg: $require('@/assets/leagueesportsbg2.jpg'),
    players: [
      {
        name: 'Mark Otter',
        ign: 'markdeotter'
      },
      {
        name: 'Mithell van Poecke',
        ign: 'tennissky1'
      },
      {
        name: 'Victor Barkey',
        ign: 'The RUde Unicorn'
      },
      {
        name: 'Jesse van Gameren',
        ign: 'Machom36'
      },
      {
        name: 'Jelle van Wezep',
        ign: 'TheJellyMan'
      }
    ],
    coaches: [
      {
        name: 'Max Jansdam',
        ign: 'DwalendeDrol'
      }
    ],
    substitutes: [
      {
        name: "Sylwia Siekman",
        ign: "SylWorld"
      },
      {
        name: 'Roos Kruk',
        ign: 'ShadowSkyeWolf'
      },
    ]
  },
]);
</script>

<style lang="scss" scoped>
.v-parallax__content {
  padding-left: 0 !important;
  padding-right: 0 !important;
}
</style>
