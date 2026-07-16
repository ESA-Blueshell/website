<template>
  <div
    :style="{ height: containerHeight }"
    class="top-banner"
  >
    <div
      :style="md ? { 'font-size': '70px !important'} : sm ? { 'font-size': '50px !important'} : xs ? { 'font-size': '40px !important'} : {}"
      class="text-h1 font-weight-bold text-white text-center"
      style="letter-spacing: 1.5px !important;line-height: normal !important;"
    >
      {{ uppercaseTitle }}
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import {useDisplay} from "vuetify"

interface Props {
  title: string
  /** Desktop/tablet height (lg and up). Accepts number (px) or any CSS length (e.g., '24rem'). */
  height?: number | string
  /** Mobile height (md and down). Accepts number (px) or any CSS length. */
  mHeight?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  height: "300px",
  mHeight: "200px",
})

const {lgAndUp, md, sm, xs} = useDisplay()

const toCssSize = (v: number | string) => (typeof v === "number" ? `${v}px` : v)

const containerHeight = computed(() =>
  lgAndUp.value ? toCssSize(props.height) : toCssSize(props.mHeight),
)

const uppercaseTitle = computed(() => (props.title ?? "").toUpperCase())
</script>

<style scoped>
.top-banner {
  align-items: center;
  background-image:
    linear-gradient(rgba(0, 0, 0, 0.45), rgba(0, 0, 0, 0.55)),
    url("/banner.webp");
  background-position: center;
  background-size: cover;
  box-shadow: inset 0 -36px 48px rgba(0, 0, 0, 0.22);
  display: flex;
  justify-content: center;
}
</style>
