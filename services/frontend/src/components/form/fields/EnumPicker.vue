<script setup lang="ts">
import {computed} from "vue"

const props = defineProps<{
  modelValue?: string | undefined;
  values: string[];
  label?: string;
  required?: boolean;
}>()
defineEmits<{ "update:modelValue": [value: string | undefined] }>()

/** Turn `CONTRIBUTION_PAID` into `Contribution paid` for display. */
const humanize = (value: string): string =>
  value
    .replace(/[._-]+/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((token, idx) =>
      idx === 0
        ? token.charAt(0).toUpperCase() + token.slice(1).toLowerCase()
        : token.toLowerCase(),
    )
    .join(" ")

const options = computed(() =>
  props.values.map((value) => ({title: humanize(value), value})),
)
</script>

<template>
  <v-select
    :items="options"
    :label="label ?? 'Value'"
    :model-value="modelValue"
    :rules="required ? [(v: string | undefined) => !!v || 'Required'] : []"
    clearable
    hide-no-data
    item-title="title"
    item-value="value"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
