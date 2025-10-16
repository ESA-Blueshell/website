<script setup lang="ts">
import {Field} from "vee-validate"
import {defineModel, defineOptions} from "vue"

defineOptions({inheritAttrs: false})

type Rules = string | Record<string, unknown> | undefined

defineProps<{
  name: string
  label?: string
  rules?: Rules
  component?: unknown
  componentProps?: Record<string, unknown>
  disabled?: boolean
}>()

const model = defineModel<unknown>()
</script>

<template>
  <Field
    v-slot="{ value, errors, handleChange, handleBlur }"
    v-model="model"
    :name="name"
    :rules="rules"
  >
    <component
      :is="component || 'v-text-field'"
      v-bind="componentProps"
      :label="label"
      :model-value="value"
      :error-messages="errors"
      :disabled="disabled"
      @update:model-value="handleChange"
      @blur="handleBlur"
      v-on="$attrs"
    />
  </Field>
</template>
