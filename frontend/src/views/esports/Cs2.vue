<template>
  <v-main>
    <top-banner title="Counter-Strike 2" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          With shooters' prevalence in the global esports scene, Blueshell Esports's CS2 teams are
          trying to climb up the charts with those sweet headshots! Below you can find our
          competitive CS2 team(s).
        </p>
      </div>
    </div>
    <team-details :team="hypers" />
  </v-main>
</template>

<script setup lang="ts">
import TopBanner from "@/components/banners/TopBanner.vue";
import TeamDetails from "@/components/TeamDetails.vue";
import { $require } from "@/plugins/require.js";
import { ref } from 'vue';

// Types
interface TeamMember {
  name: string;
  ign: string;
}

interface Team {
  name: string;
  bg: string;
  players: TeamMember[];
  coaches: TeamMember[];
  substitutes: TeamMember[];
}

// Data
const hypers = ref<Team>({
  name: 'BS HyperS',
  bg: $require('@/assets/csgoesports1.jpg'),
  players: [
    {
      name: 'Ivo Heitlager',
      ign: 'BSKingCookie'
    },
    {
      name: 'Reinier Algra',
      ign: 'Reign'
    },
    {
      name: 'Joran Hagen',
      ign: 'MacVanish_'
    },
    {
      name: 'Mario van der Wal Agraz',
      ign: 'TheCheeser'
    },
    {
      name: 'Marijn de Boer',
      ign: 'Marijn02'
    },
  ],
  coaches: [
    {
      name: 'Bob Even',
      ign: 'Bobbuz'
    }
  ],
  substitutes: [
    {
      name: 'Chris Wong',
      ign: 'FetaBass'
    }
  ]
});
</script>

<style lang="scss" scoped>

</style>
