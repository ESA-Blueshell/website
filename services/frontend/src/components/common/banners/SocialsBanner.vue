<template>
  <div :style="{background: $vuetify.theme.computedThemes[$vuetify.theme.global.name].colors.wallpaper}">
    <v-row
      align="center"
      class="mx-auto container flex-nowrap"
      justify="space-between"
    >
      <v-col
        cols="120"
        md="auto"
      >
        <p
          class="text-white text-h4 font-weight-thin mb-0"
          style="float: left"
        >
          Follow us on Social Media
        </p>
      </v-col>
      <v-spacer />
      <v-col md="auto">
        <v-btn
          v-for="social in socials"
          :key="social.url"
          :href="social.url"
          class="ml-5 my-3"
          color="primary"
          target="_blank"
        >
          <v-icon style="--v-icon-size-multiplier: 1.7 ">
            {{ social.icon }}
          </v-icon>
        </v-btn>
      </v-col>
    </v-row>
  </div>
</template>

<script lang="ts">
export default {
  name: "SocialsBanner",
  data() {
    return {
      socials: [
        {
          icon: "mdi-instagram",
          url: "https://www.instagram.com/esablueshell/",
        },
        {
          icon: "mdi-facebook",
          url: "https://www.facebook.com/BlueshellEsports",
        },
        {
          icon: "mdi-twitch",
          url: "https://www.twitch.tv/blueshellesports",
        },
        {
          icon: "mdi-twitter",
          url: "https://twitter.com/BlueshellESA",
        },
        {
          icon: "mdi-linkedin",
          url: "https://linkedin.com/company/blueshell-esports",
        },
      ],
    }
  },
}
</script>

<style lang="scss" scoped>

.container {
  max-width: 1100px;
  height: 100%;
}

.v-btn {
  padding: 10px !important;
  width: 65px !important;
  height: 65px !important;
  min-width: 0 !important;
}

</style>
