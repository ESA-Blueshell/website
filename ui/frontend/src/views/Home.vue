<template>
  <v-main>
    <main-banner />

    <v-container>
      <div
        class="mx-auto my-10"
        style="max-width: 600px"
      >
        <p class="text-center text-h2 font-weight-light">
          Who are we?
        </p>

        <p class="text-center text-subtitle-1 font-weight-light">
          We are Blueshell Esports, the gaming and esports student association at the University of
          Twente. We house a bustling gaming community, organize regular online and offline events
          and present opportunities for competitive play.
        </p>
      </div>
    </v-container>

    <v-container class="mb-10">
      <v-row
        class="text-center"
        justify="center"
      >
        <v-col
          v-for="col in columns"
          :key="col.title"
          style="max-width:450px;min-height: 250px"
          cols="12"
          sm="5"
          md="4"
          align-self="center"
        >
          <a
            class="expand"
            style="width: 100%"
            @click="$goto(col.url)"
          >
            <v-icon
              size="x-large"
              :color="col.color"
              class="icon"
            >
              {{ col.icon }}
            </v-icon>
            <p
              class="text-h3 ma-3 font-weight-thin"
            >
              {{ col.title }}
            </p>
          </a>
          <p
            class="text-body-1 font-weight-light mx-auto"
            style="max-width: 400px"
          >
            {{ col.text }}
          </p>
        </v-col>
      </v-row>
    </v-container>

    <discord-banner />

    <games-we-play
      :games="games"
      class="pt-3 pb-3"
    />

    <socials-banner />

    <v-container class="mt-10 mb-16">
      <p class="mx-auto text-center text-h2">
        Our partners
      </p>
      <v-row
        justify="space-around"
        align="center"
        class="mt-6 mx-auto"
        style="max-width: 1100px"
      >
        <v-col
          v-for="partner in partners"
          :key="partner.url"
          cols="12"
          sm="6"
          class="pa-6"
        >
          <v-img
            :src="$vuetify.theme.global.current.dark?partner.logoDark:partner.logo"
            style="max-width: 450px"
            class="mx-auto expand"
            @click="$goto(partner.url)"
          />
        </v-col>
      </v-row>
    </v-container>
  </v-main>
</template>

<script>

import MainBanner from "@/components/banners/MainBanner.vue";
import DiscordBanner from "@/components/DiscordBanner.vue";
import SocialsBanner from "@/components/banners/SocialsBanner.vue";
import GamesWePlay from "@/components/GamesWePlay.vue";
import {$require} from "@/plugins/require";
import {$goto} from "@/plugins/goto";

export default {
  components: {GamesWePlay: GamesWePlay, SocialsBanner: SocialsBanner, DiscordBanner: DiscordBanner, MainBanner: MainBanner},
  data() {
    return {
      hoveredGame: null,
      games: [
        {
          categoryName: "Competitive",
          titles: [
            {
              title: "League of Legends",
              bg: $require("@/assets/leaguebg.jpg"),
              icon: $require("@/assets/league.png"),
              esportsLink: "/esports/league-of-legends"
            },
            {
              title: "Counter-Strike 2",
              bg: $require("@/assets/csgobg.jpg"),
              icon: $require("@/assets/csgo.png"),
              esportsLink: "/esports/counter-strike-2"
            },
            {
              title: "Valorant",
              bg: $require("@/assets/valorantbg.jpg"),
              icon: $require("@/assets/valorant.png"),
              esportsLink: "/esports/valorant"
            },
            {
              title: "Rocket League",
              bg: $require("@/assets/rocketleaguebg.jpg"),
              icon: $require("@/assets/rocketleague.png"),
              esportsLink: "/esports/rocketleague"
            },
            {
              title: "Trackmania",
              bg: $require("@/assets/trackmaniabg.jpg"),
              icon: $require("@/assets/trackmania.png"),
              esportsLink: "/esports/trackmania"
            },
          ]
        },
        {
          categoryName: "Community",
          titles: [
            {
              title: "Dota 2",
              bg: $require("@/assets/dota2bg.jpg"),
              icon: $require("@/assets/dota2.png"),
            },
            {
              title: "Minecraft",
              bg: $require("@/assets/minecraftbg.jpg"),
              icon: $require("@/assets/minecraft.png"),
            },
            {
              title: "Pokémon",
              bg: $require("@/assets/pokemonbg.jpg"),
              icon: $require("@/assets/pokemon.png"),
            },
            {
              title: "Overwatch",
              bg: $require("@/assets/overwatchbg.jpg"),
              icon: $require("@/assets/overwatch.png"),
            },
            {
              title: "Super Smash Bros",
              bg: $require("@/assets/smashbg.jpg"),
              icon: $require("@/assets/smash.png"),
            },
            {
              title: "Team Fight Tactics",
              bg: $require("@/assets/tftbg.jpg"),
              icon: $require("@/assets/tft.png"),
            },
            {
              title: "Trackmania",
              bg: $require("@/assets/trackmaniabg.jpg"),
              icon: $require("@/assets/trackmania.png"),
            },
            {
              title: "Valorant",
              bg: $require("@/assets/valorantbg.jpg"),
              icon: $require("@/assets/valorant.png"),
            },
            {
              title: "World of Warcraft",
              bg: $require("@/assets/wowbg.jpg"),
              icon: $require("@/assets/wow.png"),
            },
          ]
        }
      ],
      columns: [
        {
          icon: "mdi-account-group",
          color: "red darken-2",
          title: "About us",
          url: "/aboutus",
          text: "Despite its memberbase, Blueshell Esports is a relatively young student association with only 5 years since its inception. Learn all about our association by clicking above!"
        },
        {
          icon: "mdi-trophy",
          color: "yellow darken-2",
          title: "Esports",
          url: "/esports",
          text: "As the name of our association suggests, esports is an integral part of Blueshell. Click the icon above to find more information on what we offer!"
        },
        {
          icon: "mdi-calendar",
          color: "blue darken-2",
          title: "Events",
          url: "/events",
          text: "To keep our community entertained, Blueshell hosts events of many kinds with the help of member-run committees. Click above to see the upcoming events!"
        },
      ],
      partners: [
        {
          logo: $require("@/assets/elnino.png"),
          logoDark: $require("@/assets/elnino.png"),
          url: "/partners/el-nino"
        },
        {
          logo: $require("@/assets/ett.png"),
          logoDark: $require("@/assets/ettdark.png"),
          url: "https://esportsteamtwente.nl/"
        },
      ]
    }
  },
  methods: {
    $goto,
  }
}
</script>
<style>
.expand {
  transition: transform .2s;
  cursor: pointer;
}

.icon {
  transition: transform .2s;
}

.expand:hover .icon {
  transform: scale(1.1) translateY(-10px);
}
</style>
