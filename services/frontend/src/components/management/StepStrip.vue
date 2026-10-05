<template>
  <ol
    class="steps"
    :data-testid="testid"
    :style="{'--steps': steps.length}"
  >
    <li
      v-for="(name, index) in steps"
      :key="name"
      :aria-current="index === current ? 'step' : undefined"
      class="steps__one"
      :class="{'steps__one--on': index === current, 'steps__one--done': index < current}"
    >
      <svg
        v-if="index < current"
        aria-hidden="true"
        class="steps__done"
        fill="none"
        viewBox="0 0 18 18"
      >
        <path
          d="m3.5 9.5 3.5 3.5 7.5-8"
          stroke="currentColor"
          stroke-width="1.8"
        />
      </svg>
      <span
        v-else
        class="steps__n"
      >{{ index + 1 }}</span>
      <span class="steps__name">{{ name }}</span>
    </li>
  </ol>
</template>

<script lang="ts" setup>
/* The steps of a task across the top of its page: the ones behind ticked, the one in hand lit. */
const {steps, current, testid = undefined} = defineProps<{
  steps: readonly string[]
  /** The step in hand, counted from 0. */
  current: number
  testid?: string
}>()
</script>

<style scoped>
.steps {
  display: grid;
  grid-template-columns: repeat(var(--steps), minmax(0, 1fr));
  gap: 2px;
  margin-top: 1rem;
}

.steps__one {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.9rem 1rem 0.9rem 1.1rem;
  color: var(--color-ash);
  background-color: var(--band-ground);
}

.steps__one--on {
  color: var(--color-chalk);
  box-shadow: inset 0 -3px 0 var(--color-eyebrow);
}

.steps__n {
  font-family: var(--font-display);
  font-size: 1.5rem;
  line-height: 1;
}

.steps__name {
  font-size: 0.82rem;
  letter-spacing: 0.04em;
}

.steps__done {
  width: 18px;
  height: 18px;
  color: var(--color-ok);
}

@media (--phone) {
  .steps__one {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.3rem;
    padding: 0.7rem 0.6rem;
  }
}
</style>
