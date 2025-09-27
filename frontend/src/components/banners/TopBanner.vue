<template>
  <div
    :style="{ height: lgAndUp ? '300px' : '200px'}"
    style="background-position: center;background-size: cover; display: flex;align-items: center;justify-content: center;background-image: linear-gradient(rgba(0,0,0,0.5),rgba(0,0,0,0.5)), url('/banner.webp')"
  >
    <div
      :style="md ? { 'font-size': '70px !important'} : sm ? { 'font-size': '50px !important'} : xs ? { 'font-size': '40px !important'} : {}"
      class="text-h1 font-weight-bold text-white text-center"
      style="letter-spacing: 5px !important;line-height: normal !important;"
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
}

const props = defineProps<Props>()

const {lgAndUp, md, sm, xs} = useDisplay()

const uppercaseTitle = computed(() => (props.title ?? "").toUpperCase())
</script>
