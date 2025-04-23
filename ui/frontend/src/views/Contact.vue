<template>
  <v-main>
    <top-banner title="Contact" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          If you want to become a member see the
          <span
            class="text-decoration-underline text-primary"
            style="cursor: pointer;"
            @click="$goto('membership')"
          >Join us!</span>
          page. If you are just looking for
          more information, you can contact the board at
          <a
            href="mailto:board@blueshell.utwente.nl"
            target="_blank"
            class="text-decoration-none"
          >board@blueshell.utwente.nl</a>
          or join the
          <a
            href="https://discord.gg/WyDhbAP"
            target="_blank"
            class="text-decoration-none"
          >Discord</a> and send a PM to
          the
          board members.
        </p>
        <p class="text-body-1">
          Do you want to ask your question in person? Usually, you can find a board member in the
          Predator Esports Lounge during the break.
        </p>
        <v-row class="mt-8">
          <v-col
            cols="12"
            sm="4"
            class="pa-0"
          >
            <p class="text-body-1">
              The post address for Blueshell is as follows:
              <br> Blueshell Esports
              <br> Postbus 217 (Bastille 49)
              <br> 7500 AE Enschede
              <br> Netherlands
            </p>
          </v-col>
          <v-col
            cols="12"
            md="8"
            class="pa-0"
          >
            <v-skeleton-loader
              v-if="mapLoading"
              type="image,image,image"
              width="600"
              height="450"
            />
            <iframe
              src="https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d2443.009787256024!2d6.849677851362431!3d52.24320587966339!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x47b8133b297e5b15%3A0x918c3341c5447636!2sPredator%20Esports%20Lounge!5e0!3m2!1sen!2snl!4v1669661043450!5m2!1sen!2snl"
              width="600"
              height="450"
              style="border:0;max-width: 100%"
              allowfullscreen=""
              loading="lazy"
              referrerpolicy="no-referrer-when-downgrade"
              :style="{filter: $vuetify.theme.global.current.dark ? 'invert(90%)' : ''}"
              @load="mapLoading=false"
            />
          </v-col>
        </v-row>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$goto} from "@/plugins/goto";

export default {
  components: {TopBanner: TopBanner},
  data: () => ({
    mapLoading: true,
  }),
  methods: {$goto},
}
</script>

<style scoped
       lang="scss"
>
@use '../styles/settings';

iframe {
  border-radius: settings.$border-radius-root;
}
</style>
