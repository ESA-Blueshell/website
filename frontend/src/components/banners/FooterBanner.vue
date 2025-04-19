<script setup>

const iconLinks = [
  {
    icon: 'mdi-email',
    href: 'mailto:board@blueshell.utwente.nl',
  },
  {
    icon: 'mdi-instagram',
    href: 'https://www.instagram.com/esablueshell/',
  },
  {
    icon: 'mdi-facebook',
    href: 'https://www.facebook.com/BlueshellEsports/',
  },
  {
    icon: 'mdi-twitch',
    href: 'https://www.twitch.tv/blueshellesports',
  },
  {
    icon: 'mdi-twitter',
    href: 'https://twitter.com/BlueshellESA',
  },
  {
    icon: 'mdi-linkedin',
    href: 'https://www.linkedin.com/company/blueshell-esports',
  },
];

const textLinks = [
  {
    text: 'El Niño',
    href: 'https://www.elnino.tech/',
  },
  {
    text: 'ETT',
    href: 'https://esportsteamtwente.nl/',
  },
  {
    text: 'Predator Esports Lounge',
    href: 'https://www.esportsloungetwente.nl/',
  },
];

</script>

<template>
  <v-lazy>
    <v-footer
      v-if="$vuetify.display.mdAndUp"
      theme="dark"
      height="160"
      class="pa-8 justify-center"
    >
      <v-row
        align="center"
        style="max-width: 1000px"
      >
        <div style="display: flex; flex-flow: column wrap; max-height: 120px; width: calc(3 * 48px);">
          <v-btn
            v-for="link in iconLinks"
            :key="link.href"
            :href="link.href"
            target="_blank"
            variant="plain"
            :icon="link.icon"
          />
        </div>

        <div
          class="ml-8"
          style="display: flex; flex-flow: column wrap; max-height: calc(2*36px + 4px);gap: 4px"
        >
          <v-btn
            v-for="link in textLinks"
            :key="link.href"
            :href="link.href"
            target="_blank"
          >
            {{ link.text }}
          </v-btn>
        </div>

        <v-spacer />

        <div>
          SITECIE GANG &copy; {{ new Date().getFullYear() }}
        </div>
      </v-row>
    </v-footer>

    <v-footer
      v-else-if="$vuetify.display.smAndDown"
      theme="dark"
      class="d-flex flex-column pa-4"
      :height="$vuetify.display.sm ? 216 : 304"
    >
      <div>
        <v-btn
          v-for="link in iconLinks"
          :key="link.href"
          :href="link.href"
          target="_blank"
          variant="plain"
          :icon="link.icon"
        />
      </div>

      <div
        class="mt-4"
        :style="{ display: $vuetify.display.xs ? 'flex' : 'grid' }"
        style="flex-direction: column; grid-template-columns: repeat(2, 1fr); gap: 8px;"
      >
        <v-btn
          v-for="link in textLinks"
          :key="link.href"
          :href="link.href"
          target="_blank"
        >
          {{ link.text }}
        </v-btn>
      </div>


      <div class="mt-4">
        SITECIE GANG &copy; {{ new Date().getFullYear() }}
      </div>
    </v-footer>
  </v-lazy>
</template>
