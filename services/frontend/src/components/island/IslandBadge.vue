<!--
  A count that rides the heading it counts.

  Beside the heading it reads as another word in the line; on it, it reads as a mark against the
  thing. The heading it rides has to be a positioned box, which is what `island-badged` is for.
-->
<template>
  <span
    class="island-badge"
    :data-testid="testid"
  >{{ count }}<span class="island-badge__said">{{ said }}</span></span>
</template>

<script lang="ts" setup>
defineOptions({name: "IslandBadge"})

withDefaults(defineProps<{
  count: number | string
  /** What the number counts, for a reader who is hearing the heading rather than seeing it. */
  said?: string
  testid?: string
}>(), {said: "", testid: undefined})
</script>

<style scoped>
.island-badge {
  position: absolute;
  /* On the last letters, not beside them: it overlaps the heading's top right corner, which is
     what makes it read as a mark against the words rather than as another word in the line. */
  top: -0.5rem;
  right: -0.35rem;
  min-width: 1.6rem;
  padding: 0.2rem 0.5rem;
  border-radius: 9999px;
  background: var(--color-brand);
  font-family: var(--font-body);
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.2;
  text-align: center;
  color: var(--color-void);
}

/* Said aloud, never drawn: the number alone is a riddle to anybody who cannot see what it sits on. */
.island-badge__said {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

@media (max-width: 767px) {
  .island-badge {
    top: -0.4rem;
    right: -0.3rem;
    font-size: 0.68rem;
  }
}
</style>
