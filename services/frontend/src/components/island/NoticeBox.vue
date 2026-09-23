<script lang="ts" setup>
/**
 * The island's notice: a title and a line, on a tint of the tone's colour.
 *
 * A tint and nothing else. A bar down its edge would lean the way the cut does and read as a
 * part of the page's structure rather than as something said to the reader.
 */
defineOptions({name: "NoticeBox"})

const {tone = "info", title = "", testid = undefined} = defineProps<{
  tone?: "info" | "warning" | "danger"
  title?: string
  testid?: string
}>()
</script>

<template>
  <div
    class="notice"
    :class="`notice--${tone}`"
    :data-testid="testid"
    :role="tone === 'info' ? 'status' : 'alert'"
  >
    <p
      v-if="title"
      class="notice__title"
    >
      {{ title }}
    </p>
    <div class="notice__body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.notice {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 1rem 1.25rem 1.15rem;
  background-color: color-mix(in oklab, var(--tone) 9%, transparent);
}

.notice--info {
  --tone: var(--color-brand);
}

.notice--warning {
  --tone: var(--color-warning);
}

.notice--danger {
  --tone: var(--color-danger);
}

.notice__title {
  font-family: var(--font-display);
  font-size: 0.95rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.notice__body {
  font-family: var(--font-body);
  font-size: 0.88rem;
  line-height: 1.5;
  color: var(--color-ash);
}
</style>
