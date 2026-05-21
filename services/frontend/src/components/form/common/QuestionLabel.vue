<script lang="ts" setup>
withDefaults(
  defineProps<{
    label: string
    required?: boolean
    helperText?: string
  }>(),
  {required: false, helperText: ""},
)
</script>

<template>
  <div class="question-label">
    <div class="question-label__title">
      <span class="question-label__text">{{ label }}</span>
      <span
        v-if="required"
        class="question-label__required"
        aria-label="required"
        title="Required"
      >*</span>
      <span
        v-else
        class="question-label__optional text-caption"
      >(optional)</span>
    </div>
    <div
      v-if="helperText"
      class="question-label__helper text-caption text-medium-emphasis"
    >
      {{ helperText }}
    </div>
  </div>
</template>

<style lang="scss" scoped>
.question-label {
  margin-bottom: 0.5rem;

  &__title {
    display: flex;
    align-items: baseline;
    gap: 0.4rem;
    flex-wrap: wrap;
  }

  &__text {
    font-size: 1.05rem;
    font-weight: 600;
    line-height: 1.45;
    color: rgba(var(--v-theme-on-surface), 0.94);
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__required {
    color: rgb(var(--v-theme-error));
    font-weight: 700;
    font-size: 1.1rem;
    line-height: 1;
  }

  &__optional {
    color: rgba(var(--v-theme-on-surface), 0.55);
    font-style: italic;
  }

  &__helper {
    margin-top: 0.15rem;
  }
}
</style>
