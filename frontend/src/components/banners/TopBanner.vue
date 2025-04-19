<template>
  <div
    style="background-position: center;background-size: cover; display: flex;align-items: center;justify-content: center;background-image: linear-gradient(rgba(0,0,0,0.5),rgba(0,0,0,0.5)), url('/banner.webp')"
    :style="{ height: $vuetify.display.lgAndUp ? '300px' : '200px'}"
  >
    <div
      class="text-h1 font-weight-bold text-white text-center"
      style="letter-spacing: 5px !important;line-height: normal !important;"
      :style="$vuetify.display.md?{ 'font-size': '70px !important'} : $vuetify.display.sm? { 'font-size': '50px !important'} : $vuetify.display.xs? { 'font-size': '40px !important'} : {}"
    >
      {{ title.toUpperCase() }}
    </div>
  </div>
</template>

<script>
export default {
  name: 'TopBanner',
  props: {
    title: String
  },
}
</script>
