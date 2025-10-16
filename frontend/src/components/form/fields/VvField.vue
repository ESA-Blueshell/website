<script setup lang="ts">
import {Field} from "vee-validate"
import {VTextField} from "vuetify/components"

defineOptions({inheritAttrs: false})

type Rules = string | Record<string, unknown> | undefined

withDefaults(defineProps<{
  name: string
  label?: string
  rules?: Rules
  component?: unknown
  componentProps?: Record<string, unknown>
  disabled?: boolean
}>(), {
  label: "",
  rules: "",
  component: () => VTextField,
  componentProps: () => ({}),
})

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
      :is="component"
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
