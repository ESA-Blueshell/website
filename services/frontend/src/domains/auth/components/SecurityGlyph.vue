<script lang="ts" setup>
/** The marks the security pages draw beside their rows, stroked in the current colour. */
defineOptions({name: "SecurityGlyph"})

defineProps<{
  name: "shield" | "key" | "mail" | "screens" | "log" | "phone"
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
    <template v-if="name === 'shield'">
      <path d="M12 3.5 5 6v5.5c0 4.2 3 7.6 7 9 4-1.4 7-4.8 7-9V6z" />
      <path d="m9 12 2.2 2.2L15.5 10" />
    </template>
    <template v-else-if="name === 'key'">
      <circle
        cx="8"
        cy="15"
        r="3.8"
      />
      <path d="M10.8 12.3 19 4.2M16 7.2l2.3 2.3M13.9 9.3l1.8 1.8" />
    </template>
    <template v-else-if="name === 'mail'">
      <rect
        height="13"
        width="17"
        x="3.5"
        y="5.5"
      />
      <path d="m4 6.5 8 6.5 8-6.5" />
    </template>
    <template v-else-if="name === 'screens'">
      <rect
        height="10"
        width="14"
        x="2.5"
        y="4.5"
      />
      <path d="M6.5 18.5h6M9.5 14.5v4" />
      <rect
        height="10.5"
        width="5"
        x="16.5"
        y="9"
      />
    </template>
    <template v-else-if="name === 'log'">
      <path d="M5 4.5h14v15H5z" />
      <path d="M8.5 8.5h7M8.5 12h7M8.5 15.5h4" />
    </template>
    <template v-else>
      <rect
        height="19"
        width="10"
        x="7"
        y="2.5"
      />
      <path d="M11 18.5h2" />
    </template>
  </svg>
</template>
