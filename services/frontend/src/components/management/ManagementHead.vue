<template>
  <header
    class="mg-head"
    :data-testid="testid"
  >
    <div class="mg-head__words">
      <p class="mg-head__eyebrow">
        {{ eyebrow }}
      </p>
      <h1 class="mg-head__title">
        {{ title }}
      </h1>
      <p
        v-if="$slots.default"
        class="mg-head__body"
      >
        <slot />
      </p>
    </div>
    <div
      v-if="$slots.actions"
      class="mg-head__actions"
    >
      <slot name="actions" />
    </div>
  </header>
</template>

<script lang="ts" setup>
/* The head of a Management page: the group it belongs to, its name, one sentence on what it is
   for, and what the page as a whole can do. */
const {eyebrow, title, testid = undefined} = defineProps<{
  eyebrow: string
  title: string
  testid?: string
}>()
</script>

<style scoped>
.mg-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem 2rem;
  padding: 1.3rem 2.4rem 0.4rem;
}

.mg-head__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.mg-head__title {
  margin-top: 0.7rem;
  font-size: 2.5rem;
  line-height: 0.95;
  text-transform: uppercase;
}

.mg-head__body {
  max-width: 36rem;
  margin-top: 0.9rem;
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--color-ash);
}

.mg-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

@media (--phone) {
  .mg-head {
    padding: 1.1rem 1.1rem 0.4rem;
  }

  .mg-head__title {
    font-size: 2rem;
  }
}
</style>
