<script lang="ts" setup>
/** Where toasts stand: over the page at the foot of the window, or at its top, one above the other. */
defineOptions({name: "ToastArea"})

const {place = "bottom"} = defineProps<{place?: "bottom" | "top"}>()
</script>

<template>
  <div
    class="toast-area"
    :class="`toast-area--${place}`"
  >
    <slot />
  </div>
</template>

<style scoped>
/* Above the island dialog and its pickers (2500), so a message about what one did is not
   drawn under it. */
.toast-area {
  position: fixed;
  left: 0;
  right: 0;
  z-index: 2600;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem;
  /* The area spans the window; only a toast takes a press. */
  pointer-events: none;
}

.toast-area--bottom {
  bottom: 0;
  padding-bottom: max(0.5rem, env(safe-area-inset-bottom));
}

.toast-area--top {
  top: 0;
}
</style>
