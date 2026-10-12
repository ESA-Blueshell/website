<template>
  <div
    class="top-banner"
    data-testid="page-banner"
  >
    <div
      class="top-banner__title"
      data-testid="page-banner-title"
    >
      {{ uppercaseTitle }}
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed} from "vue"

const props = defineProps<{title: string}>()

const uppercaseTitle = computed(() => props.title.toUpperCase())
</script>

<style scoped>
.top-banner {
  align-items: center;
  background-image:
    linear-gradient(rgba(0, 0, 0, 0.45), rgba(0, 0, 0, 0.55)),
    image-set(url("/banner-1920.webp") 1x, url("/banner.webp") 2x);
  background-position: center;
  background-size: cover;
  box-shadow: inset 0 -36px 48px rgba(0, 0, 0, 0.22);
  display: flex;
  height: 200px;
  justify-content: center;
}

.top-banner__title {
  color: #fff;
  font-family: "Shellhouse One", sans-serif;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 1.5px;
  line-height: normal;
  text-align: center;
}

/* Vuetify's sm, md and lg widths, which the pages under the banner still lay out by. */
@media (min-width: 600px) {
  .top-banner__title {
    font-size: 50px;
  }
}

@media (min-width: 840px) {
  .top-banner__title {
    font-size: 70px;
  }
}

@media (min-width: 1145px) {
  .top-banner {
    height: 300px;
  }

  .top-banner__title {
    font-size: 90px;
  }
}

/* The widths siteBanner.ts lists, picked by how wide the band is drawn: a background has no
   `sizes` for the browser to read. */
@media (max-width: 640px) {
  .top-banner {
    background-image:
      linear-gradient(rgba(0, 0, 0, 0.45), rgba(0, 0, 0, 0.55)),
      image-set(url("/banner-640.webp") 1x, url("/banner-1280.webp") 2x);
  }
}

@media (min-width: 641px) and (max-width: 1280px) {
  .top-banner {
    background-image:
      linear-gradient(rgba(0, 0, 0, 0.45), rgba(0, 0, 0, 0.55)),
      image-set(url("/banner-1280.webp") 1x, url("/banner-2560.webp") 2x);
  }
}
</style>
