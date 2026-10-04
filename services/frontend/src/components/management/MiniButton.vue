<template>
  <router-link
    v-if="to"
    class="mini"
    :class="{'mini--danger': tone === 'danger'}"
    :data-testid="testid"
    :to="to"
  >
    <slot />
  </router-link>
  <button
    v-else
    class="mini"
    :class="{'mini--danger': tone === 'danger'}"
    :data-testid="testid"
    type="button"
  >
    <slot />
  </button>
</template>

<script lang="ts" setup>
/* An act on one row of a table: small enough to sit several to a row. */
const {to = "", tone = "plain", testid = undefined} = defineProps<{
  /** Where it leads; omit for something the page handles itself. */
  to?: string
  tone?: "plain" | "danger"
  testid?: string
}>()
</script>

<style scoped>
.mini {
  display: inline-flex;
  align-items: center;
  padding: 0.34rem 0.7rem;
  font-family: var(--font-display);
  font-size: 0.62rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 8%, transparent);
  clip-path: polygon(0.45rem 0, 100% 0, calc(100% - 0.45rem) 100%, 0 100%);
  cursor: pointer;
}

.mini:hover {
  color: var(--color-void);
  background-color: var(--color-brand);
}

.mini--danger:hover {
  color: var(--color-danger-on-fill);
  background-color: var(--color-danger-fill);
}

.mini:disabled {
  opacity: 0.45;
  cursor: default;
}
</style>
