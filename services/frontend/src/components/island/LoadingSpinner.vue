<script lang="ts" setup>
/** What a page shows while it fetches: a turning ring, with words for a screen reader. */
defineOptions({name: "LoadingSpinner"})

const STROKE = 4

const {label = "Loading", size = 32} = defineProps<{
  label?: string
  /** The ring's width and height in pixels. */
  size?: number
}>()
</script>

<template>
  <span
    class="loading-spinner"
    role="status"
  >
    <svg
      aria-hidden="true"
      class="loading-spinner__ring"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      :width="size"
    >
      <circle
        class="loading-spinner__track"
        :cx="size / 2"
        :cy="size / 2"
        :r="(size - STROKE) / 2"
        :stroke-width="STROKE"
      />
      <!-- pathLength makes the dash a share of the ring, whatever its size. -->
      <circle
        class="loading-spinner__arc"
        :cx="size / 2"
        :cy="size / 2"
        pathLength="100"
        :r="(size - STROKE) / 2"
        :stroke-width="STROKE"
      />
    </svg>
    <span class="loading-spinner__label">{{ label }}</span>
  </span>
</template>

<style scoped>
.loading-spinner {
  display: inline-flex;
  vertical-align: middle;
}

.loading-spinner__ring {
  animation: loading-spinner-turn 1.4s linear infinite;
}

.loading-spinner__track,
.loading-spinner__arc {
  fill: none;
  stroke: currentColor;
}

.loading-spinner__track {
  opacity: 0.14;
}

.loading-spinner__arc {
  stroke-dasharray: 20 100;
  stroke-linecap: round;
  animation: loading-spinner-dash 1.4s ease-in-out infinite;
}

/* Not a Tailwind utility: the spinner also stands on pages outside an island, where none applies. */
.loading-spinner__label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

@keyframes loading-spinner-turn {
  to {
    rotate: 270deg;
  }
}

@keyframes loading-spinner-dash {
  0% {
    stroke-dasharray: 1 100;
    stroke-dashoffset: 0;
  }

  50% {
    stroke-dasharray: 80 100;
    stroke-dashoffset: -12;
  }

  100% {
    stroke-dasharray: 80 100;
    stroke-dashoffset: -99;
  }
}

@keyframes loading-spinner-pulse {
  50% {
    opacity: 0.4;
  }
}

/* Turning is what the preference asks to stop. A slow fade still says the page is busy. */
@media (prefers-reduced-motion: reduce) {
  .loading-spinner__ring {
    animation: loading-spinner-pulse 2s ease-in-out infinite;
  }

  .loading-spinner__arc {
    animation: none;
  }
}
</style>
