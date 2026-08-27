<script lang="ts" setup>
import {computed} from "vue"

defineOptions({name: "TargetPath"})

const props = withDefaults(defineProps<{
  /** Where the target sits, outside in. Empty for a system that files nothing. */
  path?: string[] | null
  /** The target's own name, added as the last step so the whole place reads at once. */
  leaf?: string | null
}>(), {path: null, leaf: null})

/**
 * A system that reports no location renders nothing rather than an empty step: an arrow with
 * a blank on one side reads as missing data, which is not what "this system has no folders"
 * means.
 */
const steps = computed<string[]>(() =>
  [...(props.path ?? []), props.leaf]
    .filter((step): step is string => typeof step === "string" && step.trim().length > 0),
)
</script>

<template>
  <span
    v-if="steps.length"
    class="target-path"
    data-testid="target-path"
  >
    <template
      v-for="(step, index) in steps"
      :key="`${index}-${step}`"
    >
      <span
        v-if="index > 0"
        aria-hidden="true"
        class="target-path__separator"
      >›</span>
      <span :class="{'target-path__leaf': index === steps.length - 1}">{{ step }}</span>
    </template>
  </span>
</template>

<style lang="scss" scoped>
.target-path {
  display: inline-flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}

// The steps above lead to the target; the target is the one being named.
.target-path__separator {
  opacity: 0.45;
}

.target-path__leaf {
  font-weight: 500;
}
</style>
