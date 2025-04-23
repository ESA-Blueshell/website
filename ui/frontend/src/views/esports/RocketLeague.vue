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
    <team :team="oogway" />
    <team
      :team="squirtles"
      name-right="true"
    />
    <team :team="turtles" />
    <team
      :team="tadpole"
      name-right="true"
    />
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
      oogway: {
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
      squirtles: {
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
      turtles: {
        name: "BS Turtles",
        bg: $require('@/assets/rocketleagueesports.jpg'),
        players: [
          {
            name: "René Hammink",
            ign: "Mr. Pancake^-^"
          },
          {
            name: "Finn Prinsenberg",
            ign: "Diagonize"
          },
          {
            name: "Bas Willemsen",
            ign: "Dipsy"
          },
        ]
      },
      tadpole: {
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
    }
  }
}
</script>

<style scoped>

</style>
