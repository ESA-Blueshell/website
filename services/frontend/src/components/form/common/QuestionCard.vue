<script lang="ts" setup>
withDefaults(
  defineProps<{
    description?: boolean
  }>(),
  {description: false},
)
</script>

<template>
  <div
    class="question-card"
    :class="{ 'question-card--description': description }"
  >
    <slot />
  </div>
</template>

<style lang="scss" scoped>
.question-card {
  padding: 0.25rem 0;

  &--description {
    padding: 0.5rem 0;
  }
}
</style>
