<template>
  <v-main>
    <top-banner title="Counter-Strike 2" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          With shooters’ prevalence in the global esports scene, Blueshell Esports’s CS2 teams are
          trying to climb up the charts with those sweet headshots! Below you can find our
          competitive CS2 team(s).
        </p>
      </div>
    </div>
    <team :team="hypers" />
  </v-main>
</template>

<script>
import TopBanner from "../../components/banners/TopBanner.vue";
import Team from "@/components/Team.vue";
import {$require} from "@/plugins/require";

export default {
  components: {Team: Team, TopBanner: TopBanner},
  data: () => {
    return {
      hypers: {
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
      },
    }
  }
}
</script>

<style scoped>

</style>
