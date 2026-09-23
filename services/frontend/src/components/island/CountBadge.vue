<!--
  A count that rides the heading it counts.

  Beside the heading it reads as another word in the line; on it, it reads as a mark against the
  thing. Written inline after the heading's last word, so it follows that word when the heading
  wraps on a phone. The word joiner keeps it from wrapping onto a line of its own.
-->
<template>
  <span
    class="island-badge"
    :data-testid="testid"
  >&#8288;<span class="island-badge__pill">{{ count }}</span><span class="island-badge__said">{{ said }}</span></span>
</template>

<script lang="ts" setup>
defineOptions({name: "CountBadge"})

withDefaults(defineProps<{
  count: number | string
  /** What the number counts, for a reader who is hearing the heading rather than seeing it. */
  said?: string
  testid?: string
}>(), {said: "", testid: undefined})
</script>

<style scoped>
.island-badge {
  white-space: nowrap;
}

/* On the last letters, not beside them: it overlaps the last word's top right corner, which is
   what makes it read as a mark against the words rather than as another word in the line. */
.island-badge__pill {
  position: relative;
  display: inline-block;
  vertical-align: top;
  margin-left: -0.35rem;
  translate: 0 -0.5rem;
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
  .island-badge__pill {
    margin-left: -0.3rem;
    translate: 0 -0.4rem;
    font-size: 0.68rem;
  }
}
</style>
