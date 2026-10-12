<script lang="ts" setup>
/** The tick that says a task is done, in the house's own green. */
defineOptions({name: "DoneTick"})

const {size = 48} = defineProps<{
  /** Its width and height in pixels. */
  size?: number
}>()
</script>

<template>
  <svg
    aria-hidden="true"
    class="done-tick"
    fill="none"
    :height="size"
    stroke="currentColor"
    stroke-linecap="square"
    stroke-width="3"
    viewBox="0 0 24 24"
    :width="size"
  >
    <path d="M4 12.5 9.5 18 20 6.5" />
  </svg>
</template>

<style scoped>
.done-tick {
  flex: none;
  color: var(--color-ok);
}
</style>
