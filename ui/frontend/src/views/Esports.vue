<template>
  <v-main>
    <top-banner title="Esports" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-h4 font-weight-light">
          Blueshell's Competitive Scene
        </p>

        <p class="text-body-1">
          Esports is an essential part of Blueshell, being able facilitate esports and giving people the chance to find
          people which whom you can create a team are pillars Blueshell Esports wishes to achieve. Blueshell is not
          alone in this there are many student esports associations around the Netherlands. Together with them the
          student esports in the Netherlands is thriving giving the opportunity for fiery competitive play for many
          different games. There are distinctions between the different competitions, there are series in which you play
          during a specified time period against other team trying to win as much points. There are tournaments which
          are shorter bracket style competitions. This means for almost every game and person who is interested in
          playing competitively there is something!
        </p>
        <p class="text-body-1">
          For more info contact the esports affairs (on discord) or send a mail to <b><a
            href="mailto:esports@blueshell.utwente.nl"
            target="_blank"
            class="text-decoration-none"
          >esports@blueshell.utwente.nl</a></b>
        </p>
        <p class="text-body-1">
          Currently in Blueshell, there are teams for CS:GO, LoL and Rocket League. To find out the current teams in
          Blueshell, click one of the pictures below.
        </p>

        <v-row
          class="my-10"
          justify="center"
        >
          <v-col
            v-for="game in games"
            :key="game.url"
            cols="4"
          >
            <router-link :to="game.url">
              <v-img
                :src="game.icon"
                class="expand"
              />
            </router-link>
          </v-col>
        </v-row>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$require} from "@/plugins/require";

export default {
  components: {TopBanner: TopBanner},
  data() {
    return {
      games: [
        {
          icon: $require('@/assets/league.png'),
          url: '/esports/league-of-legends'
        },
        {
          icon: $require('@/assets/csgo.png'),
          url: '/esports/counter-strike-2'
        },
        {
          icon: $require('@/assets/valorant.png'),
          url: '/esports/valorant'
        },
        {
          icon: $require('@/assets/rocketleague.png'),
          url: '/esports/rocketleague'
        },
        {
          icon: $require('@/assets/trackmania.png'),
          url: '/esports/trackmania'
        },
      ]
    }
  },
}
</script>
<style>
.expand {
  transition: transform .2s;
}

.expand:hover {
  transform: scale(1.1);
}
</style>
