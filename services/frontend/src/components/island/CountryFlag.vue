<script lang="ts" setup>
/**
 * `flag-icons` rather than an emoji flag, which is the operating system's and renders as
 * anything from a glossy sticker to two letters in a box. Its stylesheet loads in `main.ts`.
 */
defineOptions({name: "CountryFlag"})

const {code, size = 18} = defineProps<{
  /** The cca2 code, in either case. */
  code: string
  /** Its width in pixels; the height follows the 4:3 the artwork is drawn at. */
  size?: number
}>()
</script>

<template>
  <span
    aria-hidden="true"
    class="country-flag fi"
    :class="`fi-${code.toLowerCase()}`"
    :style="{width: `${size}px`, height: `${Math.round(size * 0.75)}px`}"
  />
</template>

<style scoped>
.country-flag {
  flex: none;
  display: inline-block;
  background-size: cover;
  background-position: center;
  /* The artwork runs to its own edge, so a hairline is what keeps a white or pale flag from
     bleeding into the row it sits on. */
  box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--color-void) 25%, transparent);
}
</style>
