<script lang="ts" setup>
import type {SocialGlyph} from "@/components/island/socialGlyphs"

defineOptions({name: "SocialMark"})

const {glyph, size = 20} = defineProps<{
  glyph: SocialGlyph
  size?: number
}>()
</script>

<template>
  <svg
    aria-hidden="true"
    fill="currentColor"
    :fill-rule="glyph.evenOdd ? 'evenodd' : undefined"
    :height="size"
    :viewBox="glyph.viewBox"
    :width="size"
  >
    <path
      v-for="path in glyph.paths"
      :key="path"
      :d="path"
    />
  </svg>
</template>
