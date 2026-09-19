<!--
  The marks the bar draws.

  Drawn here rather than taken from an icon font, because the bar is the island's top edge and
  everything on it inherits `currentColor`: an icon that carries its own fill reads as a
  different shade from the ones beside it, which is exactly what the management icon used to do.
-->
<template>
  <svg
    aria-hidden="true"
    fill="none"
    :height="size"
    stroke="currentColor"
    stroke-linecap="round"
    stroke-linejoin="round"
    stroke-width="1.4"
    viewBox="0 0 24 24"
    :width="size"
  >
    <template v-if="mark === 'caret'">
      <path d="M6 9.5 12 15.5 18 9.5" />
    </template>
    <template v-else-if="mark === 'menu'">
      <path d="M3 6h18M3 12h18M3 18h18" />
    </template>
    <template v-else-if="mark === 'sun'">
      <circle
        cx="12"
        cy="12"
        r="4.2"
      />
      <path d="M12 2v2.6M12 19.4V22M2 12h2.6M19.4 12H22M4.9 4.9l1.9 1.9M17.2 17.2l1.9 1.9M19.1 4.9l-1.9 1.9M6.8 17.2l-1.9 1.9" />
    </template>
    <template v-else-if="mark === 'moon'">
      <path d="M20 14.2A8.4 8.4 0 0 1 9.8 4a8.6 8.6 0 1 0 10.2 10.2Z" />
    </template>
    <template v-else-if="mark === 'account'">
      <circle
        cx="12"
        cy="8"
        r="3.8"
      />
      <path d="M4.4 20c0-3.8 3.4-6.2 7.6-6.2s7.6 2.4 7.6 6.2" />
    </template>
    <template v-else-if="mark === 'management'">
      <circle
        cx="9"
        cy="8"
        r="3.4"
      />
      <path d="M2.6 19.4c0-3.4 2.9-5.6 6.4-5.6 1 0 2 .2 2.8.5" />
      <path d="M14.4 20.4h2.6l5-5a1.8 1.8 0 0 0-2.6-2.6l-5 5Z" />
    </template>
    <template v-else-if="mark === 'mail'">
      <rect
        height="14"
        rx="1.5"
        width="19"
        x="2.5"
        y="5"
      />
      <path d="M2.8 6.2 12 13l9.2-6.8" />
    </template>
    <template v-else-if="mark === 'discord'">
      <path d="M8.4 5.6a14 14 0 0 1 7.2 0l.9-1.8a18 18 0 0 1 4 1.6c2.2 3.4 2.4 7.6 2 11.2a17 17 0 0 1-5 3 12 12 0 0 1-1.2-2M6.8 17.6a17 17 0 0 1-3-2.6c-.4-3.6-.2-7.8 2-11.2a18 18 0 0 1 4-1.6l.9 1.8M6.8 17.6l1.2-2M6.8 17.6c2.2 1.4 5.2 1.9 5.2 1.9s3-.5 5.2-1.9" />
      <circle
        cx="9.2"
        cy="12.4"
        r="1.5"
      />
      <circle
        cx="15.1"
        cy="12.4"
        r="1.5"
      />
    </template>
    <template v-else-if="mark === 'instagram'">
      <rect
        height="17"
        rx="4.6"
        width="17"
        x="3.5"
        y="3.5"
      />
      <circle
        cx="12"
        cy="12"
        r="4.2"
      />
      <path
        d="M16.7 7.4h.01"
        stroke-width="2"
      />
    </template>
    <template v-else-if="mark === 'facebook'">
      <rect
        height="17"
        rx="2.4"
        width="17"
        x="3.5"
        y="3.5"
      />
      <path d="M15.1 8h-1.7c-1 0-1.7.6-1.7 1.7V12M14.8 12H9.9M12.7 12v8.5" />
    </template>
    <template v-else-if="mark === 'twitch'">
      <path d="M4.5 3h15v9.6l-4 4h-3.3l-3 2.9v-2.9H4.5z" />
      <path d="M10.8 7.2v4.6M15.2 7.2v4.6" />
    </template>
    <template v-else-if="mark === 'x'">
      <path d="M4.6 4.6 19.4 19.4M19.4 4.6 4.6 19.4" />
    </template>
    <template v-else-if="mark === 'linkedin'">
      <rect
        height="17"
        rx="2.4"
        width="17"
        x="3.5"
        y="3.5"
      />
      <path
        d="M7.8 10.4v6M7.8 7.3h.01M11.6 16.4v-6M11.6 12.6c0-1.2 1-2 2.2-2s2.2.8 2.2 2.3v3.5"
        stroke-linecap="square"
      />
    </template>
  </svg>
</template>

<script lang="ts" setup>
export type Mark =
  | "caret" | "menu" | "sun" | "moon" | "account" | "management"
  | "mail" | "discord" | "instagram" | "facebook" | "twitch" | "x" | "linkedin"

withDefaults(defineProps<{mark: Mark; size?: number}>(), {size: 20})
</script>
