<script lang="ts" setup>
/** A bank, stroked in the current colour as the security rows' marks are. */
defineOptions({name: "BankGlyph"})
</script>

<template>
  <svg
    aria-hidden="true"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    viewBox="0 0 24 24"
  >
    <path d="M3.5 9 12 4l8.5 5z" />
    <path d="M5.5 9.5v7M10 9.5v7M14 9.5v7M18.5 9.5v7M3.5 19.5h17" />
  </svg>
</template>
