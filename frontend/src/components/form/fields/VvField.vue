<template>
  <Field
    v-slot="{ value, errors, handleChange, handleBlur }"
    v-model="model"
    :name="name"
    :rules="rules"
  >
    <component
      :is="component"
      :disabled="disabled"
      :error-messages="errors"
      :label="label"
      :model-value="display ? display(value) : value"
      v-bind="componentProps"
      @blur="handleBlur"
      @update:model-value="(v: unknown) => update ? update(v, handleChange) : handleChange(v)"
      v-on="$attrs"
    />
  </Field>
</template>

<script lang="ts" setup>
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
  display?: (value: unknown) => unknown
  update?: (incoming: unknown, handleChange: (v: unknown) => void) => void
}>(), {
  label: "",
  rules: "",
  component: () => VTextField,
  componentProps: () => ({})
})

const model = defineModel<unknown>()
</script>
