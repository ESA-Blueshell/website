<script lang="ts" setup>
/* Where something stands against what it should be. The marks differ by shape and lightness as
   well as colour, so they read apart without it. */
export type StateKind = "in-step" | "missing" | "extra" | "unreachable" | "not-created" | "not-compared"

const {kind, testid = undefined} = defineProps<{
  kind: StateKind
  testid?: string
}>()

const WORDS: Record<StateKind, string> = {
  "in-step": "In step",
  missing: "Missing",
  extra: "Extra",
  unreachable: "Unreachable",
  "not-created": "Not created yet",
  "not-compared": "Not compared",
}
</script>

<template>
  <span
    class="state-mark"
    :class="`state-mark--${kind}`"
    :data-testid="testid"
  ><slot>{{ WORDS[kind] }}</slot></span>
</template>

<style scoped>
.state-mark {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.84rem;
  font-weight: 500;
  white-space: nowrap;
}

.state-mark::before {
  content: "";
  flex: none;
  width: 0.55rem;
  height: 0.7rem;
  background: currentColor;
  transform: skewX(-12deg);
}

.state-mark--in-step {
  color: var(--color-ash);
}

.state-mark--missing {
  color: var(--color-brand);
}

.state-mark--extra {
  color: var(--color-warning);
}

.state-mark--extra::before {
  background: none;
  box-shadow: inset 0 0 0 1.5px currentColor;
}

.state-mark--not-created {
  color: var(--color-danger);
}

.state-mark--not-created::before {
  width: 0.7rem;
  height: 2px;
  transform: none;
}

.state-mark--not-compared {
  color: color-mix(in oklab, var(--color-ash) 70%, transparent);
}

.state-mark--not-compared::before {
  width: 0.7rem;
  height: 1px;
  transform: none;
}

.state-mark--unreachable {
  color: var(--color-ash);
}

.state-mark--unreachable::before {
  width: 0.6rem;
  height: 0.6rem;
  background: none;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1.5px currentColor;
  transform: none;
}
</style>
