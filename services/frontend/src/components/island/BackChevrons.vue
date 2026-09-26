<script lang="ts" setup>
/**
 * Three chevrons pointing back, the mark of every way back on the island. They stand still at rest
 * and run back the way they point while the link holding them is under the pointer or focused.
 */
defineOptions({name: "BackChevrons"})
</script>

<template>
  <span
    aria-hidden="true"
    class="back-chevrons"
  />
</template>

<style scoped>
.back-chevrons {
  flex: none;
  width: 30px;
  height: 14px;
  background-color: var(--accent, var(--color-brand));
  mask-image:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 14'%3E%3Cpath d='M7.5 1.5 2 7l5.5 5.5' fill='none' stroke='%23000' stroke-width='1.8'/%3E%3C/svg%3E"),
    linear-gradient(90deg, transparent, #000 70%);
  mask-size: 10px 14px, 100% 100%;
  mask-repeat: repeat-x, no-repeat;
  mask-position: 0 50%, 0 0;
  mask-composite: intersect;
}

a:hover > .back-chevrons,
a:focus-visible > .back-chevrons {
  animation: back-chevrons-run 420ms linear infinite;
}

@keyframes back-chevrons-run {
  from {
    mask-position: 0 50%, 0 0;
  }

  to {
    mask-position: -10px 50%, 0 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  a:hover > .back-chevrons,
  a:focus-visible > .back-chevrons {
    animation: none;
  }
}
</style>
