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
          align-self="center"
          class="expand"
          cols="12"
          md="4"
          sm="5"
          style="max-width:450px;min-height: 250px"
        >
          <a
            class="d-block"
            style="width: 100%; color: inherit; text-decoration: none;"
            @click="$goto(col.url)"
          >
            <v-icon
              :color="col.color"
              size="x-large"
            >
              {{ col.icon }}
            </v-icon>
            <p class="text-h3 ma-3 font-weight-thin">
              {{ col.title }}
            </p>
            <p
              class="text-body-1 font-weight-light mx-auto"
              style="max-width: 400px"
            >
              {{ col.text }}
            </p>
          </a>
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
        align="center"
        class="mt-6 mx-auto"
        justify="space-around"
        style="max-width: 1100px"
      >
        <v-col
          v-for="partner in partners"
          :key="partner.url"
          class="pa-6"
          cols="12"
          sm="6"
        >
          <v-img
            :src="$vuetify.theme.global.current.dark ? partner.logoDark : partner.logo"
            class="mx-auto expand"
            style="max-width: 450px"
            @click="$goto(partner.url)"
          />
        </v-col>
      </v-row>
    </v-container>
  </v-main>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"

import MainBanner from "@/components/common/banners/MainBanner.vue"
import DiscordBanner from "@/components/base/DiscordBanner.vue"
import SocialsBanner from "@/components/common/banners/SocialsBanner.vue"
import GamesWePlay from "@/components/base/GamesWePlay.vue"

import {useGames} from "@/domains/esports/island/useGames"
import {srcsetOf} from "@/domains/esports/pictures"
import {$require} from "@/plugins/require.js"
import {$goto} from "@/plugins/goto"
import {associationYears} from "@/utils/association"

interface GameTitle {
  title: string
  bg: string
  icon: string
  /** The widths each picture is stored at, where it came from a record rather than the bundle. */
  bgSrcset?: string
  iconSrcset?: string
  esportsLink?: string
}

interface GameCategory {
  categoryName: string
  titles: GameTitle[]
}

interface Column {
  icon: string
  color: string
  title: string
  url: string
  text: string
}

interface Partner {
  logo: string
  logoDark: string
  url: string
}

/**
 * The games the association competes in, from the same records the esports pages draw.
 *
 * They were five literals here with bundled art and hardcoded links, so a game the board
 * renamed or re-addressed through the dialogs did not follow it to the busiest page on the
 * site, and Trackmania — fielded in the records — was not listed as competitive at all.
 *
 * `current` is the api's answer to which games are played now: fielded in the most recent
 * season, or in the one before it while the newest has nothing fielded yet. Filtering on it
 * here rather than asking a second endpoint keeps the api shipping the fact and the page
 * deciding what its Competitive block means.
 */
const {current: playedNow} = useGames()

const competitive = computed<GameCategory>(() => ({
  categoryName: "Competitive",
  titles: playedNow.value.map(game => ({
    title: game.name,
    // Resolved against the api at the esports adapter, so nothing here builds a url.
    bg: game.banner?.url ?? "",
    bgSrcset: srcsetOf(game.banner),
    icon: game.icon?.url ?? "",
    iconSrcset: srcsetOf(game.icon),
    esportsLink: `/esports/${game.slug}`,
  })),
}))

/**
 * The games members play together, which are not games the association fields.
 *
 * These stay written down here: they have no record, and giving them one is a different piece
 * of work. So the art they name stays bundled too.
 */
const community = ref<GameCategory[]>([
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
    ],
  },
])

/**
 * Both blocks, less any that came out empty. An empty Competitive heading is what the records
 * being unreachable would otherwise look like, and a heading over nothing is worse than no
 * heading at all.
 */
const games = computed<GameCategory[]>(() =>
  [competitive.value, ...community.value].filter(one => one.titles.length > 0))

const columns = ref<Column[]>([
  {
    icon: "mdi-account-group",
    color: "red darken-2",
    title: "About us",
    url: "/aboutus",
    text:
      `Despite its memberbase, Blueshell Esports is a relatively young student association with only ${associationYears()} years since its inception. Learn all about our association by clicking above!`,
  },
  {
    icon: "mdi-trophy",
    color: "yellow darken-2",
    title: "Esports",
    url: "/esports",
    text:
      "As the name of our association suggests, esports is an integral part of Blueshell. Click the icon above to find more information on what we offer!",
  },
  {
    icon: "mdi-calendar",
    color: "blue darken-2",
    title: "Events",
    url: "/events",
    text:
      "To keep our community entertained, Blueshell hosts events of many kinds with the help of member-run committees. Click above to see the upcoming events!",
  },
])

const partners = ref<Partner[]>([
  {
    logo: $require("@/assets/elnino.png"),
    logoDark: $require("@/assets/elnino.png"),
    url: "/partners/el-nino",
  },
  {
    logo: $require("@/assets/marketing_maatwerk_logo_big.png"),
    logoDark: $require("@/assets/marketing_maatwerk_logo_big.png"),
    url: "https://marketingmaatwerk.nl/",
  },
  {
    logo: $require("@/assets/ett.png"),
    logoDark: $require("@/assets/ettdark.png"),
    url: "https://esportsteamtwente.nl/",
  },
])

</script>

<style lang="scss" scoped>
.expand {
  transition: transform .2s;
  cursor: pointer;
}

.expand:hover {
  transform: scale(1.1) translateY(-10px);
}
</style>
