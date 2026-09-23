<template>
  <v-main>
    <!-- The bands already on the island; the Vuetify sections below follow slice by slice. -->
    <island
      class="home-island"
      testid="home-island"
    >
      <home-hero />
      <upcoming-band />
      <casual-band />
      <lineup-band />
    </island>

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
import {ref} from "vue"

import Island from "@/components/island/Island.vue"
import HomeHero from "@/domains/association/island/HomeHero.vue"
import UpcomingBand from "@/domains/association/island/UpcomingBand.vue"
import CasualBand from "@/domains/association/island/CasualBand.vue"
import LineupBand from "@/domains/esports/island/LineupBand.vue"
import DiscordBanner from "@/components/base/DiscordBanner.vue"
import SocialsBanner from "@/components/common/banners/SocialsBanner.vue"

import {$require} from "@/plugins/require.js"
import {$goto} from "@/plugins/goto"
import {associationYears} from "@/utils/association"

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
/* The island root fills a page; here it holds only the bands above the Vuetify sections. */
.home-island {
  min-height: 0;
}

.expand {
  transition: transform .2s;
  cursor: pointer;
}

.expand:hover {
  transform: scale(1.1) translateY(-10px);
}
</style>
