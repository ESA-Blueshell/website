<template>
  <v-main>
    <top-banner title="Trackmania" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          We have two players playing under the Blueshell banner for multiple Trackmania tournaments, most notably
          in the regionals of the Trackmania World Tour circuit. Aside from that we have multiple players sporting our
          club tag, competing in whatever tournament happens to be active. Be sure to say hi if you see a [ESABS] player
          driving somewhere!
        </p>
      </div>
    </div>
    <team :team="blueshell" />
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
      blueshell: {
        name: '[ESABS]',
        bg: $require('@/assets/trackmaniabg.jpg'),
        players: [
          {
            name: 'Jarno',
            ign: 'Poepboer'
          },
          {
            name: 'Tim',
            ign: 'Swordie'
          },
        ]
      },
    }
  }
}
</script>

<style scoped>

</style>
