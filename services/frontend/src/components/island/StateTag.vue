<script lang="ts" setup>
/** A word of state beside a title: on, off, waiting, this browser. Ruled in its own colour, never filled. */
defineOptions({name: "StateTag"})

const {tone = "accent", testid = undefined} = defineProps<{
  tone?: "accent" | "ok" | "warn" | "quiet"
  testid?: string
}>()
</script>

<template>
  <span
    class="state-tag"
    :class="`state-tag--${tone}`"
    :data-testid="testid"
  ><slot /></span>
</template>

<style scoped>
.state-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.26rem 0.55rem;
  border: 1px solid currentColor;
  font-family: var(--font-body);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  line-height: 1.2;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--color-eyebrow);
}

.state-tag--ok {
  color: var(--color-ok);
}

.state-tag--warn {
  color: var(--color-warning);
}

.state-tag--quiet {
  color: var(--color-ash);
}
</style>
