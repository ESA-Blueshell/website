<script lang="ts" setup>
withDefaults(
  defineProps<{
    index?: number
    description?: boolean
  }>(),
  {index: undefined, description: false},
)
</script>

<template>
  <div
    class="question-card"
    :class="{ 'question-card--description': description }"
  >
    <div
      v-if="!description && index != null"
      class="question-card__index"
      aria-hidden="true"
    >
      {{ index }}
    </div>
    <div class="question-card__body">
      <slot />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.question-card {
  display: flex;
  gap: 0.85rem;
  padding: 1rem 1.1rem;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.10);
  border-radius: 10px;
  background-color: rgb(var(--v-theme-surface));
  transition: border-color 120ms ease, box-shadow 120ms ease;

  &:focus-within {
    border-color: rgb(var(--v-theme-primary));
    box-shadow: 0 0 0 3px rgba(var(--v-theme-primary), 0.12);
  }

  &--description {
    border: none;
    background-color: transparent;
    padding: 0.5rem 0.25rem;
  }

  &__index {
    flex: 0 0 auto;
    width: 1.75rem;
    height: 1.75rem;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: rgba(var(--v-theme-primary), 0.12);
    color: rgb(var(--v-theme-primary));
    font-weight: 600;
    font-size: 0.85rem;
    line-height: 1;
  }

  &__body {
    flex: 1 1 auto;
    min-width: 0;
  }
}
</style>
