<template>
  <!--
    This component is the large banner at the top of the front page.
    This was moved to a separate component to keep the Home.vue file from getting cluttered.
  -->
  <div class="banner">
    <v-container
      fluid
      style="background-size: cover;background-position: center;height: 800px;background-image: linear-gradient(rgba(0,0,0,0.5),rgba(0,0,0,0.5)), url('/banner.webp')"
    >
      <v-row
        align="center"
        style="height: 100%"
      >
        <v-col>
          <div class="text-center mt-16">
            <p
              id="blueshell"
              class="text-white text-h2 text-sm-h1 text-md-h1 text-lg-h1 text-xl-h1"
              style="height: 150px"
              :style="$vuetify.display.mdAndUp?{ 'font-size': '175px !important'} : ($vuetify.display.smAndUp?{ 'font-size': '110px !important'}:{ 'font-size': '16vw !important' })"
            >
              BLUESHELL
            </p>

            <p
              class="text-white font-weight-light font-italic"
              :class="{'text-h3': $vuetify.display.mdAndUp, 'text-h4': $vuetify.display.smAndDown}"
            >
              Student esports and gaming association of the Twente region!
            </p>
            <v-btn
              class="mt-5"
              color="primary"
              rounded
              style="font-size: 30px;height: 50px;"
              size="x-large"
              @click="$goto('membership/signup')"
            >
              join now
            </v-btn>
          </div>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script>
import {$goto} from "@/plugins/goto";

export default {
  name: 'MainBanner',
  methods: {$goto}
}
</script>
