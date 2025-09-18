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
import {$require} from "@/plugins/require.js";
import {ref} from "vue";
import type Team from "@/types/Team.ts"

const teams = ref<Team[]>([
  {
    name: 'BS Tempra',
    bg: $require('@/assets/valorantesports1.jpg'),
    players: [
      {
        name: 'Chris Wong',
        ign: 'fetabass'
      },
      {
        name: 'Mudassar Jamil',
        ign: 'hazzY'
      },
      {
        name: 'Dylan Holkenborg',
        ign: 'Infory'
      },
      {
        name: 'Niels de Gruijl',
        ign: 'BigMonke778'
      },
      {
        name: 'Amir Kuanov',
        ign: 'seidukan'
      },
    ],
    substitutes: [
      {
        name: 'Stefan Morriën',
        ign: 'stweebfan'
      }
    ]
  },
  {
    name: 'BS SpicyWater',
    bg: $require('@/assets/valorantesports2.jpg'),
    players: [
      {
        name: 'Michal Rokita',
        ign: 'udeyy'
      },
      {
        name: 'Thomas Dekker',
        ign: 'ItIsIThomas'
      },
      {
        name: 'Sanne van Kooten',
        ign: 'EternalScreams'
      },
      {
        name: 'Max Jansdam',
        ign: 'DwalendeDrol'
      },
      {
        name: 'Joris Jonkers',
        ign: 'DJundertaker25'
      },
    ],
    substitutes: [
      {
        name: 'Reinier Algra',
        ign: 'Reign'
      }
    ]
  },
  {
    name: 'BS IcyWater',
    bg: $require('@/assets/valorantesports1.jpg'),
    players: [
      {
        name: 'Jesse van Gameren',
        ign: 'Machom36'
      },
      {
        name: 'Jelle van Wezep',
        ign: 'TheJellyMan'
      },
      {
        name: 'Mitchell van Poecke',
        ign: 'tennisky1'
      },
      {
        name: 'Yannick Sloot',
        ign: 'Yank'
      },
      {
        name: 'René Hammink',
        ign: 'Mr. Pancake'
      },
    ],
  }
]);
</script>

<style lang="scss" scoped>

</style>
