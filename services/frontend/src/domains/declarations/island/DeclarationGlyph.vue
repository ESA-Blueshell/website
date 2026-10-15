<script lang="ts" setup>
/** The marks the declaration rows draw, stroked in the current colour as the security rows are. */
defineOptions({name: "DeclarationGlyph"})

defineProps<{
  name: "plus" | "receipt" | "bank" | "paid"
}>()
</script>

<template>
  <svg
    aria-hidden="true"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    viewBox="0 0 24 24"
  >
    <template v-if="name === 'plus'">
      <path d="M12 5v14M5 12h14" />
    </template>
    <template v-else-if="name === 'receipt'">
      <path d="M6 3.5h12v17l-2-1.4-2 1.4-2-1.4-2 1.4-2-1.4-2 1.4z" />
      <path d="M9 8h6M9 11.5h6M9 15h3.5" />
    </template>
    <template v-else-if="name === 'bank'">
      <path d="M3.5 9 12 4l8.5 5z" />
      <path d="M5.5 9.5v7M10 9.5v7M14 9.5v7M18.5 9.5v7M3.5 19.5h17" />
    </template>
    <template v-else>
      <path d="M6 3.5h12v17l-2-1.4-2 1.4-2-1.4-2 1.4-2-1.4-2 1.4z" />
      <path d="m9 11.5 2.2 2.2 4-4.2" />
    </template>
  </svg>
</template>
